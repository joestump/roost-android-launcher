---
status: accepted
date: 2026-07-12
decision-makers: [joestump, Claude]
extends: [ADR-0002, ADR-0004, ADR-0005]
governs: [SPEC-0004]
---

# ADR-0007: One tile model — apps, web apps, and action buttons are all `ActionButton`s

## Context and Problem Statement

The home currently renders three parallel subsystems: the **featured agent hero** (`featuredHero()`), a
**utility grid** of favorite apps + web apps (`utilityGrid()`, reading `Prefs.favorites` + `Prefs.webApps`,
alphabetical, 4-across), and the **Actions zone** (`actionsZone()`, the pluggable `ActionButton`s of
[ADR-0002](ADR-0002-pluggable-action-buttons.md) rendered as SLIM/REGULAR/RICH tiles). Each has its own data
store, its own render path, and its own arrangement rules (favorites are alphabetical and un-orderable; only
Actions can be arranged/hidden/disabled).

The owner's observation: **an app shortcut is already an `ActionButton` (`kind = SHORTCUT`).** Apps and web
apps are the only hold-outs. If they became `ActionButton`s too, the home would be **one** arrangeable,
density-capable, hide/disable-able, sync-able surface, and the three subsystems would collapse into the
provider machinery ADR-0002 already describes — the featured agent staying the one special surface.

How do we unify without a disruptive data migration, and without flattening the distinct look of the app
grid vs the action tiles?

## Decision Drivers

* **Finish ADR-0002, don't fork it.** The uniform `ActionButton` + stateless provider pattern is already the
  thesis; apps/web are just two more providers.
* **No config migration.** Favorites (`Prefs.favorites`) and web apps (`Prefs.webApps`) stay exactly as they
  are — the Settings app-picker and web-app form are untouched. Providers *read* those stores; nothing is
  re-homed. (Owner: "leave the Favorites setup, refactor the launcher side.")
* **Zones become a property, not a subsystem.** A tile carries a **section** tag; the home groups by section.
  This is what enables **filtering** and cross-group **arranging** later.
* **Preserve the look.** Unify the *data model*, not necessarily the *presentation*. A section declares how it
  renders (compact icon grid vs density tiles), so today's app grid and action tiles still look like
  themselves.
* **Framework-only** ([ADR-0001](ADR-0001-framework-only-zero-dependency-launcher.md)) — no new deps.

## Considered Options

* **A. Providers + a thin layout layer (chosen).** Add `ActionKind.APP` and `ActionKind.WEB`; a
  `FavoritesProvider` emits an `APP` button per favorited package and a `WebProvider` emits a `WEB` button per
  web app — both reading the existing Prefs. Every tile the home shows (except the agent hero) is an
  `ActionButton`. A new **layout store** (ordered keys + a section per key) is the single arrangement
  authority across all providers; the per-source stores remain the "what exists" truth. Sections declare their
  presentation.
* **B. Migrate everything into `action_buttons`.** Fold favorites/web into the `action_buttons` collection as
  stored `APP`/`WEB` buttons. One list, but it rewrites the favorites store (against the owner's steer) and
  needs a real migration.
* **C. Leave three subsystems, just share the tile view.** Cosmetic only — doesn't deliver one arrangeable,
  filterable, section-tagged grid.

## Decision Outcome

Chosen: **A**. It realizes "everything is an action button" as a *rendering + arrangement* refactor over the
existing stores — no favorites/web migration, no lost config — and it keeps ADR-0002's provider contract as
the single extension point. New tile sources (and `actions.d`, [ADR-0006](ADR-0006-declarative-action-provisioning.md))
can later provision apps/web too, because they're just `ActionButton`s.

### The model

* **Kinds** grow to `{ APP, WEB, SHORTCUT, HASS_SCENE, HTTP }`. `APP` (`a = package`) launches the app; `WEB`
  (`a = url`) opens the fullscreen `WebAppActivity`. Behavior splits into **launch kinds** (`APP`, `WEB`,
  `SHORTCUT` — fire-and-forget, no on-tile state machine, they open something) and **fire kinds**
  (`HTTP`, `HASS_SCENE` — the idle→pending→success/error/timeout tile of [ADR-0004](ADR-0004-generalized-http-action-provider.md)).
* **Providers** (ADR-0002 objects): `FavoritesProvider` (reads `Prefs.favorites`, minus the agent, installed
  only), `WebProvider` (reads `Prefs.webApps`), plus the existing `ShortcutProvider`, HASS scenes, and stored
  HTTP. The home is the **union** of all providers + stored HTTP.
* **Uniform presentation.** Every tile renders the same way — the density-aware tile (SLIM / REGULAR / RICH,
  one home-wide setting), no apps-vs-actions split and no section grouping. (An earlier revision split the
  home into an app GRID and an actions TILES zone with a per-tile section; owner feedback replaced it with one
  look for everything, so `TileSection`/`TilePresentation` were dropped.)
* **Per-kind taglines.** The subtitle line is metadata-driven so a tile reads at a glance: `WEB` shows its
  host, `HTTP` shows `METHOD · host`, `SHORTCUT` shows "shortcut", `APP` shows just its name. The
  idle→pending→success/error/timeout status line appears **only on fire kinds** (`HTTP`, `HASS_SCENE`);
  launch kinds carry no "tap to fire".
* **Filter by kind.** A chip row on the home (`All` + one chip per present kind, via `ActionKind.filterLabel()`)
  narrows the tiles to a single kind; the active filter persists (`Prefs.tileFilter`). A Settings toggle per
  kind (`Prefs.hiddenFilterKinds`) chooses which chips appear.
* **Layout** (`Prefs.tileLayout`, an ordered list of keys) is the single order authority across every source,
  replacing "favorites are alphabetical" and generalizing the `action_buttons` order to all tiles. New keys
  append to the tail; the Arrange screen is a flat reorder + on/off list. Seeded on first run from today's
  arrangement (favorites alphabetical, then the Actions order) so the home looks identical the first time.
* **Enable/disable + hide** (`disabled_action_keys`, `hidden_items`, ADR-0005/v0.8.0) and **icon overrides**
  already key by `ActionButton.key`, so they apply uniformly to apps and web tiles for free.
* The **featured agent** stays a distinct hero surface (not an `ActionButton`) — the one thing that is not "a
  tile" — and its card background is accent-tinted to sit apart.

### Consequences

* Good: one surface — arrange, hide, disable, change-icon, and one uniform look work on every tile, including
  apps. Favorites become manually orderable (they weren't).
* Good: `utilityGrid()` / `WebAppsActivity`-on-home / `actionsZone()` collapse into one uniform tile renderer
  + two tiny providers; ADR-0002 stays the only extension seam.
* Good: **filtering** is a first-class home feature — a per-kind chip row, configurable in Settings.
* Neutral: favorites/web still live in their own Prefs (`favorites`, `webApps`) — the `tileLayout` store
  references them by key. Two stores per concept (existence vs placement), but no migration and clean
  separation.
* Bad: a small one-time seed for `tileLayout` (defaulted so the home is unchanged) and care that a launch-kind
  tile never shows a firing state.
* Neutral: Curated vs Appliance mode ([ADR-0005]) is unaffected — Appliance still hides the whole grid behind
  a long-press.

### Confirmation

A reviewer confirms: the home renders apps, web apps, shortcuts, HASS scenes, and HTTP as one uniform,
`tileLayout`-ordered tile grid with per-kind taglines and a kind-filter chip row; `Prefs.favorites`/`Prefs.webApps`
are unchanged by the refactor; Arrange is a flat reorder + on/off list over every tile (including apps); a launch-kind
tile shows no firing state; the agent hero remains separate with an accent-tinted background. No third-party
library is added. Formalized in [SPEC-0004](../openspec/unified-home/spec.md).

## More Information

* Complements, doesn't replace, the authoring surfaces: the app-picker (favorites), the web-app form, the
  HTTP builder, and `actions.d` all still create their respective things — they just all surface as tiles.
* Filtering, per-section density, and `actions.d`-provisioned apps/web are explicitly **future** work this ADR
  enables but does not require.
* Realizes the owner request captured 2026-07-12 ("everything should just be an action button minus the agent
  application"), extending [ADR-0002](ADR-0002-pluggable-action-buttons.md).
