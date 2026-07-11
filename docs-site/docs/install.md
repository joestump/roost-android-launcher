---
title: Install & build
sidebar_position: 2
---

# Install & build

## Build the APK

Requires **JDK 17** and the **Android SDK** (platform 34, build-tools 34.0.0).

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || echo /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home)"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

## Install & set as default launcher

1. Enable **Developer options → USB debugging** (or **Wireless debugging**) and confirm the device shows up:

   ```bash
   adb devices
   ```

2. Install the APK:

   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

3. Set Roost as the home app — press Home once and pick **Roost**, or do it over adb:

   ```bash
   adb shell cmd package set-home-activity rocks.stump.roost/.MainActivity
   ```

   On Android 12+ you can confirm it stuck:

   ```bash
   adb shell cmd role get-role-holders android.app.role.HOME
   # → rocks.stump.roost
   ```

4. Point Roost at your agent app. Open **Roost → Apps & settings → Featured agent app** and set the
   package name if it isn't the default. To see what's installed:

   ```bash
   adb shell pm list packages
   ```

## Optional: keep the screen awake while docked

```bash
adb shell settings put global stay_on_while_plugged_in 3
```

Roost also has a **Keep screen on while docked** toggle in Settings that keeps its own window awake.

:::tip Wireless install
If USB is flaky (hubs and charge-only cables are common culprits), use **Wireless debugging**:
`adb pair <ip:port>` with the pairing code, then `adb connect <ip:port>` using the address on the main
Wireless-debugging screen.
:::
