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

It's **~700 lines of pure Android framework Kotlin** — no AndroidX, no Compose, no Material, no raster
assets, no bundled fonts. Deliberately hackable.

## The idea

A phone that isn't *yours* — it's the **agent's**. Its own login, its own VPN tunnel, its own mail and
password accounts. Roost gives that phone an identity: a little LED-eyed robot that "lives" on the device,
a greeting, and a dock-dark home screen that reads as *the agent's home*, not any one vendor's.

## Vendor-neutral by design

The featured tile renders the **installed agent app's own icon at runtime**, so the model's branding comes
for free while Roost's own identity stands on its own. Point it at any agent app in Settings.

## What it does

- **Boots into your agent** on power-up.
- **Curated home**: a robot mascot, a greeting, a featured agent card, and a grid of your utility apps.
- **Appliance mode**: an ambient "at rest" face; long-press reveals the grid.
- **Themeable accent** (Honey / Slate / Sage / Violet), **keep-screen-on**, and a **matching wallpaper** so
  Recents and app transitions stay on-theme.

## Get started

- [Install &amp; set as default launcher](./install.md)
- [Home modes &amp; settings](./modes.md)
- [The Roost design](./design.md)
- [Provisioning a device](./provisioning.md)
- [How it works](./architecture.md)

The source lives on [Gitea](https://gitea.stump.rocks/joestump/roost-android-launcher) (primary) and mirrors
to [GitHub](https://github.com/joestump/roost-android-launcher).
