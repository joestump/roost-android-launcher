---
status: draft
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

### Requirement: Section is a property; sections group the home

Each tile MUST have a **section** (`Prefs.tileSection`, key → section), defaulting from kind (`APP`/`WEB` →
Apps; `SHORTCUT`/`HASS_SCENE`/`HTTP` → Actions) and reassignable per tile. The home MUST render sections in a
defined order, each with its eyebrow label, and MUST render each section with its declared **presentation** —
`GRID` (compact icon + label, N-across) or `TILES` (density-aware SLIM/REGULAR/RICH). A tile's presentation
follows its section, so any kind can appear in any section.

#### Scenario: Default sections preserve today's home

- **WHEN** a fresh install renders the home
- **THEN** apps/web MUST group under an Apps (GRID) section and shortcuts/HASS/HTTP under an Actions (TILES)
  section, matching the pre-unification layout

#### Scenario: Moving a tile's section moves and re-presents it

- **WHEN** the owner moves an `APP` tile into the Actions section
- **THEN** it MUST render as a density tile under Actions and still launch on tap

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
