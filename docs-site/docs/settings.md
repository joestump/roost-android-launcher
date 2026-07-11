---
title: The Settings screens
sidebar_position: 6
---

# The Settings screens

Settings began as one `SettingsActivity` — a single scroll of a dozen equally-weighted sections, with a
Favorites checkbox list that swallowed the page and no sense of *where am I*. It's now a calm **landing**
that drills into focused per-category screens.

This realizes [ADR-0005](https://gitea.stump.rocks/joestump/roost-android-launcher/src/branch/main/docs/adrs/ADR-0005-settings-navigation-ia.md).
Because Roost is [framework-only](./architecture.md) — no AndroidX, so no Jetpack Navigation and no
Fragments — the drill-down is plain `Activity` navigation: each screen is a normal `Activity`, `startActivity`
goes deeper, and the system **back stack** returns. A shared `SettingsScreen` component gives every screen
one row-and-control vocabulary, so nothing drifts.

## The landing

<img src="/roost-android-launcher/img/settings.png" alt="The Settings landing — a device identity strip, five category rows, and Open Android system settings" width="320" />

A **device-identity strip** at the top, then category rows — icon + label + summary + chevron — each opening
its own detail screen, plus a shortcut out to the OS:

| Category | What's inside |
| --- | --- |
| **Home & behavior** | Home mode (Curated / Appliance), auto-launch agent on boot, keep screen on while docked, bandwidth heartbeat. |
| **Agent** | Agent name, the featured-agent app picker, and **Restart agent app**. |
| **Appearance** | Accent tint (Honey / Slate / Sage / Violet), match wallpaper to Roost. |
| **Apps, tiles & content** | Favorites (an app picker), Web apps, Action buttons, Hidden items. |
| **Network** | WireGuard tunnel + a remote-control note. |
| **Open Android system settings** | Jump straight to the OS Settings. |

Every setting that lived in the old monolith is still here — the redesign is information architecture and
polish, not a feature cut.

## App pickers, not package fields

The two lists that used to hurt most — the featured agent (a raw package-name text field) and Favorites (a
wall of checkboxes) — are now **searchable app pickers**: icon + name + check, backed by `AppPickerActivity`.

<img src="/roost-android-launcher/img/settings-agent.png" alt="The Agent detail screen — inline agent name, the featured-app picker, and Restart agent app" width="320" />

The **Agent** screen sets the agent name inline, points the featured hero card at any installed app through
the picker, and adds a **Restart agent app** control for when the agent needs a clean bounce.

<img src="/roost-android-launcher/img/settings-favorites.png" alt="The Favorites screen — a searchable app-picker grid of icons and names" width="320" />

**Favorites** is the same picker idea as a searchable grid — tap to add or remove an app from the home
surface, no package strings anywhere.

## Action buttons

Under **Apps, tiles & content → Action buttons** is where [HTTP action tiles](./http-actions.md) are managed:

<img src="/roost-android-launcher/img/settings-actions.png" alt="The Action buttons screen — an HTTP actions section with New action, per-action enabled toggles, and a Home Assistant account form" width="320" />

- An **HTTP actions** section with a **New action** entry (into the builder / endpoints picker) and a
  per-action **enabled** toggle.
- The **Home Assistant** account form, now just one authoring path that produces an HTTP action.

The deep configuration — the HTTP-action builder, the endpoints picker, the icon picker — stays bespoke; the
navigation around it is uniform.
