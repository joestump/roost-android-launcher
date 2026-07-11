---
title: Modes & settings
sidebar_position: 3
---

# Modes & settings

Roost has two home behaviors, switchable in **Apps & settings → Home mode**.

## Curated

Pressing Home shows the full home surface: the robot **mascot**, a **greeting**, and one aligned grid —
the **featured agent** first (same size as the rest, marked with an accent ring), then your **utility
apps**, your **web apps**, and an **Add** tile. Tapping the mascot or the ringed tile opens your agent. On
boot, the agent app is foregrounded once.

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
| **Agent name** | Names your agent (e.g. its username). The greeting becomes "&lt;name&gt; is home" and the mono status line reads "&lt;name&gt; · docked & charging · 87%". Blank falls back to "roost". |
| **Auto-launch agent on boot** | Foreground the agent app once after boot. |
| **Keep screen on while docked** | Hold Roost's window awake (pair with the `stay_on_while_plugged_in` global for a true always-on dock). |
| **Accent tint** | Honey, Slate, Sage, or Violet — recolors the mascot eyes, ring, glows, and the Add tile live. |
| **Featured agent app** | The package launched on boot and shown as the ringed tile. |
| **Favorites** | Which installed apps appear on the grid. |
| **Web apps** | Add a name + URL that opens **fullscreen in a WebView** — self-host a Homarr / Homepage dashboard as an app tile. `https://` is auto-prefixed. |
| **Match wallpaper to Roost** | Paint a matching dock-dark wallpaper so Recents and app transitions stay on-theme. |

The mono status line and greeting update **live** from a battery-change receiver, so charging state and %
are never stale.

## Why not a hard kiosk?

Android's Lock Task Mode can pin the phone to a single app, but then you can't reach your VPN client to
bring a tunnel up, or your password manager to sign in. Roost is a **curated launcher** instead: it feels
like a dedicated agent device, but the handful of apps the agent needs are always one tap (or one
long-press) away.
