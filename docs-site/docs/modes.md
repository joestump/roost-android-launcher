---
title: Modes & settings
sidebar_position: 3
---

# Modes & settings

Roost has two home behaviors, switchable in **Settings → Home & Behavior → Home mode**.

## Curated

Pressing Home shows the full home surface: the robot **mascot**, a live **greeting**, a Sage-green **VPN
chip**, an accent-tinted full-width **hero card** for the **featured agent**, then **one uniform grid of
tiles**. Everything except the hero is the same tile — favorite apps, web apps, app shortcuts, Home Assistant
scenes, and [HTTP actions](./http-actions.md) — with a **Store** tile at the tail for adding more. A **filter
chip row** above the grid (`All`, plus a chip per kind present — Apps / Web / Shortcuts / HTTP / Scenes)
narrows the tiles. Every tile carries the same three lines — a **title**, a **metadata subtitle**, and an
**action line** — filled per kind: an app its category + "tap to open", a web tile its host, a shortcut
"shortcut" + "tap to run", an HTTP tile `METHOD · host` + its fire state. Tapping the mascot or the hero card
opens your agent. On boot, the agent app is foregrounded once — optionally behind a
[waking-up sequence](./design.md#waking-up).

This is the relaxed default — a normal launcher that happens to be built around one agent.

## Appliance

Pressing Home shows a minimal **ambient "at rest" face** — just the mascot and greeting, like a robot's
idle dock screen. A **long-press anywhere** reveals the tile grid for that visit only (it re-hides when
you leave). On boot, the agent app is foregrounded once.

This is the "dedicated appliance" feel — the utilities are deliberately out of the way.

## Settings

Settings open as a calm **landing** that drills into per-category screens — see
[The Settings screens](./settings.md) for the full information architecture. The controls, wherever they now
live:

| Setting | What it does |
| --- | --- |
| **Home mode** | Curated vs Appliance (above). |
| **Agent name** | Names your agent (e.g. its username). The greeting becomes "&lt;name&gt; is home" and the mono status line reads "&lt;name&gt; · docked & charging · 87%". Blank falls back to "roost". |
| **Featured agent app** | Point the featured **hero card** at any installed app via a searchable **app picker** — icon + name, no package strings. |
| **Restart agent app** | Bounce the agent app cleanly when it needs a fresh start. |
| **Auto-launch agent on boot** | Foreground the agent app once after boot. |
| **Keep screen on while docked** | Hold Roost's window awake (pair with the `stay_on_while_plugged_in` global for a true always-on dock). |
| **Accent tint** | Honey, Slate, Sage, or Violet — recolors the mascot eyes, chips, glows, the Store tile, and monochrome tile icon glyphs live (full-color icons and health colors stay fixed). |
| **Action density** | How every home tile renders — a Slim list, Regular cards, or a Rich two-column grid. One home-wide setting; every tile reshapes together. |
| **Launcher filters** | Which per-kind filter chips (Apps / Web / Shortcuts / HTTP / Scenes) can appear above the tiles on the home. |
| **Favorites** | Which installed apps appear on the grid — a searchable **app picker**. They surface as `APP` tiles and are now manually orderable alongside everything else. |
| **Web Apps** | Add a name + URL that opens **fullscreen in a WebView** — self-host a Homarr / Homepage dashboard as a tile. `https://` is auto-prefixed. |
| **Action Buttons** | Enable [HTTP action tiles](./http-actions.md) for the home grid, and manage Home Assistant accounts. |
| **Match wallpaper to Roost** | Paint a matching dock-dark wallpaper so Recents and app transitions stay on-theme. |

The mono status line and greeting update **live** from a battery-change receiver, so charging state and %
are never stale.

## Why not a hard kiosk?

Android's Lock Task Mode can pin the phone to a single app, but then you can't reach your VPN client to
bring a tunnel up, or your password manager to sign in. Roost is a **curated launcher** instead: it feels
like a dedicated agent device, but the handful of apps the agent needs are always one tap (or one
long-press) away.
