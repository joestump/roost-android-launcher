# Design handoff — Roost's Voice Loop (the talking nightstand)

> Paste this into a design-focused Claude session. It's self-contained — the designer won't have the
> build conversation, so everything needed is here. Companion to `docs/DESIGN-BRIEF.md` (Roost's overall
> look) and `docs/SETTINGS-DESIGN-BRIEF.md` (the settings system). **Brief B — the mascot brief — owns
> the mascot's state machine and idle look; this brief *consumes* three of its voice states — LISTENING,
> THINKING (the uploading/transcribing/processing steps), and SPEAKING — and must stay visually coherent
> with it. Do not redesign the mascot here.**

## The ask (one line)
Design the **Voice Loop**: Roost's north-star experience where a docked phone becomes an ambient
**conversational appliance** — you hold to talk and it files what you said to your agent; when the agent
replies out loud, the phone speaks it back with a chime and a lit-up mascot. Make a phone feel like a
**talking nightstand you address across the room**, not an app you tap.

## What Roost is (context)
A tiny, **vendor-neutral** Android launcher that turns a spare phone into a dedicated device for an AI
agent. It boots into the owner's agent app and keeps a curated set of apps, web apps, and action buttons
one tap away. Warm-dark "portable robot home" identity, a little **LED-eyed robot mascot** (see brief B),
one themeable accent. Package `rocks.stump.roost`. See `docs/DESIGN-BRIEF.md`.

## Why this feature only makes sense on a dedicated-agent phone (the "a laptop can't do this" angle)
A laptop is a thing you sit down at, wake, unlock, focus, and type into. This phone is **parked on a dock
on the nightstand or the desk, awake, plugged in, and pointed at one agent** — nothing else competes for
it. That changes what voice can be:
- **It's always the same conversation.** There's one agent, one identity, one durable inbox (Switchboard).
  You don't pick an app or a chat thread — you just talk, and it lands.
- **It can talk back into the room.** Because the device is dedicated and docked, it can hold a foreground
  service open, subscribe to the agent's outbound channel, and **speak replies aloud** hours later without
  anyone unlocking anything. A laptop won't narrate to an empty room; a nightstand appliance should.
- **The mic is single-purpose and physical.** Hold the phone (or a hardware key), talk, release. There's
  no wake-unlock-focus-tap dance. The privacy model can be dead simple because there's exactly one thing
  the mic is ever for.
- **"Think out loud before bed."** Screen-off, in the dark, you hold and dictate a long rambling note; it
  becomes a task your agent works overnight. That's a nightstand behavior, not a computer behavior.

The whole point: **turn a spare phone into the thing you talk to.** This brief is that thing's face.

## The hard problem to solve (read this twice)
Voice is invisible, and this feature spans **an async, multi-hop, failure-prone pipeline that the user
can't see** — mic → speech-to-text → a durable queue → (later) a spoken reply from a background service.
The design job is to make all of that **legible, trustworthy, and calm** using only a robot face, a few
shapes, and color. Specifically:

1. **A summon that feels physical.** Holding to talk must feel like pressing a walkie-talkie, not opening
   a form. The recording overlay has to communicate *"the mic is live, I'm hearing you, release to send"*
   in a way that's obvious with the phone at arm's length, in a dark room, maybe with the screen off.
2. **A pipeline made of states, not spinners.** After you release, the utterance travels:
   **listening → uploading to STT → transcribing → queued in Switchboard → confirmed** (or fails at any
   hop). Each state needs its own unmistakable read, and **failures must be recoverable** (retry / it's
   saved locally), never a toast that vanishes.
3. **Ambient legibility.** These states are read from across a room, off-axis, at low brightness. Lean on
   the **mascot's eyes and glow, motion, and one accent color** — not small text — as the primary signal.
   Text is the caption, the mascot is the headline.
4. **A privacy model you can trust at a glance.** The mic is **only ever live while physically held** (or
   during an explicit long-note session the user started). There must be an **unmistakable mic-live
   indicator** and an equally unmistakable *"mic is off"* resting state, so a phone that sits in a bedroom
   is obviously not always-listening. This is the feature's credibility; design it like the headline it is.
5. **Spoken replies that respect the room.** When the agent speaks, the user needs a **"now speaking"**
   presence, the words on screen, and **replay / mute / skip** within thumb reach — and a global mute so
   the nightstand can be silenced without disabling the mic.

## Full inventory — everything that must be reachable
Grouped by concern (raw material; **you** decide the final IA and which surface each lives on):

**A. Push-to-talk summon (input)**
- A **hold-to-talk affordance on Home** — a large, unmistakable primary control (the mascot itself? a
  dedicated dock button? both?). Press-and-hold records; release sends.
- A **hardware-key summon** so you can talk without looking: long-press a volume key, or bind the Android
  **ASSIST** role, to open the recording overlay from anywhere. *(Capability-flagged below — design the
  affordance and its discovery/onboarding regardless of which mechanism ships.)*
- The **recording overlay**: a live **waveform / level meter**, a "Listening…" label, an elapsed timer,
  and a clear **release-to-send** vs **slide-to-cancel** gesture model.
- A **screen-off "long note" / think-out-loud mode**: a hands-free session you start deliberately (hold
  longer to "lock" recording, or a dedicated control) that keeps recording with the screen dark until you
  stop it — for long, rambling, close-your-eyes dictation. Needs its own on-screen-off presence.
- **Post-capture:** the transcript is filed into **Switchboard** as a task (the agent's durable to-do
  inbox). Optional: a glance at the transcript with **send / discard / re-record** before it's filed.

**B. Pipeline states (feedback)**
- **Idle / ready** (mic off — the resting nightstand face).
- **Listening** (mic live, capturing).
- **Uploading** to the STT service.
- **Transcribing** (STT working).
- **Queued** (accepted into Switchboard — the success confirmation, showing a snippet of what was heard).
- **Failure** variants: no mic permission, no network, STT error, Switchboard rejected/unreachable —
  each with a recovery path (**retry**, or **"saved — will send when back online."**).

**C. Spoken replies on the dock (output)**
- A background **foreground service** subscribes to the agent's **outbound channel** and, for messages
  flagged speakable, plays them via **TTS** preceded by a soft **chime**.
- A **"now speaking" indicator** on Home: the mascot animates through the SPEAKING state (brief B) and the
  spoken text appears as a caption/transcript.
- Playback controls within thumb reach: **replay**, **skip** (to next queued reply), and **mute**.
- A **global mute / "quiet hours"** affordance so the dock can be silenced (mic still available) — and its
  resting indicator so you can tell at a glance whether the nightstand will speak.
- A lightweight **history** of the last few spoken replies you can replay (optional; a short list, not an
  inbox).

**D. Privacy surface (trust)**
- The **mic-live indicator** — the single most important visual in this brief.
- The **at-rest "mic is off"** state — equally deliberate.
- A one-line, plain-language **privacy statement** of the model ("The mic is only on while you hold the
  button.") surfaced at the right moment (first run / permission prompt / a Settings note).

**E. North-star capstone (design now, may build later)**
- On-device **"Hey Roost" wake word** — a hands-free, no-touch summon. Design the *affordance and its
  states* (an "always-listening for the wake word" indicator that is honestly distinct from mic-capture,
  an enable/disable control, the privacy framing) even though the detection engine is capability-flagged.

## Hard constraints (the design MUST be buildable)
Framework-only per **ADR-0001** — pure Android framework, **no AndroidX, no Compose, no Material
Components, no third-party libraries**. Views built programmatically in Kotlin. `minSdk 26`. So:

- **Navigation = separate `Activity`s** (no nav component). Precedent: `SettingsActivity`, `ActionsActivity`,
  `IconPickerActivity`, `WebAppActivity`. The recording overlay is best as its own **translucent Activity**
  (or a full-bleed view swapped into `MainActivity`'s `FrameLayout`); a Voice **Settings** screen is its
  own Activity.
- **Surfaces = `GradientDrawable`** rounded rects via `Roost.rounded(fill, radiusPx, stroke, strokeW)`.
  **Custom visuals (waveform, mascot, "speaking" bars) = `Canvas`** in a `View` — exactly like the
  existing `MascotView` and `BandwidthView` (both hand-drawn on `Canvas`, no assets).
- **Animation = `Handler(Looper.getMainLooper())` + `postDelayed` polling and `invalidate()`**, or a
  simple `ValueAnimator`. Precedent: the 1 s `rateTick` in `MainActivity` and `BandwidthView`'s 1 s
  `tick`. A live waveform will tick faster (~30–60 ms) — same pattern, shorter interval. **No animation
  framework, no Lottie, no bundled motion assets.**
- **Type = system fonts only** (`sans-serif`, `Roost.medium()` = `sans-serif-medium`, `Typeface.MONOSPACE`
  for status lines). No bundled fonts.
- **Networking = `HttpURLConnection` + `org.json`**, off the main thread. Precedent: `Hass.kt`
  (`httpGet`/`httpPost`, `Bearer` auth, 8 s timeouts) and `invokeAction()` in `MainActivity`, which runs a
  network POST on a `Thread {}` and marshals the result back with `runOnUiThread {}`. STT upload and the
  Switchboard POST follow this exact shape, **authenticated per endpoint (bearer *or* HMAC — the same auth
  options brief A's HTTP-Action builder offers; Switchboard uses an HMAC-signed webhook)**; the
  outbound-channel subscription is a long-poll loop in a Service.
- **Persistence = `SharedPreferences` via `Prefs.kt`** (typed getters/setters; lists stored as JSON
  strings). New voice settings (endpoints, tokens, mute state, wake-word on/off) live here.
- **Reuse the Roost palette exactly** (from `Roost.kt`, verified):
  - dock `#14110D` (`Roost.DOCK`), radial highlight `#1D1912` (`Roost.DOCK_TOP`), panel `#1C1813`
    (`Roost.PANEL`), tile `#2A241C` (`Roost.TILE`), text `#F3EEE4` (`Roost.TEXT`), muted `#A29A8C`
    (`Roost.MUTED`), hairline `0x14FFFFFF` (`Roost.HAIRLINE`, ~8% white).
  - **Themeable accent** `#E7A44E` Honey default (`Roost.DEFAULT_ACCENT`); also Slate `#7FA6C9`, Sage
    `#93B98C`, Violet `#B79BE0` (`Roost.ACCENTS`). Read the live value via `Prefs.accent(context)`; never
    hard-code the accent.
  - Helpers you'll compose with: `Roost.dp(c, v)`, `Roost.rounded(...)`, `Roost.soft(accent)` (accent at
    ~16% — chips/glows/selected fills), `Roost.withAlpha(color, alpha)`, `Roost.dockBackground(c)` (the
    radial dock), `Roost.medium()`.
  - **The recording overlay and speaking states must feel like the same warm-dark room as Home** — a
    deeper, more focused version of the dock, not a new visual world.

**Capability flags — call these out explicitly so Joe can decide (each needs a permission, an asset, or a
platform capability beyond the framework-only baseline):**
- **`RECORD_AUDIO`** is a *dangerous runtime permission* not currently in the manifest. The first summon
  must gracefully handle the permission request and the *denied* state. Recording itself uses framework
  `MediaRecorder`/`AudioRecord` (fine); the permission is the gate. **Design the pre-permission and
  denied states.**
- **Hardware-key / hands-free summon.** A launcher can't casually intercept a volume long-press globally.
  Real options are the **ASSIST role** (register an assist activity / voice-interaction; the user sets
  Roost as the default assistant in system settings) or an **accessibility service** — both need setup and
  user consent. **Design the affordance and its onboarding; flag that the binding mechanism is a platform
  capability, not a given.**
- **Spoken-reply background service.** A **foreground service** with an ongoing notification is required to
  keep the outbound-channel subscription and TTS alive while docked. On API 34+ it needs a foreground
  **service type** (media-playback; mic type only if long-note recording runs in a service). **New
  component — flag it.** TTS itself is framework (`android.speech.tts.TextToSpeech`, uses the system TTS
  engine — no bundled voice).
- **The chime.** Prefer a **synthesized tone** (`ToneGenerator`/`AudioTrack`) to stay asset-free; if you
  want a designed sound, that's **one bundled audio asset — flag it.**
- **"Hey Roost" wake word.** On-device wake-word detection needs a wake-word engine (a library — forbidden
  by ADR-0001) or a bundled model. **Hard-flag: design the affordance and states now; the detector itself
  is out of the framework-only baseline and is a later decision.**

## What I'd love from you
1. **The summon, as an object you press.** Design the primary hold-to-talk affordance on Home and how it
   relates to the mascot (does the mascot *become* the button? is there a discrete dock button beneath it?
   both?). Show the press-and-hold interaction, the release-to-send / slide-to-cancel model, and how it
   reads at arm's length.
2. **The recording overlay.** The full-bleed "listening" surface: the **live waveform / level meter**
   (Canvas-drawable, driven by mic amplitude on a fast Handler tick), the "Listening…" label + timer, the
   cancel gesture, and the transition into the **long-note / screen-off** variant (what a dark-screen
   recording session looks like — minimal, glanceable, honest that the mic is live).
3. **The pipeline as a state set.** Give every state a distinct, ambient-legible treatment built from the
   mascot + accent + minimal text: **idle/ready, listening, uploading, transcribing, queued/confirmed,
   and each failure** (no-permission, offline/"saved for later", STT error, Switchboard error) with its
   recovery affordance. Specify what the **mascot** does in **LISTENING** (mic live) and **THINKING**
   (uploading/transcribing/processing) and how it hands off to brief B's other states — keep it coherent
   with brief B, don't redraw the robot.
4. **Spoken replies on the dock.** The **"now speaking"** presence (mascot SPEAKING state from brief B +
   the spoken text as caption), and the **replay / skip / mute** controls. Design the **global mute /
   quiet-hours** control and its at-rest indicator, and an optional short **replay history**.
5. **The privacy model, made visible.** Design the **mic-live indicator** and the **mic-off resting
   state** as first-class, unmistakable elements (note: Android 12+ also shows a system green mic dot —
   complement it, don't rely on it alone). Place the plain-language privacy line where it earns trust.
6. **A reusable component set**, each mapped to a framework primitive: the **hold-button**, the
   **waveform view** (`Canvas`), a **pipeline-status strip / pill** (the mono status line evolves —
   `Roost.rounded` + `Typeface.MONOSPACE`, like the existing VPN chip), a **recoverable-error card**
   (`GradientDrawable` panel + retry action row), the **now-speaking caption**, **transport controls**
   (replay/skip/mute as icon buttons — `VectorDrawable`/`Canvas`, no assets), and the **mic-state
   indicator**. Reuse the settings brief's row/toggle vocabulary for the Voice Settings screen.
7. **Entry points & chrome.** Where the summon lives on Home (curated *and* appliance/ambient modes), how
   the overlay enters/exits, and a **Voice** section in Settings (endpoints, tokens, chime on/off, mute,
   long-note, wake-word toggle) that matches `SettingsActivity`'s language.
8. **The "Hey Roost" affordance.** Even as a north-star: the enable control, the honestly-distinct
   "listening for the wake word" indicator (different from mic-capture), and its privacy framing.

## Deliverables
- **Mockups** (name each screen/state):
  1. **Home — idle/ready** with the hold-to-talk affordance (curated mode).
  2. **Home — appliance/ambient** with the summon (the at-rest nightstand face).
  3. **Recording overlay — listening** (waveform + "Listening…" + timer + cancel).
  4. **Recording overlay — screen-off long-note** (dark, minimal, mic-live honest).
  5. **Pipeline — uploading / transcribing** (the in-flight state).
  6. **Pipeline — queued / confirmed** (success, with the heard-snippet).
  7. **Failure — offline "saved for later"** and **Failure — mic permission needed** (two recovery states).
  8. **Now speaking** (mascot SPEAKING + caption + replay/skip/mute).
  9. **Global mute / quiet-hours** state (and the at-rest mute indicator on Home).
  10. **Privacy** — the mic-live vs mic-off indicators side by side + the privacy line.
  11. **Voice Settings** screen (matches `SettingsActivity`).
  12. **"Hey Roost"** affordance + its listening indicator (north-star).
- **A component spec**: for each control — row/button/indicator/waveform/status-pill/error-card/transport
  — give the visual treatment, **spacing + corner-radius tokens** (e.g. tiles use `dp(16)` radius, chips
  `dp(20)`; match existing usage), the **type scale** (title ~22sp `Roost.medium()`, section headers
  uppercase accent ~12sp, mono status ~11–12sp `Typeface.MONOSPACE`, captions/labels ~12.5–13sp), and the
  color ints used — **each mapped to a framework primitive** (`GradientDrawable` via `Roost.rounded`,
  `Canvas` view, `VectorDrawable`, system-font weight). Flag any element needing a bundled asset.
- **A state diagram** of the Voice Loop (idle → listening → uploading → transcribing → queued → speaking,
  plus every failure edge and its recovery) — so the build knows exactly which visual maps to which state.
- **Implementation notes**: how many Activities/services, which surfaces are Canvas views vs overlays, how
  the mic amplitude drives the waveform, and how the pipeline states are pushed to the UI from the
  `Thread {}`/`runOnUiThread {}` network calls and the foreground service.

## Tone
Same as Roost: warm, calm, characterful, clean — and here especially **trustworthy and unhurried**. This
is the feature that makes the phone feel *alive and present in the room*, so it should feel like a friendly
appliance you can rely on and glance at from bed — never a surveillance gadget, never a busy app. The
mascot is the star; text is the caption. Make holding-to-talk feel as satisfying and obvious as a
walkie-talkie, and make a spoken reply in a quiet room feel like a small, welcome moment.

## Repo (read the current code)
https://gitea.stump.rocks/joestump/roost-android-launcher
- `app/src/main/java/rocks/stump/roost/Roost.kt` — palette + drawable helpers (colors, `rounded`, `soft`,
  `dp`, `dockBackground`, `medium`). The source of every token above.
- `app/src/main/java/rocks/stump/roost/MascotView.kt` — the LED-eyed robot, hand-drawn on `Canvas`
  (`accent` + `awake` today). **Brief B extends this into the state machine you consume — read it, stay
  coherent, don't redraw it.**
- `app/src/main/java/rocks/stump/roost/MainActivity.kt` — the Home surface you extend: the greeting +
  mono `statusLine()`, the tappable VPN chip (`vpnChip()` / `applyVpnChip()` — precedent for a mono status
  pill), the `Handler` `rateTick` poll, the tile grid + `FlowLayout` action-pill row, and `invokeAction()`
  (the `Thread {}` + `runOnUiThread {}` network-POST-with-inline-result pattern the pipeline reuses).
- `app/src/main/java/rocks/stump/roost/BandwidthView.kt` — a live `Canvas` sparkline on a 1 s `Handler`
  tick; the closest existing analog to the waveform view.
- `app/src/main/java/rocks/stump/roost/Hass.kt` — `HttpURLConnection` + `org.json` REST client with
  `Bearer` auth and timeouts; the shape STT-upload and the Switchboard POST follow.
- `app/src/main/java/rocks/stump/roost/Prefs.kt` — `SharedPreferences` wrapper; where new voice settings
  live (JSON for lists).
- `app/src/main/java/rocks/stump/roost/SettingsActivity.kt` — the Settings language the Voice Settings
  screen should match (see also `docs/SETTINGS-DESIGN-BRIEF.md`).
- `app/src/main/AndroidManifest.xml` — current permissions (`INTERNET`, `ACCESS_NETWORK_STATE`; **no
  `RECORD_AUDIO`, no service, no foreground-service permissions yet** — the gaps this feature adds).
- `docs/adrs/` — architecture decisions, esp. **ADR-0001** (framework-only) and **ADR-0002/SPEC-0001**
  (pluggable providers — a model for how a "voice provider" could be structured).
