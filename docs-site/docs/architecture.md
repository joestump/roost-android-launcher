---
title: How it works
sidebar_position: 9
---

# How it works

Roost is still intentionally small and still pure framework. It's a HOME-category activity, a boot receiver,
a `Canvas` mascot, a palette helper, a settings landing that drills into per-category screens, and an
HTTP-action client + firing tile — no database, no analytics. The only network traffic is the
[HTTP actions](./http-actions.md) you define yourself and the VPN chip's rate readout.

## Files

| File | Role |
| --- | --- |
| `MainActivity.kt` | The HOME surface — renders the curated home, the featured hero card, the Actions zone, and the appliance face. |
| `MascotView.kt` | The LED-eyed robot, drawn with `Canvas` (idle breathing/blink + awake pulse). |
| `Roost.kt` | Palette tokens (accent + fixed semantic ramp), drawable helpers, and the wallpaper painter. |
| `Prefs.kt` | Typed `SharedPreferences` wrapper — the single source of truth (apps, web apps, action buttons, HTTP-action definitions, secrets). |
| `BootReceiver.kt` | Catches `BOOT_COMPLETED`, arms a one-shot launch flag. |
| **HTTP actions** ([ADR-0004](#architecture-decisions) / SPEC-0002) | |
| `HttpActionClient.kt` | Framework-only client — method, arbitrary headers, `None`/`Bearer`/`HMAC` auth, `{{var}}` body substitution (`HttpURLConnection` + `org.json` + `javax.crypto.Mac`). |
| `ActionTileView.kt` | The `Canvas` action tile — the `idle → pending → success/queued → error → timeout` firing state machine on a `Handler` tick. |
| `HttpActionActivity.kt` | The builder — method/auth segmented controls, header rows, JSON body + variable chips, test-fire. |
| `EndpointsActivity.kt` | "Pick from my endpoints" — pre-wired templates + a raw-request path. |
| **Settings** ([ADR-0005](#architecture-decisions)) | |
| `SettingsActivity.kt` | Now a **landing** — a device-identity strip + category rows, not one long scroll. |
| `SettingsScreen.kt` | Shared row/control vocabulary reused by every settings screen. |
| `BehaviorActivity.kt` | Home & behavior — home mode, auto-launch, keep-screen-on, bandwidth heartbeat. |
| `AgentActivity.kt` | Agent — inline name, featured-app picker, restart agent app. |
| `AppearanceActivity.kt` | Appearance — accent tint, match wallpaper, action density. |
| `AppsActivity.kt` | Apps, tiles & content — drills into Favorites, Web apps, Action buttons, Hidden. |
| `AppPickerActivity.kt` | Searchable app picker (icon + name) for the featured agent and Favorites. |
| `WebAppsActivity.kt` | Manage web apps (name + URL → fullscreen WebView). |
| `HiddenActivity.kt` | Manage hidden items. |
| `NetworkActivity.kt` | Network — WireGuard tunnel + remote-control note. |
| `ActionsActivity.kt` | Action buttons — the landing that drills into HTTP actions, Home Assistant, App shortcuts, Arrange on home, and Synced actions. |
| `HttpActionsActivity.kt` | HTTP actions — the action list, New action (builder / endpoints picker), per-action enabled toggles. |
| `HassActivity.kt` | Home Assistant — the account-form authoring path that produces an HTTP action. |
| `ShortcutsActivity.kt` | App shortcuts — enable Android launcher-shortcut action buttons. |
| `ArrangeActivity.kt` | Arrange on home — drag-to-reorder the enabled action buttons into the home Actions-zone order. |
| **Synced actions** ([ADR-0006](#architecture-decisions) / SPEC-0003) | |
| `SyncedActions.kt` | The reconciler — reads `actions.d/*.json` from the granted tree URI and upserts/removes `ActionKind.HTTP` actions, scoped to a tracked synced-id set (`DocumentsContract` + `ContentResolver` + `org.json`). |
| `SyncedActionsActivity.kt` | Synced actions settings — grant/clear the folder, show count + last-sync status, and Sync now. |

## Architecture decisions

The bigger moves are recorded as ADRs (MADR format) and formalized as specs, in the repo under `docs/`:

| Record | What it decides |
| --- | --- |
| [ADR-0001](https://gitea.stump.rocks/joestump/roost-android-launcher/src/branch/main/docs/adrs/ADR-0001-framework-only-zero-dependency-launcher.md) | Framework-only, zero-dependency launcher (no AndroidX/Compose/Material/libraries). |
| [ADR-0002](https://gitea.stump.rocks/joestump/roost-android-launcher/src/branch/main/docs/adrs/ADR-0002-pluggable-action-button-providers.md) | Pluggable action-button providers (uniform `ActionButton` model). |
| [ADR-0003](https://gitea.stump.rocks/joestump/roost-android-launcher/src/branch/main/docs/adrs/ADR-0003-icon-rendering-strategy.md) | Icon rendering strategy (framework-only fetch/cache + SVG-path renderer). |
| [ADR-0004](https://gitea.stump.rocks/joestump/roost-android-launcher/src/branch/main/docs/adrs/ADR-0004-generalized-http-action-provider.md) | Generalized [HTTP-action provider](./http-actions.md) with on-tile firing feedback. |
| [ADR-0005](https://gitea.stump.rocks/joestump/roost-android-launcher/src/branch/main/docs/adrs/ADR-0005-settings-navigation-ia.md) | [Settings navigation IA](./settings.md) — a landing + per-category detail Activities. |
| [ADR-0006](https://gitea.stump.rocks/joestump/roost-android-launcher/src/branch/main/docs/adrs/ADR-0006-declarative-action-provisioning.md) | [Declarative action provisioning](./synced-actions.md) — import agent-authored `actions.d/*.json` from a synced folder. |
| [SPEC-0001](https://gitea.stump.rocks/joestump/roost-android-launcher/src/branch/main/docs/openspec/action-buttons/spec.md) | Action Buttons (formalizes ADR-0002). |
| [SPEC-0002](https://gitea.stump.rocks/joestump/roost-android-launcher/src/branch/main/docs/openspec/http-actions/spec.md) | HTTP Actions (formalizes ADR-0004). |
| [SPEC-0003](https://gitea.stump.rocks/joestump/roost-android-launcher/src/branch/main/docs/openspec/synced-actions/spec.md) | [Synced Actions](./synced-actions.md) (formalizes ADR-0006). |

## Becoming the home screen

Roost registers `MainActivity` for the `HOME` + `DEFAULT` categories. Once it's the default home app,
pressing Home returns to it.

## The boot-into-agent flow

Starting an activity directly from `BOOT_COMPLETED` runs into Android's background-activity-launch limits.
Roost sidesteps that: the receiver just **arms a one-shot flag**, and the system starts the HOME activity
on its own. `MainActivity.onResume` consumes the flag and foregrounds the agent — a launch from the
*foreground* HOME activity, which is allowed.

1. Device boots and the system sends the `BOOT_COMPLETED` broadcast.
2. If **auto-launch on boot** is enabled, `BootReceiver` arms a one-shot `pendingBootLaunch` flag.
3. The system starts Roost's HOME activity on its own (Roost is the default launcher).
4. `MainActivity.onResume` runs:
   - if `pendingBootLaunch` is set → **clear it and `startActivity` the agent app**;
   - otherwise → **render the home surface**.

Because the `startActivity` call happens inside the foreground HOME activity's `onResume` (not inside the
receiver), it isn't subject to the background-launch restriction.

## Package visibility

To list installed apps for the favorites picker and to render their icons, Roost declares a `<queries>`
element for the `MAIN` / `LAUNCHER` intent (Android 11+ package visibility). Featured and utility tiles
pull each app's real icon via `PackageManager.getApplicationIcon()` at render time.

## Framework-only, on purpose

No AndroidX, no Compose, no Material, no libraries — the `dependencies {}` block is empty and
`android.useAndroidX=false`. Views are built programmatically; the mascot, the action tile, and the icon are
`Canvas`/`VectorDrawable`; networking is `HttpURLConnection` + `org.json` off-thread, and HMAC signing uses
the platform's own `javax.crypto.Mac` — still no third-party dependency. That keeps the APK tiny (well under
a megabyte) and the codebase easy to read end to end.
