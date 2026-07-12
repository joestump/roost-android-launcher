---
status: accepted
date: 2026-07-11
decision-makers: [joestump, Claude]
extends: [ADR-0001]
---

# ADR-0005: Settings navigation — a landing + per-category detail Activities

## Context and Problem Statement

Roost's Settings began as a single `SettingsActivity` — one `ScrollView` of ~12 flat, equally-weighted
sections (home mode, three behavior toggles, accent, wallpaper, agent name, a raw featured-package field,
a full all-apps favorites checkbox list, web apps, hidden, VPN, open-Android-settings). Every capability
worked, but the screen became an overloaded, infinitely-scrolling wall with no hierarchy, no "where am I,"
and a Favorites checkbox list that swallowed the page. The Claude Design "Settings redesign" mockup
restructures this into a **landing that drills into per-category detail screens**. How should that
navigation be modeled under the framework-only constraint of
[ADR-0001](ADR-0001-framework-only-zero-dependency-launcher.md), which forbids AndroidX and therefore the
Jetpack Navigation component and Fragments?

## Decision Drivers

* **Hierarchy & calm** — a scannable landing of a few categories, each opening a focused detail screen,
  instead of one endless scroll.
* **No capability dropped** — every existing setting must remain reachable (the redesign is IA + polish,
  not a feature cut).
* **Framework-only** — no nav component, no Fragments, no RecyclerView; drill-down must use plain
  `Activity` navigation and programmatic `LinearLayout`/`ScrollView`.
* **Elegant long lists** — the all-apps Favorites list, the app picker, and icon/scene lists must become
  searchable/sectioned pickers, not walls of rows.
* **Real pickers, not raw fields** — the featured agent app and Favorites should be **app pickers**
  (icon + name), not typed package strings.

## Considered Options

* **A. Settings landing → per-category detail Activities.** `SettingsActivity` becomes a short landing
  listing categories (Home & behavior, Agent, Appearance, Apps/tiles/content, Network) as icon + label +
  summary + chevron rows. Each opens its own detail `Activity` (`BehaviorActivity`, `AgentActivity`,
  `AppearanceActivity`, `AppsActivity`, `NetworkActivity`); `AppsActivity` further drills into Favorites
  (an app picker), Web apps, Action buttons, and Hidden. Drill-down = `startActivity`; back = the system
  back stack.
* **B. Keep the single scrolling `SettingsActivity`.** Improve spacing/grouping but keep one screen.
* **C. One Activity with in-screen section navigation** — a tab bar or an anchored table-of-contents that
  scrolls/swaps content panels within a single Activity.

## Decision Outcome

Chosen option: **A**. A landing + detail Activities is the idiomatic framework-only way to get drill-down
navigation without a nav component: each screen is a normal `Activity`, the back stack is the system's,
and each detail screen stays short and focused. This directly realizes the redesign mockup and the
`docs/SETTINGS-DESIGN-BRIEF.md` information architecture while dropping no capability.

### Consequences

* Good, because each screen is calm and single-purpose; the landing answers "where do I go" at a glance.
* Good, because it is pure framework: `startActivity` for drill-down, the system back stack for return,
  `ScrollView` + `LinearLayout` per screen, a shared row/control vocabulary reused across screens.
* Good, because it forces the long lists into real pickers — the Favorites all-apps list and the featured
  agent become a **searchable app picker** (icon + name + check), not a package-string field or a wall.
* Bad, because more Activities means more `AndroidManifest.xml` registrations and a shared settings
  component vocabulary must be factored to avoid per-screen drift (mitigated by small shared row helpers).
* Neutral, because provider-specific management (Home Assistant accounts, the HTTP-action builder and
  endpoints picker from [ADR-0004](ADR-0004-generalized-http-action-provider.md)) remains its own screen,
  reached from the Apps → Action buttons detail — the IA is uniform, the deep config stays bespoke.

### Confirmation

`SettingsActivity` renders a category landing; each category is its own `Activity` registered in the
manifest; the featured agent and Favorites are app pickers (icon + name), not raw package fields; and a
reviewer confirms every setting previously present in the monolith is still reachable through the new
tree. Realizes `docs/SETTINGS-DESIGN-BRIEF.md`.

## More Information

* Realizes the design handoff `docs/SETTINGS-DESIGN-BRIEF.md` and the Claude Design "Settings redesign"
  mockup.
* Category tree: **Home & behavior** (home mode · auto-launch on boot · keep screen on · bandwidth
  heartbeat), **Agent** (agent name · featured agent app picker · restart agent app), **Appearance**
  (accent tint · match wallpaper), **Apps, tiles & content** (Favorites picker · Web apps · Action buttons
  · Hidden items), **Network** (WireGuard tunnel · remote-control note), plus **Open Android Settings**.
* The icon picker ([ADR-0003](ADR-0003-icon-rendering-strategy.md)) and the HTTP-action builder / endpoints
  picker ([ADR-0004](ADR-0004-generalized-http-action-provider.md)) are reused as-is from the relevant
  detail screens.
