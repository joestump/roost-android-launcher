# Design handoff — Roost: Agent Presence & Ambient Status

> Paste this into a fresh, design-focused Claude session. It's **self-contained** — the designer has none
> of the build history, so everything needed is here. Companion to `docs/DESIGN-BRIEF.md` (Roost's overall
> look) and `docs/SETTINGS-DESIGN-BRIEF.md`. This brief is one of a set of feature briefs (A: HTTP Action
> buttons, **B: this one — Agent Presence & Ambient Status**, C: Voice, D: Two-way Agent I/O). It **owns the
> mascot state machine** — briefs **C and D** reuse the vocabulary defined here (C for its voice states, D for
> its I/O-presence cues). Brief A owns the HTTP-Action primitive that B's routing-preset buttons consume.

## The ask (one line)
Roost's home screen already has a header — a robot mascot, a greeting, a monospace status line, a VPN chip,
a bandwidth heartbeat. Turn that header into a **calm, glanceable "ambient status zone"** that makes the
agent's live state readable **from across a dark room, nightstand-style** — which model is routing right
now, what the agent is doing, and whether the stack behind it is healthy — **without turning the home
screen into a dashboard**. The whole design hinges on one line of hierarchy: what is **always visible**
versus what is **one tap away**.

## What Roost is (context)
A tiny, **vendor-neutral** Android launcher that turns a spare phone into a **dedicated device for one AI
agent**. It boots into the owner's agent app and keeps a curated set of apps, web apps, and action buttons
one tap away. Warm-dark "portable robot home" identity; a little LED-eyed robot **mascot**; **one themeable
accent**. Package `rocks.stump.roost`. See `docs/DESIGN-BRIEF.md` for the full identity.

## Why this feature matters on a dedicated-agent phone (the "a laptop can't do this" angle)
The phone sits in a dock on a desk or nightstand and **is** the agent — its own login, its own VPN, its own
accounts. Because it's a fixed physical object rather than a window you open, it can do the one thing a
laptop tab can't: **be ambiently legible**. You should be able to walk past it and, without touching it or
leaning in, know:
- **Is the agent working right now?** (the mascot's eyes tell you from 10 feet away)
- **What is it working on?** ("Grooming Gitea issues · 4m")
- **Which model is it routing to?** — and did it **silently fall back** to something cheaper/dumber than you
  intended? (the whole point of a live model chip: catch the silent downgrade)
- **Is the stack behind it up?** (LiteLLM, GPUs, Gitea, Outline, the Signal/iMessage bridges)
- **What is today costing?** (tokens + USD, at a glance)

A dedicated device earns the right to show this **continuously and quietly**. The design problem is doing
it **without clutter** — an agent's home should feel inhabited and calm, not like a Grafana panel bolted to
a phone.

## The home surface you are extending (what exists today)
`MainActivity.renderHome()` builds a vertical `LinearLayout` inside a `ScrollView` inside a `FrameLayout`
whose background is `Roost.dockBackground` (a warm radial glow). The children, top to bottom, are:

1. **`mascot(128dp)`** — a `MascotView` (Canvas-drawn LED-eyed robot; see below).
2. **greeting** — `TextView`, 21sp, `Roost.TEXT`, centered: "**\<name\> is home**" / "your agent is home".
3. **status line** — `TextView`, 12sp, **monospace**, `Roost.MUTED`, centered:
   "`roku · docked & charging · 82%`". Updates live from a battery `BroadcastReceiver`.
4. **VPN chip** — `vpnChip()` → a monospace `TextView` pill, only shown when WireGuard is installed. Tappable
   to toggle the tunnel; when up it shows live rates: "`vpn up  ↓1.2M ↑48K`". Styled by `applyVpnChip()`
   with `Roost.soft(accent)` fill + accent hairline when up, `Roost.TILE` + `Roost.HAIRLINE` when off.
5. spacer → **utility grid** (3-col app tiles) → **action-pill row** (`FlowLayout`) → weighted spacer →
   "Apps & settings" link.

Behind the scroll, pinned to the **bottom edge**, is a **`BandwidthView`** — two auto-scaling accent
sparklines (down brighter, up dimmer) drawn at low alpha, polling `TrafficStats` once a second.

**The "state zone" this brief designs = items 1–4 (mascot + greeting + status + chip row), plus new
elements that layer in alongside them.** You are extending the header, not rebuilding the home screen. The
utility grid, action pills, and "Apps & settings" link below are out of scope (other briefs / already
built) — but your additions must sit **above** the grid and not push it off the first screenful.

There is a **live-polling precedent** already in this file you should design around: a
`Handler(Looper.getMainLooper())` posts a `rateTick` every **1000ms** that reads traffic counters and calls
`refreshVpnChip()`. It **starts in `onResume`, stops in `onPause`.** Every new poll you introduce follows
this exact shape (and the same lifecycle discipline — nothing polls while the screen is off).

## What to design — four components

### (a) MODEL CHIP + routing-preset buttons
A **live route indicator** that sits **in the chip row with the VPN chip** (same pill language), showing the
agent's **current model** — e.g. `claude-opus-4-8`, `gemini-2.5-flash`, `local/qwen`. Driven by a small
polled JSON read from the owner's LiteLLM proxy (an OpenAI-compatible gateway; think a lightweight GET that
returns "what model is the default route bound to right now").

The chip is **color-coded by provider/cost tier** so a **silent fallback is visible across the room**: if
you set the agent to "Max" but the chip is wearing the "cheap" color, you notice without reading the text.
Propose the tier→color mapping (see palette below — I lean toward: **frontier = accent**, **balanced =
Sage**, **cheap = muted**, **local = Slate**, so the ramp reads even when the theme accent is re-tinted).

Paired with the chip: **routing-preset buttons** — **Cheap / Balanced / Max / Local-only** — that each apply
a LiteLLM preset in **one call** and then let the chip confirm the new route. **These buttons are instances
of the HTTP-Action primitive defined in brief A (HTTP Action buttons)** — an async POST with inline
success/failure feedback. **Cross-reference brief A; do not redesign the action primitive here.** Your job
is: where do the four presets live (an inline segmented control under the chip? a small sheet the chip opens?
part of the action-pill row?), and how the **current** preset is shown as selected. The mechanics of "POST,
show pending, show ✓/✗" belong to A.

### (b) THE MASCOT STATE MACHINE — **this brief owns it**
Today `MascotView` has a **single boolean `awake`**: false = small eye-glow, true = bigger eye-glow. That's
the whole state model. **Replace it with a real visual state machine** — the **emotional core** of Roost,
the thing that makes the device "visibly come alive."

Define a **shared state vocabulary** (state → eye **color** · **animation** · **brightness**), because
**briefs C (Voice) and D (Agent I/O) reuse it** — C for its listening/thinking/speaking states, D for its
artifact-arrived / decision-pending presence cues. Design at least:

- **idle** — the agent is home but not working: dim eyes, a slow, occasional **blink**, resting glow. Accent-colored.
- **working** — actively doing a task: **bright, gently pulsing** eyes (a slow "breathing" glow). Accent-colored.
- **stalled / waiting** — a task is blocked or waiting on input, or a tool call is running long: an **amber**
  shift with a slower, heavier pulse. **This color overrides the theme accent** (see note).
- **error** — the last task failed / the agent is unreachable: a **muted red**, steady or a double-blink.
  Overrides accent.
- **asleep / offline** — no agent reachable, screen idle: very dim, **desaturated/grey**, no glow.
- **hooks for brief C (define the slots, C fills the exact motion):** **listening** (steady wide eyes + a
  soft outer ring), **thinking** (a distinct pulse cadence vs "working"), **speaking** (pulse that can be
  driven by TTS amplitude — **flag:** this needs a live amplitude feed from the Voice brief's TTS service).
- **attention / notify cue (for brief D):** a brief, transient **"you've got something"** pulse that layers
  *over* the current base state — e.g. eyes brighten when a new artifact lands in the outbox, and a distinct
  antenna/eye cue while a decision gate is pending. Define it as a short **overlay**, not a base state, so
  brief D triggers it on I/O events rather than driving the mascot directly.

**Opinionated call to make explicit:** idle/working/listening/thinking should wear the **themeable accent**
(the robot's eyes match the owner's theme). But **stalled and error use fixed semantic colors** (amber, red)
that **override the accent** — "something is wrong" must read regardless of whether the owner themed Roost
blue. State this rule in your spec.

The mascot lives in **two places at two sizes**: `mascot(128dp)` on curated home, `mascot(150dp)` on the
**appliance "at rest"** ambient face (`renderAmbient()` — a near-empty dark screen with just the mascot +
greeting + status, revealed by long-press). The **ambient face is prime nightstand real estate** — design
how each state looks **there**, where the mascot is the only thing on screen and legibility across a dark
room matters most.

### (c) AGENT STATUS BEACON
A **single dock status line** — the mid-distance signal between "the mascot's mood" and "the detail grid" —
driven by a small **polled status JSON** (from the owner's agent box / Switchboard, a webhook→todo→agent
queue). It renders the **current task**: title + phase + elapsed. Examples:

- `⚙ Grooming Gitea issues · 4m`
- `⧗ Waiting on approval · 12m`
- `✓ Idle · last ran 26m ago`

It should read as a **natural sibling of the existing monospace status line** (item 3 above) — same warm,
technical, quiet voice — not a second competing headline. Decide: is the beacon a **second line under** the
battery status, a **replacement** for it when a task is active, or a subtle **band** above the chip row?
Design its **empty/idle** state (no active task) so it degrades gracefully to calm rather than looking
broken. The **phase glyph** and elapsed timer are the two live bits; keep them legible at nightstand
distance. The mascot state (b) should **track** the beacon (task running → mascot **working**; task blocked
→ **stalled**; last task failed → **error**) — call out that coupling.

### (d) STACK HEALTH + SPEND GRID
The **up-close, on-tap** detail layer: a compact **row of colored-dot tiles**, one per service, from a
**single aggregated poll** (one JSON the agent box assembles from LiteLLM `/health`, GPU util/VRAM, Gitea,
Outline, and the Signal/iMessage bridges). Each tile = a service name + a **status dot** (green up / amber
degraded / red down / grey unknown). Plus a **spend readout**: **today's token throughput** and **USD
spend**, with a **sparkline** (reuse the `BandwidthView` sparkline language — same ring-buffer, low-alpha,
auto-scaling Canvas treatment).

This is the densest element, so it is the strongest candidate for **"on tap, not always visible"** — e.g.
collapsed to a single summary chip ("`stack ok · $3.42 today`") that expands to the full grid, or a detail
Activity the beacon opens. **You decide** where the always-visible/on-tap line falls; that decision is the
spine of this whole brief. Design both the **collapsed** and **expanded** forms, and the states: all-healthy,
one-service-degraded, everything-down (agent box unreachable → the whole grid greys out), and
no-data-yet (first poll pending).

## The unifying problem (read this twice)
You are layering **four** new live signals onto a header that already carries **four** (mascot, greeting,
battery status, VPN chip) — on a **phone-width** surface, above a grid that must stay reachable. If every
signal is always on, the home screen becomes noise. The deliverable is a **hierarchy of glance distances**:

- **Across the room (ambient, always on):** the **mascot's eye state** — color + motion. One signal, zero reading.
- **At the dock, a glance (always on, calm):** the **greeting**, the **battery status line**, the **beacon**
  (current task + elapsed), and the **chip row** (VPN + model chip).
- **On tap / lean-in (detail):** routing presets, the **stack health grid**, the **spend sparkline**,
  per-service status.

Your job is to place every element on this ladder, make the always-on tier read as **one quiet system**
(not four widgets), and make the on-tap tier feel like a natural expansion of it — all in Roost's warm-dark,
one-accent language.

## Full inventory — everything that must be reachable
**Mascot states:** idle, working, stalled/waiting, error, asleep/offline, + hooks for listening/thinking/
speaking (brief C), + an **attention/notify overlay cue** (brief D: artifact-arrived, decision-pending). Two
sizes (128dp curated, 150dp appliance/ambient). Accent-following for normal states; amber/red **override**
for stalled/error.

**Model chip:** current model name; provider/cost **tier color**; a **stale/unknown** state (poll failed);
tap target. **Routing presets:** Cheap, Balanced, Max, Local-only; the **currently-selected** preset shown;
the apply-in-one-call action (mechanics per brief A); pending + ✓/✗ feedback.

**Agent status beacon:** phase glyph, task title, elapsed timer; states = **active task**, **waiting/blocked**,
**idle (last-ran)**, **no-data/offline**. Coupling to mascot state.

**Stack health + spend:** per-service dot tiles (LiteLLM, GPU, Gitea, Outline, Signal bridge, iMessage
bridge — extensible list); today's **token throughput**; today's **USD spend**; **spend sparkline**;
collapsed summary + expanded grid; states = healthy / degraded / down / no-data.

**Cross-cutting:** where each element sits in the always-on vs on-tap ladder; how it all looks on the
**appliance/ambient** face vs **curated** home; every poll's **loading** and **error/stale** state (a phone
on a nightstand will lose Wi-Fi — nothing should look broken when a fetch fails, it should look **quietly
stale**).

## Hard constraints (the design MUST be buildable this way)
Framework-only per **ADR-0001** — **pure Android framework: no AndroidX, no Jetpack Compose, no Material
Components, no third-party libraries.** Views built **programmatically** in Kotlin. `minSdk 26`. Enforced by
an empty `dependencies {}` block. So stay inside these primitives:

- **The mascot = `Canvas`.** `MascotView` is already a `View` with a hand-rolled `onDraw` (rounded-rect head,
  `RadialGradient` glow eyes, antenna). Its states are **Canvas draws + a `ValueAnimator`** for pulse/blink —
  that's the only animation you get (no animation framework, no Lottie, no bundled assets). Pulsing =
  animate the glow radius/alpha; blinking = animate eye height/alpha. **Flag:** continuous animation must
  **pause when the screen is off / the Activity isn't resumed** (precedent: the 1s rate poll starts in
  `onResume`, stops in `onPause`) — call this out so it's built battery-safe.
- **Chips, tiles, dots, sparklines = `GradientDrawable` rounded rects + `Canvas`.** The VPN chip, action
  pills, and app tiles are all `Roost.rounded(...)` pills/cards; the bandwidth heartbeat is a Canvas
  sparkline. Your model chip, status dots, and spend sparkline reuse exactly these.
- **Icons/glyphs = `VectorDrawable` or `Canvas`.** There's a hand-rolled SVG-path renderer (`SvgPath`) + an
  `IconStore`, and existing vectors (`ic_scene`, `ic_web`, `ic_plus`). Phase glyphs (⚙ ⧗ ✓) can be Unicode
  in a `TextView` **or** small vectors — your call, but **no bundled icon fonts**.
- **Networking = `HttpURLConnection` + `org.json`, off the main thread.** The precedent is `Hass.kt`
  (Bearer-token REST client, 8s timeouts) invoked via `Thread { runCatching { … } ; runOnUiThread { … } }`
  in `MainActivity.invokeAction`. **All four of your polls** (model, beacon, health/spend, and the
  preset-apply POST) use this. **Polling cadence via `Handler(Looper.getMainLooper())`**, mirroring the 1s
  `rateTick` — but slower (a model/beacon/health poll every ~10–30s is plenty; **propose the cadences**, and
  keep them lifecycle-bound).
- **Persistence = `SharedPreferences` via `Prefs.kt`.** New config (LiteLLM base URL + key, the status/health
  JSON endpoints, which services to show) lands as new `Prefs` keys, following the existing typed-accessor
  pattern. The **Settings** screens to enter these are governed by `docs/SETTINGS-DESIGN-BRIEF.md` — you
  just need to **name the settings** your feature requires so they can be slotted there.
- **Reuse the Roost palette EXACTLY** (verified in `Roost.kt`):
  - dock `#14110D` (`DOCK`) → radial top `#1D1912` (`DOCK_TOP`), panel/cards `#1C1813` (`PANEL`), tile
    surface `#2A241C` (`TILE`), primary text **warm cream** `#F3EEE4` (`TEXT`), muted **warm taupe** `#A29A8C`
    (`MUTED`), hairline `0x14FFFFFF` (~8% white, `HAIRLINE`).
  - **themeable accent** — default **Honey `#E7A44E`**; owner can pick **Slate `#7FA6C9`**, **Sage `#93B98C`**,
    **Violet `#B79BE0`**.
  - Helpers you'll reference: `Roost.dp(ctx, v)`, `Roost.rounded(color, radiusPx, strokeColor, strokePx)`,
    `Roost.soft(accent)` (accent at ~16% alpha — used for chip fills/selected states/soft glows),
    `Roost.withAlpha(color, alpha)`, `Roost.dockBackground(ctx)`, `Roost.medium()` (sans-serif-medium),
    `Roost.applyWallpaper(ctx)`.
  - **Type today:** title ~21–22sp `Roost.medium()`; section headers **uppercase accent ~12sp**; the status
    line is **monospace** 12sp. Chips are monospace 11sp. Stay in this scale.
- **Semantic status colors (a small, principled addition you should propose):** the health dots, tier colors,
  and stalled/error mascot states need colors **independent of the themeable accent** (so "down" reads red
  even when the accent is red-ish, and "healthy" reads green even when the accent is green-ish Sage). Propose
  a tiny fixed ramp that lives comfortably in the warm-dark world — e.g. **healthy** `#93B98C` (reuses Sage),
  **degraded/amber** a warm amber distinct from Honey (~`#D98F3C`), **down** a muted warm clay-red
  (~`#CF6B5A`), **unknown/grey** = `MUTED`. Refine these; give exact hex ints; note that they are **fixed**,
  not themeable.
- Everything must feel like the **same warm-dark place** as the rest of Roost — the ambient status zone is
  the agent's living room, not an ops console.

## What I'd love from you
1. **The glance-distance ladder.** Explicitly assign every element to **across-the-room (mascot)**,
   **at-a-glance always-on**, or **on-tap detail** — and show the resulting header composition. This is the
   central decision; lead with it.
2. **The mascot state system** — the deliverable this brief exists for. A **state → eye color · animation ·
   brightness** table (idle / working / stalled / error / asleep + listening/thinking/speaking hooks), the
   **accent-follows vs semantic-override** rule, and how each state looks at **both** sizes (128dp curated,
   150dp ambient). Specify the `ValueAnimator` motions concretely enough to build (what property, what range,
   what period) and note the screen-off pause.
3. **The chip row.** How the **model chip** and **VPN chip** coexist (two chips centered? a wrapping row?),
   the **tier-color** mapping, the **stale/unknown** treatment, and where the **routing presets** live
   (inline segmented control? a small sheet? merged into the action row?) — deferring the POST mechanics to
   brief A but showing the **selected-preset** and **applying/confirmed** states.
4. **The beacon.** Its relationship to the existing monospace battery line (second line? replacement?
   band?), the phase-glyph + title + elapsed layout, its **idle/empty** and **offline** states, and the
   **beacon→mascot coupling**.
5. **The health + spend layer.** The **collapsed summary** and **expanded grid**, the per-service dot tiles,
   the token/USD readout, the **spend sparkline** (in `BandwidthView`'s language), and its
   healthy/degraded/down/no-data states — plus whether "expanded" is inline or a detail Activity.
6. **A reusable component set**, each mapped to a framework primitive: **status chip** (`Roost.rounded` pill),
   **status dot** (Canvas circle / small `GradientDrawable`), **service tile** (`Roost.rounded(TILE, …)`),
   **segmented preset control** (a row of `Roost.rounded` pills, one selected), **beacon line** (styled
   `TextView`), **metric + sparkline** (Canvas), and the **mascot** (`Canvas` + `ValueAnimator`).
7. **Every state, drawn:** loading/first-poll, success, **stale** (fetch failed but keep last-known, dimmed),
   error, empty/idle, offline (agent box unreachable). A nightstand device fails a fetch constantly — make
   failure look **calm**, never broken.
8. **The settings you require.** Just name them (LiteLLM base URL + key, status endpoint, health endpoint,
   which services/dots to show, poll cadences, which of these to show on the ambient face) so
   `SETTINGS-DESIGN-BRIEF.md` can place them. Don't design those settings screens here.

## Deliverables
- **A hierarchy diagram / annotated home composition** showing the three glance tiers and where every new
  element sits above the existing utility grid.
- **Mockups** (name each screen/state):
  1. **Curated home — full state zone**, working state (mascot working + beacon active + model chip + VPN chip).
  2. **Curated home — idle/empty** (no active task, healthy stack, collapsed spend).
  3. **Ambient / appliance face** — the nightstand view, mascot large, in **idle**, **working**, and
     **stalled/error** states (3 small frames).
  4. **Mascot state sheet** — all states side by side at both sizes, with the color/animation spec.
  5. **Model chip + routing presets** — chip in each tier color; presets with selected + applying + ✓/✗.
  6. **Stack health + spend** — collapsed summary chip **and** expanded grid; plus degraded, all-down, and
     no-data variants.
  7. **Failure/stale states** — how the chip, beacon, and grid look when their polls fail.
- **A component spec:** for each control — the **row/pill/tile/dot/sparkline/mascot** — give the corner-radius
  and spacing **tokens** (in dp, matching the existing rhythm: tiles use radius 16dp, chips ~20dp, tile
  surface 66dp, tile width 78dp, 3-col space-between grid), the **type scale** (sizes + weights: cream
  `#F3EEE4` medium/regular, muted `#A29A8C`, monospace where the existing status line uses it), and the exact
  **color int** per element — each mapped to its framework primitive (`GradientDrawable` / `Canvas` /
  `VectorDrawable` / `ValueAnimator` / system font weight).
- **The semantic color ramp** (healthy/degraded/down/unknown + tier colors) as fixed hex ints, with a note
  that they're non-themeable.
- **Implementation notes:** which polls run (and cadence), that they're `Handler`-driven and lifecycle-bound
  like the existing 1s rate poll, that network is `HttpURLConnection` + `org.json` off-main-thread per
  `Hass.kt`, that the mascot animation pauses when not resumed, and **an explicit FLAG list** for anything
  that reaches beyond framework-only (e.g. the **TTS-amplitude feed** for the speaking state, or any
  endpoint the agent box must expose — the aggregated status/health/spend JSON is assumed to be provided by
  Joe's stack, not built here).

## Tone
Same as Roost: **warm, calm, characterful, quiet.** The ambient status zone should feel like a small robot
**at home** — awake, breathing, occasionally blinking, glancing up when it's working — not a monitoring
dashboard. The mascot is the soul of it; the data is the murmur underneath. **Legible across a dark room,
effortless up close.**

## Repo (read the current code)
https://gitea.stump.rocks/joestump/roost-android-launcher — the exact files to read:
- `app/src/main/java/rocks/stump/roost/MainActivity.kt` — the home surface you extend: `renderHome()` /
  `renderAmbient()`, the greeting + monospace `statusLine()`, `vpnChip()` / `applyVpnChip()` / the 1s
  `rateTick` poll (your polling precedent), the `mascot()` factory, and `invokeAction()` (the async
  `Thread { … } / runOnUiThread { … }` HTTP pattern).
- `app/src/main/java/rocks/stump/roost/MascotView.kt` — the mascot you are turning from one `awake` boolean
  into a full state machine. **This is the heart of the brief.**
- `app/src/main/java/rocks/stump/roost/BandwidthView.kt` — the Canvas sparkline language to reuse for spend.
- `app/src/main/java/rocks/stump/roost/Roost.kt` — the palette ints + drawable helpers (quote these exactly).
- `app/src/main/java/rocks/stump/roost/Hass.kt` — the framework-only `HttpURLConnection` + `org.json` REST
  precedent for every poll and the preset-apply POST.
- `app/src/main/java/rocks/stump/roost/Prefs.kt` — the `SharedPreferences` typed-accessor pattern your new
  config follows.
- `app/src/main/java/rocks/stump/roost/FlowLayout.kt` — the framework-only wrapping row (for a preset/chip row).
- `app/src/main/java/rocks/stump/roost/Models.kt` — the `ActionButton(kind, key, title, a, b)` model (routing
  presets are HTTP-Action instances per **brief A**).
- `docs/adrs/ADR-0001-framework-only-zero-dependency-launcher.md` — the non-negotiable constraint.
- Companion briefs: `docs/DESIGN-BRIEF.md` (identity), `docs/SETTINGS-DESIGN-BRIEF.md` (where your settings land).
