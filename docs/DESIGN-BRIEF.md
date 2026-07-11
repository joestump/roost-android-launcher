# Design handoff — a dedicated-device launcher for an AI agent

> Working name: **"Agent Home"** (placeholder — naming is open). Paste this into a design-focused
> Claude session. It's self-contained — the designer won't have the build conversation, so everything
> needed is here.

## The ask (one line)
Give a minimal Android home-screen launcher a **lightweight but distinctive visual identity**, themed
around: *this phone isn't a person's phone — it's an AI agent's own dedicated device.* Its own identity,
its own account, its own little world. Think **a portable "robot home"** — the place the agent lives when
it's docked. Cozy, characterful, unmistakably the agent's — but still clean and fast.

## Vendor-neutral by design (important)
This will likely be **released publicly**, and the owner's "agent app" might be Claude, ChatGPT, Gemini,
Perplexity, a local/self-hosted LLM app — anything. So:
- Don't tie the identity to any single vendor's brand or proprietary logo/marks (also avoids trademark
  issues on release).
- The launcher renders the **featured app's own icon at runtime** (pulled from the installed app), so the
  agent tile already carries the model's branding for free. The launcher's **own** identity (background,
  its app icon, the "home" framing, any greeting/mascot) must therefore **stand on its own** and read as
  *"an AI agent's home,"* not *"Claude's home."*
- Ideally the palette is a **neutral, warm, brandable base** — optionally themeable/accentable so an owner
  could tint it toward their model, but great-looking with no tinting at all.

## What it is (design within this)
- An Android **launcher** app that replaces the home screen on a spare phone (reference device: Pixel 7a,
  Android 15). It **boots straight into the owner's agent app**; pressing Home returns to this launcher.
- Purpose: turn a spare phone into a **dedicated device for one AI agent** — with its own login, its own VPN
  tunnel, its own mail/password accounts, etc.
- **Two modes:**
  - **Curated** — Home shows a grid: a **featured "agent" tile**, a row of utility apps (e.g. a VPN client,
    a mail app, a password manager — whatever the owner pins), and an "Apps & settings" link.
  - **Appliance** — Home shows just the agent app, front and center; a **long-press** reveals the utility grid.
- A **Settings** screen (mode toggle, boot auto-launch, keep-screen-on, favorites app-picker, and which app
  is the "featured agent app").

## Hard implementation constraints (the design MUST be cheap to build)
Deliberately tiny, **dependency-free** app: pure Android **framework**, no AndroidX, no Jetpack Compose, no
Material Components. Views are built **programmatically** in Kotlin. `minSdk 26`. The visual language must be
achievable with framework primitives:
- Colors, solid fills, **simple gradients** (`GradientDrawable`), rounded-rect shapes, light elevation.
- **VectorDrawable** XML for icons/marks.
- **System fonts** (`sans-serif`, `-light`, `-medium`, `-condensed`). Prefer system; if a custom typeface is
  truly needed, flag it (one bundled font file max).
- No animation framework; at most simple `ValueAnimator` tweaks. Assume mostly **static**.
- **Flag anything** that needs a raster asset, a bundled font, or a library.

## Current state (reference build — not sacred, and intentionally Claude-flavored as a placeholder)
- Background: near-black `#141414`.
- Placeholder accent: `#D97757` (Claude clay-orange) — treat as a **stand-in** for a neutral/brandable accent.
- **Home:** centered featured agent tile (96dp icon + 20sp label) → 3-column grid of app tiles (56dp icons +
  13sp labels) → muted "Apps & settings" link.
- **Appliance screen:** centered agent tile + hint line "Long-press anywhere for apps & settings".
- Text: `#F2F2F2` primary, `#9A9A9A` muted.

## What I'd love from you
1. **Visual identity / mood.** A small, **vendor-neutral** palette (warm and calm; propose a base + an
   optional accent that could be re-tinted per owner), a type treatment, and a spacing/corner-radius rhythm.
   Personality without clutter.
2. **The agent's identity on screen — the heart of it.** How does the device *feel like the agent's home*
   without leaning on any model's logo? Pick/refine: a neutral **mascot/mark** or "presence" motif; a
   **greeting / status line** ("your agent is home", "docked & charging", clock + date); an **idle/ambient**
   face; an "awake / working" indicator. Make it feel **inhabited** — a robot with a home.
3. **The two surfaces:** curated home (tile treatment; how the featured-agent tile — which shows the
   installed app's own icon — coexists with the launcher's neutral identity; the utility grid) and the
   appliance **"at rest"** face (a robot's idle dock screen — prime real estate for personality).
4. **The launcher's own app icon** — a neutral "an agent's home" mark (must not resemble any specific
   vendor's logo).
5. **Settings** — a light styling pass to match.
6. **States worth designing:** docked/charging vs on-battery; screen-on idle; "just booted / waking up".

## Deliverables
- A concise **visual spec**: palette (hex, with any accent marked as themeable), type scale, spacing +
  corner-radius tokens, component treatments.
- **1–3 mockups** (home + appliance + icon), light-touch, enough to build from. If helpful, show the featured
  tile with a generic placeholder agent icon so it's clearly model-agnostic.
- **Implementation notes** mapping each choice to a framework primitive (color int, `GradientDrawable`,
  `VectorDrawable`, system font weight); flag anything needing a bundled asset.

## Tone
Warm, a little playful, clearly *an AI agent's own gadget* — a robot that has a home. Not corporate, not a
heavy themed skin, not tied to one brand. **Fast, calm, characterful.**

## Repo (read the current code/assets)
Reference implementation (currently Claude-flavored): https://gitea.stump.rocks/joestump/roost-android-launcher
- `app/src/main/java/rocks/stump/claudelauncher/MainActivity.kt` — home + appliance surfaces; note the
  featured/utility tiles load each installed app's real icon at runtime.
- `app/src/main/java/rocks/stump/claudelauncher/SettingsActivity.kt` — settings + favorites picker.
- `app/src/main/res/` — current colors, strings, and the adaptive launcher icon.
