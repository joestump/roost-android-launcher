# Design handoff — "Claude Home": a dedicated device for an AI agent

> Paste this into a design-focused Claude ("Claude Design") session. It's self-contained —
> the designer won't have the build conversation, so everything needed is here.

## The ask (one line)
Give a minimal Android home-screen launcher a **lightweight but distinctive visual identity**, themed
around: *this phone isn't a person's phone — it's my AI agent's own dedicated device.* Its own identity,
its own Claude account, its own little world. Think **a portable "robot home"** — the place the agent
lives when it's docked. Cozy, characterful, unmistakably the agent's — but still clean and fast.

## What it is (technical reality — design within this)
- An Android **launcher** app ("Claude Home", package `rocks.stump.claudelauncher`) that replaces the home
  screen on a spare **Pixel 7a (Android 15)**. It boots straight into the Claude app; pressing Home returns
  to this launcher.
- Purpose: turn a spare phone into a **dedicated device for my Claude agent** — it has its own Claude login,
  its own WireGuard tunnel, its own Proton Mail / Proton Pass accounts.
- **Two modes:**
  - **Curated** — Home shows a grid: a featured **Claude** tile, a row of utility apps (WireGuard, Proton
    Mail, Proton Pass), and an "Apps & settings" link.
  - **Appliance** — Home shows just Claude, front and center; a **long-press** reveals the utility grid.
- A **Settings** screen (mode toggle, boot auto-launch, keep-screen-on, favorites app-picker).

## Hard implementation constraints (the design MUST be cheap to build)
This is a deliberately tiny, **dependency-free** app: pure Android **framework**, no AndroidX, no Jetpack
Compose, no Material Components. Views are built **programmatically** in Kotlin. `minSdk 26`. So the visual
language must be achievable with framework primitives:
- Colors, solid fills, **simple gradients** (`GradientDrawable`), rounded-rect shapes, light elevation.
- **VectorDrawable** XML for icons/marks.
- **System fonts** (`sans-serif`, `sans-serif-light`, `sans-serif-medium`, `sans-serif-condensed`). If you
  want a custom typeface, call it out explicitly (one bundled font file max) — but prefer system.
- No animation framework; at most simple `ValueAnimator`/property tweaks. Assume mostly **static**.
- **Flag anything** that needs a raster asset, a bundled font, or a library, so it's a conscious choice.

## Current state (starting point, not sacred)
- Background: near-black `#141414`.
- Claude brand orange: `#D97757` (currently the app's own icon — an 8-spoke sunburst on the orange).
- **Home:** vertically-centered featured "Claude" tile (96dp icon + 20sp label) → a 3-column grid of app
  tiles (56dp icons + 13sp labels) → a muted "Apps & settings" text link.
- **Appliance lock screen:** centered Claude tile + hint line "Long-press anywhere for apps & settings".
- Text colors in use: `#F2F2F2` (primary), `#9A9A9A` (muted).

## What I'd love from you
1. **Visual identity / mood.** A small palette rooted in Claude's warm clay/orange world (add warm neutrals
   and maybe a calm "ambient/docked" accent), a type treatment, and a spacing/corner-radius rhythm. Warmth
   and a hint of personality **without clutter**.
2. **The agent's identity on screen — this is the heart of it.** How does the device *feel like the agent's
   home*? Options to pick/refine: a **name** for the device or agent; a small **avatar/mark** that's more
   than the raw Claude logo; a **greeting / status line** ("<agent> is home", "docked & charging", clock +
   date); an **idle/ambient** face; an "awake / working" indicator. Make it feel **inhabited**.
3. **The two surfaces:**
   - **Curated home** — tile/card treatment; how the featured Claude presence and the agent's identity
     coexist; the utility grid.
   - **Appliance / "at rest"** — the agent's ambient home face. Prime real estate for personality — think a
     robot's idle dock screen.
4. **The launcher's own app icon** — currently the sunburst; consider a "home for the agent" spin.
5. **Settings** — a light styling pass so it matches.
6. **States worth designing:** docked/charging vs on-battery; screen-on idle; "just booted / waking up".

## Deliverables
- A concise **visual spec**: palette (hex), type scale, spacing + corner-radius tokens, component treatments.
- **1–3 mockups** of the home + appliance screens (and the icon) — light-touch, enough to build from.
- **Implementation notes** mapping each choice to a framework primitive (color int, `GradientDrawable`,
  `VectorDrawable`, system font weight), flagging anything that needs a bundled asset.

## Tone
Warm, a little playful, clearly *my agent's own gadget* — a robot that has a home. Not corporate, not a heavy
themed skin. **Fast, calm, characterful.**

## Repo (read the current code/assets)
Gitea: https://gitea.stump.rocks/joestump/claude-android-launcher
- `app/src/main/java/rocks/stump/claudelauncher/MainActivity.kt` — renders the home + appliance surfaces.
- `app/src/main/java/rocks/stump/claudelauncher/SettingsActivity.kt` — settings + favorites picker.
- `app/src/main/res/` — current colors, strings, and the adaptive launcher icon.
