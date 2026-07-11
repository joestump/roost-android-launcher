---
title: Provisioning a device
sidebar_position: 5
---

# Provisioning a device

Roost handles the *launcher*. Signing into accounts is up to you — the launcher never touches credentials.
Here's the one-time setup for turning a spare phone into an agent device.

## 1. Prep the phone

- Factory-reset (recommended) so it's genuinely the agent's device.
- Sign it into the **Google account** the device should use, so you can install from Play.
- Enable **Developer options** (tap Build number 7×) and **USB / Wireless debugging**.

## 2. Install the apps

Install your **agent app** and the utilities you want — commonly a **VPN client** (e.g. WireGuard), a
**mail app**, and a **password manager**. From a shell you can jump straight to a Play listing:

```bash
adb shell am start -a android.intent.action.VIEW -d "market://details?id=<package.name>"
```

## 3. Install Roost & make it home

See [Install &amp; build](./install.md). In short: `adb install -r`, then set Roost as the HOME app and
point **Featured agent app** at your agent's package.

## 4. Sign in & connect

- Sign into the agent account, mail, and password manager.
- Import your VPN config. For WireGuard, you can push a `.conf` to the phone and import it from file:

  ```bash
  adb push my-tunnel.conf /sdcard/Download/
  ```

  :::caution WireGuard tunnel names
  WireGuard derives the tunnel name from the filename and requires **≤15 characters** (letters, digits, and
  `_=+.-` only). A long filename fails with *"Unable to import - invalid name."* Rename before importing.
  :::

## 5. Tidy the grid

Open **Roost → Apps & settings**, tick the apps you want on the grid, pick an **accent tint**, and hit
**Match wallpaper to Roost** so Recents and transitions match.

Reboot to confirm it lands straight in your agent app.
