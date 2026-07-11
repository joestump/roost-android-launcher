---
status: accepted
date: 2026-07-11
decision-makers: [joestump, Claude]
extends: [ADR-0001]
governs: [SPEC-0001]
---

# ADR-0002: Pluggable action-button providers

## Context and Problem Statement

Beyond launching apps, Roost should let the user place **action buttons** on the home screen — each an icon + short title that, when tapped, *does one thing*. The first two sources are Android **app-shortcuts** (an installed app's long-press menu items) and Home Assistant **scenes**, and more sources are likely (scripts, webhooks, Tasker tasks, MQTT, quick-settings). How should these be modeled so adding a new source is cheap and the home-screen render + settings UI stay uniform?

## Decision Drivers

* **Extensibility** — adding a provider should be small and localized, not a cross-cutting change.
* **Uniform surface** — all buttons render identically on the home (icon + title) and toggle identically in settings, regardless of source.
* **Framework-only** (per [ADR-0001](ADR-0001-framework-only-zero-dependency-launcher.md)) — no library for Home Assistant; simple serializable state in `SharedPreferences`.
* **Separation from app tiles** — action buttons are visually and conceptually distinct from the app-tile grid (they *do a thing*, they don't open an app).
* **Cheap persistence** — the enabled set must serialize to a `SharedPreferences` string with no schema migrations.

## Considered Options

* **A. Pluggable provider model** — one `ActionButton` value type `(kind, key, title, a, b)` where `kind` is an `ActionKind` enum and `a`/`b` are two provider-specific string args. Each provider is an object exposing **scan** (list candidates for the picker) and **invoke** (do the thing). The enabled set is a JSON array in prefs. The home render and settings picker are generic over `kind`.
* **B. Bespoke per-feature** — a standalone "app shortcuts" feature and a standalone "Home Assistant" feature, each with its own storage keys, settings UI, and home-render code.
* **C. Raw-intent/URL buttons only** — a single generic button type where the user pastes an Android intent URI or a URL; no provider concept.

## Decision Outcome

Chosen option: **A. Pluggable provider model**, because it makes the render path and settings picker provider-agnostic while confining each source's knowledge (how to scan, how to invoke, how to make an icon) to a single small object. A new provider = a new `ActionKind` value + a scanner + an invoker + a branch in two `when(kind)` sites (icon + invoke). Persistence is a flat JSON array of `(kind, key, title, a, b)`, so no migrations.

### Consequences

* Good, because adding a provider is localized and small; the home row and picker don't change.
* Good, because all buttons look and behave consistently for the user.
* Good, because state is a single serializable prefs string per concept (`action_buttons`, plus `hass_accounts`).
* Bad, because two opaque `a`/`b` string args are less self-documenting than a typed per-provider payload (mitigated by the `key` scheme `kind:arg1:arg2` and provider-owned constructors like `ShortcutProvider.Item.toButton()`).
* Bad, because `invoke()` and `icon()` are `when(kind)` dispatch sites the author must remember to extend (a sealed hierarchy would enforce this, but adds ceremony; revisit if providers proliferate).
* Neutral, because provider-specific setup UI (e.g. Home Assistant account management) still lives in the settings screen — the *model* is uniform, the *configuration* can be bespoke per provider.

### Confirmation

`ActionButton` + `ActionKind` in `Models.kt`; enabled set via `Prefs.actionButtons()/setActionEnabled()`. Each provider is a Kotlin `object` (`ShortcutProvider`, `Hass`) with scan + invoke. The home renders a horizontal pill row from `Prefs.actionButtons()`; `ActionsActivity` drives the pickers. A reviewer confirms a new provider touches only: (1) an `ActionKind` value, (2) its provider object, (3) the `icon`/`invoke` `when` branches, (4) a picker section.

## Pros and Cons of the Options

### A. Pluggable provider model

* Good, because uniform render + picker; localized provider additions.
* Good, because trivially serializable, no migrations.
* Neutral, because `a`/`b` are stringly-typed (keyed + provider-constructed to stay safe).
* Bad, because `when(kind)` dispatch must be kept in sync across icon/invoke.

### B. Bespoke per-feature

* Good, because each feature can use the most natural types with no shared abstraction.
* Bad, because N features = N settings sections, N storage schemas, N home-render code paths — duplication and drift.
* Bad, because the home screen would need to merge heterogeneous button sources anyway, re-introducing an ad-hoc abstraction.

### C. Raw-intent/URL buttons only

* Good, because maximally generic with zero per-provider code.
* Bad, because users must hand-author Android intent URIs — hostile UX.
* Bad, because it can't *discover* candidates (scan app-shortcuts, list Home Assistant scenes) or fetch real icons/labels.

## Architecture Diagram

```mermaid
flowchart TD
    subgraph Model["Model (Models.kt)"]
        AB["ActionButton(kind, key, title, a, b)"]
        AK["ActionKind { SHORTCUT, HASS_SCENE, ... }"]
    end
    subgraph Providers["Providers (framework-only)"]
        SP["ShortcutProvider\nscanAll() / icon() / invoke()\n(LauncherApps — Roost is HOME)"]
        HS["Hass\nscenes() / activateScene()\n(HttpURLConnection + org.json)"]
    end
    Prefs["Prefs\naction_buttons[] · hass_accounts[]"]
    Picker["ActionsActivity\n(scan → tick → enable)"]
    Home["MainActivity\nhorizontal pill row"]

    Picker -->|scan| SP
    Picker -->|scan| HS
    Picker -->|setActionEnabled| Prefs
    Prefs --> Home
    Home -->|invoke(kind)| SP
    Home -->|invoke(kind)| HS
    AB --- AK
```

## More Information

* Formalized in [SPEC-0001](../openspec/SPEC-0001-action-buttons.md).
* Extends [ADR-0001](ADR-0001-framework-only-zero-dependency-launcher.md): Home Assistant uses `HttpURLConnection` + `org.json`, not a client library, because of the zero-dependency constraint.
* App-shortcut access relies on Roost being the **default HOME app** (`LauncherApps.getShortcuts` requires the launcher role); it degrades to an empty list otherwise.
* Known limit at time of writing: the Claude app publishes **no** app-shortcuts and no documented deep links, so the SHORTCUT provider surfaces nothing for Claude specifically — but works for any app that does publish shortcuts.
* Future providers to consider: Home Assistant scripts/webhooks, generic HTTP webhook, Tasker task, MQTT publish.
