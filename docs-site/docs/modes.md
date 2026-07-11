---
title: Modes & settings
sidebar_position: 3
---

# Modes & settings

Roost has two home behaviors, switchable in **Apps & settings → Home mode**.

## Curated

Pressing Home shows the full home surface: the robot **mascot**, a **greeting**, a **featured agent card**
(tap to open your agent), and a **grid of your utility apps** plus an **Add** tile. On boot, the agent app
is foregrounded once.

This is the relaxed default — a normal launcher that happens to be built around one agent.

## Appliance

Pressing Home shows a minimal **ambient "at rest" face** — just the mascot and greeting, like a robot's
idle dock screen. A **long-press anywhere** reveals the utility grid for that visit only (it re-hides when
you leave). On boot, the agent app is foregrounded once.

This is the "dedicated appliance" feel — the utilities are deliberately out of the way.

## Settings

| Setting | What it does |
| --- | --- |
| **Home mode** | Curated vs Appliance (above). |
| **Auto-launch agent on boot** | Foreground the agent app once after boot. |
| **Keep screen on while docked** | Hold Roost's window awake (pair with the `stay_on_while_plugged_in` global for a true always-on dock). |
| **Accent tint** | Honey, Slate, Sage, or Violet — recolors the mascot eyes, chips, glows, and the Add tile live. |
| **Featured agent app** | The package launched on boot and shown in the featured card. |
| **Favorites** | Which installed apps appear on the grid. |
| **Match wallpaper to Roost** | Paint a matching dock-dark wallpaper so Recents and app transitions stay on-theme. |

## Why not a hard kiosk?

Android's Lock Task Mode can pin the phone to a single app, but then you can't reach your VPN client to
bring a tunnel up, or your password manager to sign in. Roost is a **curated launcher** instead: it feels
like a dedicated agent device, but the handful of apps the agent needs are always one tap (or one
long-press) away.
