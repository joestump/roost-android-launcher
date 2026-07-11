---
title: The Roost design
sidebar_position: 4
---

# The Roost design

Roost's visual identity is "an AI agent's home" — warm, calm, characterful, and **not tied to any one
vendor**. Everything is drawn with Android framework primitives: colors, gradients, `Canvas`, and
`VectorDrawable`. No raster assets, no bundled fonts.

## The mascot

A tiny **LED-eyed robot** — a rounded-rect head, two glowing circular eyes, and a little antenna. It's a
single `Canvas` view (`MascotView.kt`): the eyes are filled circles over a soft `RadialGradient` glow, and
they **widen (bigger glow) in the "awake" state**. No image files involved.

## Palette

A warm neutral base with **one themeable accent**.

| Token | Hex | Role |
| --- | --- | --- |
| Dock black | `#14110D` | Background base (radial toward `#1D1912`) |
| Panel | `#1C1813` | Cards / featured panel |
| Tile surface | `#2A241C` | Utility tiles |
| Text primary | `#F3EEE4` | Warm cream text |
| Text muted | `#A29A8C` | Warm taupe |
| **Accent** | `#E7A44E` | **Themeable** (Honey default) |

The accent is owner-selectable in Settings — **Honey**, **Slate** (`#7FA6C9`), **Sage** (`#93B98C`), or
**Violet** (`#B79BE0`) — and recolors the mascot eyes, chips, glows, and the Add tile live.

## Type

System families only — no bundled fonts:

- Greeting — `sans-serif`, 21sp
- Featured title — `sans-serif-medium`, 18sp
- Labels — `sans-serif`, 13sp
- Status line — `monospace`, 12sp (e.g. `roost · docked & charging · 87%`, with real battery)

## The app icon

A little **house with a glowing "resident" dot** — home + presence in one adaptive `VectorDrawable`,
monochrome-friendly and vendor-neutral.

## A cohesive device

Roost draws its own opaque dark home surface, but the OS wallpaper still shows through **Recents** and
**app-transition animations**. The **Match wallpaper to Roost** action paints the same dock gradient as the
wallpaper, so the whole device reads as one warm-dark habitat. It's applied once on first run and
re-appliable from Settings.

## Provenance

Roost's look came from a design handoff. The original brief lives in the repo at
[`docs/DESIGN-BRIEF.md`](https://gitea.stump.rocks/joestump/roost-android-launcher/src/branch/main/docs/DESIGN-BRIEF.md).
