---
status: accepted
date: 2026-07-12
implements: [ADR-0007]
extends: [SPEC-0001, SPEC-0002]
---

# SPEC-0004: Unified home layout

## Overview

Every home tile except the featured agent hero is an `ActionButton` ([ADR-0002](../../adrs/ADR-0002-pluggable-action-buttons.md)),
grouped into ordered **sections**, ordered by a single **layout**, and rendered per its section's
**presentation**. Apps and web apps join shortcuts, HASS scenes, and HTTP as tiles via two new providers,
without moving their config. Realizes [ADR-0007](../../adrs/ADR-0007-unified-tile-model.md) under the
framework-only constraint of [ADR-0001](../../adrs/ADR-0001-framework-only-zero-dependency-launcher.md).

## Requirements

### Requirement: Apps and web apps are ActionButtons

The system MUST expose a `FavoritesProvider` that reads `Prefs.favorites` and emits one
`ActionButton(kind = APP, a = package)` per installed, non-agent favorite, and a `WebProvider` that reads
`Prefs.webApps` and emits one `ActionButton(kind = WEB, a = url)` per web app. These providers MUST NOT write
to `Prefs.favorites` / `Prefs.webApps`; the Settings app-picker and web-app form remain the only writers.

#### Scenario: A favorite renders as a tile

- **WHEN** the owner has favorited an installed app
- **THEN** an `APP` tile for it MUST appear on the home, and tapping it MUST launch the app

#### Scenario: Favorites config is untouched

- **WHEN** the home is rendered from providers
- **THEN** `Prefs.favorites` and `Prefs.webApps` MUST be byte-for-byte unchanged by rendering or arranging

### Requirement: Launch kinds never show a firing state

`APP`, `WEB`, and `SHORTCUT` are launch kinds: tapping opens something and the tile MUST NOT enter the
`pending → success/error/timeout` state machine ([SPEC-0002](../http-actions/spec.md)). `HTTP` and
`HASS_SCENE` remain fire kinds and MUST keep the on-tile state machine.

#### Scenario: Tapping an app is quiet

- **WHEN** the owner taps an `APP` or `WEB` tile
- **THEN** it MUST launch immediately with no ring/spinner/timeout, and no error tile on a normal launch

### Requirement: Uniform presentation with per-kind taglines

Every tile MUST render with the same density-aware presentation (SLIM / REGULAR / RICH, one home-wide
setting) — no apps-vs-actions split, no section grouping. A tile's **subtitle** MUST be metadata-driven per
kind: `WEB` its host, `HTTP` `METHOD · host`, `SHORTCUT` "shortcut", `APP` no subtitle (name only). The
firing status line MUST appear only on fire kinds (`HTTP`, `HASS_SCENE`); launch kinds MUST NOT show a
"tap to fire" status.

#### Scenario: Apps and actions look the same

- **WHEN** the home renders a favorite app and an HTTP action at the same density
- **THEN** both MUST use the same tile chrome; the app MUST show no firing status while the HTTP action shows
  its `METHOD · host` subtitle and fire status

### Requirement: Filter by kind

The home MUST offer a filter chip row — `All` plus one chip per kind currently present — that narrows the
tiles to a single kind; the active filter MUST persist (`Prefs.tileFilter`). A Settings control MUST let the
owner choose which per-kind chips appear (`Prefs.hiddenFilterKinds`).

#### Scenario: Filtering narrows the home

- **WHEN** the owner taps the `HTTP` chip
- **THEN** only HTTP tiles (plus the trailing Store tile) MUST show, and the chip reads as active

### Requirement: One layout orders every tile

`Prefs.tileLayout` (an ordered list of tile keys) MUST be the single order authority across all providers and
stored actions, generalizing the `action_buttons` order and replacing the alphabetical favorites order.
Reconciliation MUST preserve existing order and append newly-appearing tiles to the tail; it MUST NOT
reshuffle on every render. On first run the layout MUST be seeded from the current arrangement (favorites
alphabetical, then the Actions order) so the home is visually unchanged.

#### Scenario: Order is stable and manual

- **WHEN** the owner arranges tiles (including apps) and later returns home
- **THEN** the saved order MUST be preserved, and a newly favorited app MUST append (not reshuffle the rest)

### Requirement: One layout orders every tile

`Prefs.tileLayout` (an ordered list of tile keys) MUST be the single order authority across all providers and
stored actions, generalizing the `action_buttons` order and replacing the alphabetical favorites order.
Reconciliation MUST preserve existing order and append newly-appearing tiles into their default section; it
MUST NOT reshuffle on every render. On first run the layout MUST be seeded from the current arrangement
(favorites alphabetical, then the Actions order) so the home is visually unchanged.

#### Scenario: Order is stable and manual

- **WHEN** the owner arranges tiles (including apps) and later returns home
- **THEN** the saved order MUST be preserved, and a newly favorited app MUST append (not reshuffle the rest)

### Requirement: Arrange, enable/disable, hide, and icon apply to every tile

The Arrange Action Buttons screen MUST let the owner reorder tiles across sections, move a tile between
sections, and toggle any tile on/off; `disabled_action_keys`, `hidden_items`, and icon overrides (keyed by
`ActionButton.key`) MUST apply uniformly to `APP`/`WEB` tiles as they do to action tiles.

#### Scenario: An app can be disabled from Arrange

- **WHEN** the owner flips an `APP` tile off in Arrange
- **THEN** it MUST disappear from the home Apps section and MUST re-appear when flipped back on

### Requirement: The featured agent stays special

The featured agent MUST remain a distinct hero surface (not an `ActionButton`) and MUST NOT appear in any
section or in Arrange. Its card background MUST be accent-tinted so it reads apart from the tile grid.

#### Scenario: Agent is not a tile

- **WHEN** the home renders
- **THEN** the agent hero MUST render once as the hero card, never as a grid/section tile
