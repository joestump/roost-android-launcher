---
title: How it works
sidebar_position: 6
---

# How it works

Roost is intentionally small. The whole app is a HOME-category activity, a boot receiver, a settings
screen, a `Canvas` mascot, and a palette helper — no database, no network, no analytics.

## Files

| File | Role |
| --- | --- |
| `MainActivity.kt` | The HOME surface — renders the curated home and the appliance face. |
| `SettingsActivity.kt` | Settings + favorites picker + accent chooser. |
| `BootReceiver.kt` | Catches `BOOT_COMPLETED`, arms a one-shot launch flag. |
| `MascotView.kt` | The LED-eyed robot, drawn with `Canvas`. |
| `Roost.kt` | Palette tokens, drawable helpers, and the wallpaper painter. |
| `Prefs.kt` | Typed `SharedPreferences` wrapper — the single source of truth. |

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

No AndroidX, no Compose, no Material, no libraries. Views are built programmatically; the mascot and icon
are `Canvas`/`VectorDrawable`; type uses system font families. That keeps the APK tiny (~800 KB) and the
codebase easy to read end to end.
