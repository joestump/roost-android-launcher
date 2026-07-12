---
slug: /
title: What is Roost?
sidebar_position: 1
---

# Roost

**A dedicated device for your AI agent** — a tiny, vendor-neutral Android launcher that turns a spare
phone into a little home for one agent.

Roost boots straight into your agent app (Claude, ChatGPT, Gemini, a local model — whatever you pick) but
keeps a short list of utility apps one tap away, because a hard single-app kiosk can't hop to your VPN
client when you need to bring a tunnel up.

It's **a few thousand lines of pure Android framework Kotlin** — no AndroidX, no Compose, no Material, no
raster assets, no bundled fonts. Deliberately hackable.

<img src="/roost-android-launcher/img/home.png" alt="The Roost home: mascot, greeting, a Sage VPN chip, the featured-agent hero card, a utility grid, and the Actions zone" width="320" />

## The idea

A phone that isn't *yours* — it's the **agent's**. Its own login, its own VPN tunnel, its own mail and
password accounts. Roost gives that phone an identity: a little LED-eyed robot that "lives" on the device,
a greeting, and a dock-dark home screen that reads as *the agent's home*, not any one vendor's.

## Vendor-neutral by design

The featured agent is a full-width **hero card** that renders the **installed agent app's own icon and name
at runtime**, so the model's branding comes for free while Roost's own identity stands on its own. Point it
at any agent app in Settings.

## What it does

- **Boots into your agent** on power-up.
- **Curated home**: a robot mascot with a live greeting, a **Sage-green VPN chip** showing live ↓/↑ rates, a
  full-width **hero card** for the featured agent, a utility grid, and — once you add any — an **Actions
  zone**.
- **HTTP Action tiles**: a home tile that fires a user-defined HTTP request (method, URL, headers, an auth
  scheme, and a `{{var}}` JSON body) and tells you how it went **right on the tile** — firing → done /
  accepted / error. [Read more](./http-actions.md).
- **Synced Actions**: an agent can provision action buttons by dropping `actions.d/*.json` files into a
  folder that syncs to the phone — write a file, the button appears. [Read more](./synced-actions.md).
- **Three Actions-zone densities**: the Actions zone reshapes between a slim list, a regular card stack,
  and a rich two-column grid — set in Settings → Appearance.
- **Appliance mode**: an ambient "at rest" face; long-press reveals the grid.
- **Redesigned Settings**: a calm landing that drills into per-category screens, with searchable **app
  pickers** for the featured agent and Favorites. [Read more](./settings.md).
- **Themeable accent** (Honey / Slate / Sage / Violet), **keep-screen-on**, and a **matching wallpaper** so
  Recents and app transitions stay on-theme.

## Get started

- [Install &amp; set as default launcher](./install.md)
- [Home modes &amp; settings](./modes.md)
- [The Roost design](./design.md)
- [HTTP Action tiles](./http-actions.md)
- [Synced Actions](./synced-actions.md)
- [The Settings screens](./settings.md)
- [Provisioning a device](./provisioning.md)
- [How it works](./architecture.md)

The source lives on [Gitea](https://gitea.stump.rocks/joestump/roost-android-launcher) (primary) and mirrors
to [GitHub](https://github.com/joestump/roost-android-launcher).
