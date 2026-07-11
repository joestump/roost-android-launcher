---
status: draft
date: 2026-07-11
implements: [ADR-0002]
---

# SPEC-0001: Action Buttons

## Overview

Action buttons let the user place items on the Roost home screen that — unlike app tiles, which open an app — perform a single action when tapped. Each button has an icon and a short title. Buttons are **pluggable by provider**; the first two providers are Android app-shortcuts and Home Assistant scenes. This spec realizes [ADR-0002](../../adrs/ADR-0002-pluggable-action-button-providers.md) and operates under the framework-only constraint of [ADR-0001](../../adrs/ADR-0001-framework-only-zero-dependency-launcher.md).

## Requirements

### Requirement: Uniform pluggable button model

The system MUST represent every action button with a single value type carrying a provider kind, a stable unique key, a short title, and two provider-specific string arguments. The home-screen render path and the persistence layer MUST be generic over the provider kind. Adding a new provider MUST require changes ONLY to: the provider-kind enumeration, the provider's own scan/invoke object, the icon and invoke dispatch sites, and one settings picker section — and MUST NOT require changes to persistence or the home render loop.

#### Scenario: Rendering is provider-agnostic

- **WHEN** the enabled set contains buttons of more than one provider kind
- **THEN** every button MUST render identically (icon + short title) in the same row, with no provider-specific branches in the render loop beyond icon resolution

#### Scenario: A button carries enough to invoke it

- **WHEN** a button is persisted and later loaded
- **THEN** its kind plus its two arguments MUST be sufficient to both resolve its icon and perform its action without any additional lookup keyed on transient state

### Requirement: Android app-shortcut provider

The system MUST be able to scan installed launchable apps for their published app-shortcuts (manifest, dynamic, and pinned) and present them for selection. Tapping an enabled app-shortcut button MUST start that shortcut. Shortcut discovery MUST use the platform launcher API and MUST reflect the shortcut's own label and icon.

#### Scenario: Scan surfaces an app's shortcuts

- **WHEN** the user runs the app-shortcut scan and an installed app publishes one or more shortcuts
- **THEN** each shortcut MUST appear as a selectable item labeled with the app name and the shortcut label

#### Scenario: Tapping launches the shortcut

- **WHEN** the user taps an enabled app-shortcut button on the home screen
- **THEN** the system MUST start that shortcut, and if it can no longer be started the system MUST fail without crashing

### Requirement: Home Assistant accounts

The system MUST allow the user to connect one or more Home Assistant instances, each defined by a display name, a base URL, and a long-lived access token. Bare hostnames entered without a scheme MUST default to `https://`. Removing an account MUST also remove any action buttons that belonged to that account.

#### Scenario: Adding an account

- **WHEN** the user submits a base URL and a token
- **THEN** the account MUST be persisted and MUST appear in the account list; if the URL or token is empty the system MUST reject the submission and inform the user

#### Scenario: Removing an account cleans up its buttons

- **WHEN** the user removes a Home Assistant account that had enabled scene buttons
- **THEN** those scene buttons MUST be removed from the enabled set

### Requirement: Home Assistant scene provider

For a connected account, the system MUST retrieve the list of scenes and present them for selection. Scene retrieval MUST read the instance's entity states and include only entities whose id is in the `scene.` domain, labeling each by its friendly name where available. Tapping an enabled scene button MUST activate that scene on its account.

#### Scenario: Loading scenes for an account

- **WHEN** the user loads scenes for a connected account that has scenes
- **THEN** each `scene.*` entity MUST appear as a selectable item labeled with its friendly name

#### Scenario: Activating a scene

- **WHEN** the user taps an enabled scene button
- **THEN** the system MUST request that the scene be turned on via that account, and MUST report success or failure to the user

### Requirement: Persistence

Enabled action buttons and Home Assistant accounts MUST persist across app and device restarts in `SharedPreferences`, each serialized as a single JSON string. The schema MUST be additive/tolerant: an unrecognized or malformed entry MUST be skipped rather than causing a failure to load the remaining entries. No schema migration step is REQUIRED.

#### Scenario: State survives a restart

- **WHEN** the app process is killed and relaunched
- **THEN** the previously enabled buttons and connected accounts MUST still be present

#### Scenario: A malformed entry is tolerated

- **WHEN** the persisted button array contains an entry with an unknown kind
- **THEN** that entry MUST be skipped and all valid entries MUST still load

### Requirement: Home-screen rendering

Enabled action buttons MUST render on the home screen as a horizontal row of pill-shaped controls (icon + short title), visually distinct from the app-tile grid. The row MUST appear only when at least one button is enabled and MUST NOT occupy space otherwise.

#### Scenario: No buttons, no row

- **WHEN** no action buttons are enabled
- **THEN** the home screen MUST NOT show an action-button row

#### Scenario: Buttons render as a distinct row

- **WHEN** one or more action buttons are enabled
- **THEN** they MUST render as pills in a single row that is not the app-tile grid

### Requirement: Picker and management UI

Action-button configuration MUST live in a dedicated settings screen, reachable from the launcher's settings. The screen MUST let the user review and remove currently enabled buttons, scan for app-shortcuts, manage Home Assistant accounts, and load and toggle scenes per account. Toggling an item MUST immediately enable or disable the corresponding button.

#### Scenario: Toggling enables a button

- **WHEN** the user ticks a scanned shortcut or a loaded scene
- **THEN** the corresponding action button MUST be added to the enabled set and appear on the home screen

#### Scenario: Reviewing and removing

- **WHEN** the user opens the action-buttons screen with buttons enabled
- **THEN** each enabled button MUST be listed with a control to remove it

### Requirement: Threading and error handling

All network operations (Home Assistant scene retrieval and activation) MUST run off the main thread. Failures — network errors, non-success responses, missing shortcuts — MUST be surfaced to the user (inline or via a transient message) and MUST NOT crash the app and MUST NOT be silently swallowed. UI updates that follow a background operation MUST be marshaled back to the main thread.

#### Scenario: A Home Assistant call fails

- **WHEN** loading scenes or activating a scene fails (unreachable host, bad token, error response)
- **THEN** the user MUST be shown an error indication and the app MUST remain responsive

#### Scenario: Network never blocks the UI thread

- **WHEN** any Home Assistant request is made
- **THEN** it MUST execute on a background thread, and the UI MUST never perform network I/O on the main thread

### Requirement: Default-launcher dependency

App-shortcut discovery and launching depend on Roost holding the default HOME role, which the platform requires for the launcher shortcut API. When Roost is not the default launcher, shortcut scanning MUST yield an empty result WITHOUT raising an error, and previously-enabled shortcut buttons MUST fail gracefully when tapped.

#### Scenario: Not the default launcher

- **WHEN** Roost is not the current default HOME app and the user runs a shortcut scan
- **THEN** the scan MUST return no shortcuts and MUST NOT crash or surface a fatal error
