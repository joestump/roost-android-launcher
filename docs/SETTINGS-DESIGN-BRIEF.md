# Design handoff — Roost Settings redesign

> Paste this into a design-focused Claude session. It's self-contained — the designer won't have the
> build conversation, so everything needed is here. Companion to `docs/DESIGN-BRIEF.md` (which produced
> Roost's overall look); this brief is specifically about **Settings**.

## The ask (one line)
Roost's Settings started small and accreted into **one overloaded, flat, infinitely-scrolling screen**.
Every feature works — but the UX is a mess. Redesign it into a **gorgeous, elegant, well-organized
settings experience**: rethink the information architecture, hierarchy, navigation, and every control —
without dropping a single capability.

## What Roost is (context)
A tiny, **vendor-neutral** Android launcher that turns a spare phone into a dedicated device for an AI
agent. It boots into the agent app and keeps a curated set of apps, web apps, and action buttons one tap
away. Warm-dark identity, a little LED-eyed robot mascot, one themeable accent. See `docs/DESIGN-BRIEF.md`.

## Visual analysis of the current Settings (the problem)
`SettingsActivity` is a single `ScrollView` of ~12 stacked sections, all the same visual weight:

1. **No architecture.** One endless vertical scroll. No top-level categories, no drill-down, no "where am
   I," no search. You scroll past everything to find anything.
2. **All concerns jammed together, unsorted.** Modes, behavior toggles, appearance (accent, wallpaper,
   bandwidth), agent identity (name, featured app), content management (favorites, web apps, action
   buttons, hidden), and network (VPN) are interleaved with no grouping. Appearance settings (accent /
   wallpaper / bandwidth) are literally separated by unrelated sections.
3. **The Favorites section swallows the screen.** It renders a checkbox row for **every installed
   launchable app** — dozens of two-line rows (`App name` / `com.package.name`) — which dominates the
   scroll and buries Web apps, Hidden, and VPN beneath it.
4. **Developer-y, inconsistent forms.** Multiple raw `EditText` + separate **Save** button pairs (Agent
   name, Featured **agent app as a raw package string**, Web-app add, VPN tunnel name). The featured app
   is typed as `com.anthropic.claude`, not picked. Web apps and favorites show two-line `label\nvalue`
   rows that read technical and dense.
5. **Flat typographic rhythm.** Section headers are all identical uppercase accent labels at one size;
   nothing establishes hierarchy, grouping, or breathing room.
6. **Sub-screens exist but aren't cohesive.** "Action buttons" links to a second screen
   (`ActionsActivity`) with its own long stack (enabled list, Home Assistant accounts with name/URL/token
   fields, a scene picker, an app-shortcut scanner). The icon picker (`IconPickerActivity`) is a third
   screen. They don't share a settings design language.

A representative screenshot of the current state is at
[`docs/screenshots/settings-current.png`](screenshots/settings-current.png) (top of the scroll — title
through the start of the Favorites app-list).

## Full inventory — everything that must stay reachable
Grouped by concern (raw material; **you** decide the final IA):

**Home & behavior**
- Home mode: **Curated** (mascot home + tile grid) vs **Boot-direct / Appliance** (opens the agent; a
  long-press on Home reveals the grid).
- Auto-launch agent on boot (toggle).
- Keep screen on while docked (toggle).
- Bandwidth heartbeat (toggle — a subtle network graph on home).

**Agent identity**
- Agent name (free text; drives the "<name> is home" greeting + the mono status line).
- Featured agent app (which installed app is *the agent* — currently a raw package field; should be an
  app picker).

**Appearance**
- Accent tint: Honey / Slate / Sage / Violet.
- Match wallpaper to Roost (one-shot action that paints a matching wallpaper).

**Apps, tiles & content**
- Favorites — which installed apps appear on the grid (today: a full checkbox list of all apps).
- Web apps — add / list / remove a name + URL that opens fullscreen (self-hosted dashboards).
- Action buttons (own sub-screen) — an "Enabled" list; **Home Assistant** accounts (name + base URL +
  long-lived token) with a per-account **scene** picker; an **app-shortcut** scanner (tick shortcuts to
  add). Buttons render as a pill row on home.
- Hidden items — restore tiles hidden via the home long-press menu.
- (For reference, already handled by the home long-press menu, not Settings: Hide / Delete / **Change
  icon** → a remote **icon picker** with three sources — selfh.st, Simple Icons, Heroicons — and **Reset
  icon**.)

**Network**
- VPN (WireGuard) tunnel name for the one-tap VPN toggle, plus a note that WireGuard's "Allow remote
  control apps" must be on.

**Advanced**
- Open Android Settings.

## Hard constraints (the design MUST be buildable)
Framework-only per **ADR-0001** — pure Android framework, **no AndroidX, no Compose, no Material
Components, no libraries**. Views built programmatically in Kotlin. `minSdk 26`. So:
- **Navigation = separate `Activity`s** for drill-down (there's no nav component). A settings "category
  list → detail screen" model is fine; each detail screen is its own Activity.
- **Lists = programmatic** (`LinearLayout` in a `ScrollView`; no RecyclerView). Long lists (all-apps,
  scenes, icon search) need a pattern that isn't a wall of rows — search/filter, sectioning, or a picker.
- **Surfaces = `GradientDrawable`** rounded rects (cards/rows), light elevation. **Icons = VectorDrawable**
  (or `Canvas`). **Type = system fonts** (`sans-serif`, `-medium`, `-condensed`). No bundled assets/fonts.
- Reuse the **Roost palette** (already in `Roost.kt`): dock `#14110D` (radial → `#1D1912`), panel
  `#1C1813`, tile `#2A241C`, text `#F3EEE4`, muted `#A29A8C`, **themeable accent** `#E7A44E` (Honey
  default; also Slate/Sage/Violet). Current type: title 22sp medium, headers uppercase accent ~12sp.
- Settings should feel like the **same warm-dark place** as the launcher home.

## What I'd love from you
1. **Information architecture.** Propose a clean category structure and the **navigation model** (a
   Settings landing that drills into detail Activities? grouped cards on fewer screens?). Name the
   categories and place every inventory item.
2. **Hierarchy & rhythm.** How a settings screen should look: grouped **cards** of related rows, section
   headers, consistent **row types**, spacing/corner-radius rhythm — so it reads calm and scannable.
3. **The long-list problem.** Turn the giant all-apps **Favorites** checkbox list, the Home Assistant
   **scene** list, and the **app-shortcut** scan into something elegant (a searchable picker screen? an
   app grid with checkmarks? sectioned lists?).
4. **Forms, done right.** Replace the raw `EditText` + Save patterns. **Featured agent app** and
   **Favorites** should be **app pickers** (icon + name), not package strings. Make Agent name, Web-app
   add, HASS account, and VPN tunnel inputs consistent and elegant (inline commit vs a single save).
5. **A component set.** Specify the reusable controls — **toggle row, radio/segmented control, value +
   chevron (navigates), action row, text input, chip/swatch, list item with icon** — with visual
   treatments mapped to framework primitives.
6. **Entry & chrome.** The current entry is a muted "Apps & settings" text link at the bottom of home.
   Design the settings entry point and the in-settings header/back/title pattern.
7. **Search?** Optional — is a settings search worth it at this scale, or does good IA make it moot?

## Deliverables
- An **IA map**: categories, what lives where, and the navigation model (how drill-down works,
  framework-only).
- **Mockups**: the Settings landing, 2–3 representative detail screens (e.g. **Appearance**, an **app /
  favorites picker**, **Action buttons / Home Assistant**), and the **component set**.
- A **component spec**: row/control types, spacing + corner-radius tokens, type scale, states — each
  mapped to a framework primitive (color int, `GradientDrawable`, `VectorDrawable`, system font weight);
  flag anything needing a bundled asset.
- **Implementation notes**: how many Activities/screens, how navigation + long-list pickers are built
  framework-only.

## Tone
Same as Roost: warm, calm, characterful, clean. Settings should feel **gorgeous and effortless** — a
well-organized control panel for the agent's device, not a developer form dump.

## Repo (read the current code)
https://gitea.stump.rocks/joestump/roost-android-launcher
- `app/src/main/java/rocks/stump/roost/SettingsActivity.kt` — the overloaded main settings screen.
- `app/src/main/java/rocks/stump/roost/ActionsActivity.kt` — action buttons (HASS accounts + scene picker
  + shortcut scanner).
- `app/src/main/java/rocks/stump/roost/IconPickerActivity.kt` — the 3-source remote icon picker.
- `app/src/main/java/rocks/stump/roost/Roost.kt` — palette + drawable helpers.
- `docs/adrs/` — architecture decisions (esp. **ADR-0001**, the framework-only constraint).
