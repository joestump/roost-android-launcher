# Roost

A tiny, dependency-free Android launcher that turns a spare phone into a **dedicated device for an AI
agent**. It boots straight into your agent app (Claude, ChatGPT, Gemini, a local model — whatever you
pick) but keeps a short list of utility apps (a VPN client, mail, a password manager) one tap away —
because a hard single-app kiosk can't hop to WireGuard when you need to bring a tunnel up.

Roost is ~700 lines of pure Android framework Kotlin — no AndroidX, no Compose, no Material, no raster
assets, no bundled fonts. Deliberately hackable. It's **vendor-neutral**: the featured tile renders the
installed agent app's own icon at runtime, so Roost's identity (a little robot that lives on the phone)
stands on its own.


## Two modes (toggle in Settings)

| Mode | Home button shows | Boot |
| --- | --- | --- |
| **Curated** | The robot mascot, a greeting, the featured agent card, and your utility grid | Auto-launches the agent once |
| **Appliance** | An ambient "at rest" face (mascot + greeting); **long-press** anywhere reveals the grid | Auto-launches the agent once |

Other toggles: **auto-launch the agent on boot**, **keep screen on while docked**, a **themeable accent**
(Honey / Slate / Sage / Violet), a **favorites picker**, the **featured agent app**, and a
**"Match wallpaper to Roost"** action that paints Recents/transitions in the same warm-dark palette.

## How it works

- Roost registers as the `HOME` activity, so pressing Home returns here.
- `BootReceiver` catches `BOOT_COMPLETED` and arms a one-shot flag; `MainActivity.onResume` consumes it
  and foregrounds the agent app. (The actual `startActivity` runs from the foreground HOME activity,
  sidestepping Android's background-activity-launch limits.)
- Favorites and settings live in `SharedPreferences` (`Prefs.kt`) — no database, no network, no analytics.
- The mascot is a `Canvas` view (`MascotView.kt`); the palette + drawable helpers live in `Roost.kt`.

## Build

Requires JDK 17 and the Android SDK (platform 34, build-tools 34.0.0).

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || echo /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home)"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

## Install & set as default launcher

1. Enable **Developer options → USB debugging** (or Wireless debugging) and confirm `adb devices` lists it.
2. Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
3. Press Home once and pick **Roost** as the default launcher (or:
   `adb shell cmd package set-home-activity rocks.stump.roost/.MainActivity`).
4. In **Roost → Apps & settings → Featured agent app**, set your agent app's package if it isn't the
   default (`adb shell pm list packages` lists what's installed).

Optional: keep the screen awake while charging (device-wide):
`adb shell settings put global stay_on_while_plugged_in 3`

## Provisioning the device (one-time, done on the phone)

1. Sign the device into its Google account and install your agent app + your utility apps.
2. Sign into the dedicated agent account, VPN, and any mail/password accounts.
3. Open **Roost → Apps & settings** and tick the apps you want on the grid.

## Roadmap

- One-tap VPN tunnel toggle tile.
- An "awake" mascot state (bigger eye-glow) driven by the agent actually working.
- Favorite reordering; per-favorite custom labels.

## License

MIT. Not affiliated with any AI vendor.
