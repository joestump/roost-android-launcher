# Claude Home

A tiny, dependency-free Android launcher that turns a spare phone into a **dedicated Claude device**.
It boots straight into the Claude app but keeps a short list of utility apps (WireGuard, Proton Pass,
Proton Mail, …) one tap away — because a hard single-app kiosk can't hop to WireGuard when you need to
bring a tunnel up.

It's ~400 lines of pure Android framework Kotlin (no AndroidX, no Compose) — deliberately hackable.

## Two modes (toggle in Settings)

| Mode | Home button shows | Boot |
| --- | --- | --- |
| **Curated** | The favorites grid (Claude featured, then your pinned apps) | Auto-launches Claude once |
| **Appliance** | A minimal Claude "lock" screen; **long-press** anywhere reveals the grid for that visit only | Auto-launches Claude once |

Other behavior toggles: **auto-launch Claude on boot**, **keep screen on**, a free-text **Claude package
override**, and a **favorites picker** listing every launchable app on the device.

## How it works

- The app registers as the `HOME` activity, so pressing Home returns here.
- `BootReceiver` catches `BOOT_COMPLETED` and arms a one-shot flag; `MainActivity.onResume` consumes it and
  foregrounds Claude. (The actual `startActivity` runs from the foreground HOME activity, sidestepping
  Android's background-activity-launch limits.)
- Favorites and settings live in `SharedPreferences` (`Prefs.kt`) — no database, no network, no analytics.

## Build

Requires JDK 17 and the Android SDK (platform 34, build-tools 34.0.0).

```sh
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

## Install & set as default launcher

1. Enable **Developer options → USB debugging** on the phone, connect it, and confirm `adb devices` lists it.
2. Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
3. Press Home once and pick **Claude Home** as the default launcher (or:
   `adb shell cmd package set-home-activity rocks.stump.claudelauncher/.MainActivity`).
4. Confirm the Claude package name on-device and set it in Settings if it differs from the default:
   `adb shell pm list packages | grep -i claude`  (default assumed: `com.anthropic.claude`).

Optional: keep the screen awake while charging (device-wide):
`adb shell settings put global stay_on_while_plugged_in 3`

## Provisioning the device (one-time, done on the phone)

These are account/UI steps the launcher can't (and shouldn't) do for you:

1. Sign the device into its Google account and install from Play: **Claude**, **WireGuard**,
   **Proton Pass**, **Proton Mail**.
2. Sign into the dedicated Claude agent account, and the Proton / WireGuard accounts.
3. Import the WireGuard tunnel config and test the tunnel.
4. Open **Claude Home → Apps & settings** and confirm the four apps are checked as favorites.

## Roadmap

- **v2:** one-tap WireGuard tunnel toggle tile (via WireGuard's `SET_TUNNEL_UP/DOWN` intent API).
- **v2:** optional "focus" watcher that relaunches Claude if it's killed in the background.
- Favorite reordering; per-favorite custom labels.

## License

Personal project. Not affiliated with Anthropic.
