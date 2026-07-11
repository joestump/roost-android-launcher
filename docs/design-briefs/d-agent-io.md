# Design handoff — Two-way Agent I/O (Roost)

> Paste this into a design-focused Claude session. It's self-contained — the designer won't have the
> build conversation, so everything needed is here. Companion to `docs/DESIGN-BRIEF.md` (Roost's overall
> look) and `docs/SETTINGS-DESIGN-BRIEF.md` (the settings-screen redesign). This brief is specifically
> about the **I/O surface**: moving files *and decisions* between the owner's agent and the phone.

## The ask (one line)
Design the surface that lets a person **hand things to the agent** and **see what the agent hands back** —
files, photos, text, and human-gate decisions — moving **both directions in two taps**, and make it feel
like a **two-way conversation with a resident robot, not a file manager**.

## What Roost is (context)
A tiny, **vendor-neutral** Android launcher that turns a spare phone into a dedicated device for an AI
agent. It boots into the owner's agent app and keeps a curated set of apps, web apps, and action buttons
one tap away. Warm-dark "portable robot home" identity; a little LED-eyed robot **mascot** (`MascotView` —
today an `awake` eye-glow flag, extended into a full state machine by **brief B**, which this brief consumes
for I/O presence); **one themeable accent**. Package `rocks.stump.roost`. See `docs/DESIGN-BRIEF.md`.

## Why this matters on a dedicated-agent phone (the "a laptop can't do this" angle)
The agent's `$HOME` on its box is **Syncthing-mirrored to this phone** — an `~/inbox` and `~/outbox`
folder that sync silently both ways. That turns the spare phone into a **physical dropbox and mailbox for
the agent**:

- **Share-to-agent** — From *any* app on the phone (Photos, a browser, a PDF viewer, a chat), the owner
  hits the system **share sheet** and picks "Roost" — the file lands in the agent's `~/inbox` seconds
  later, no cloud round-trip, no copy-paste, no "email it to myself." A laptop makes you find a folder;
  the phone makes it a two-tap reflex you already do a hundred times a day.
- **Artifact spotlight** — The agent works overnight (drafts, renders, logs, a finished report). Those
  land in `~/outbox`. The **docked phone on the desk becomes the agent's out-tray** — glanceable from
  across a dark room, legible **without unlocking anything or opening the agent's chat app**.
- **Approve / Deny** — When the agent hits a human gate ("OK to send this email?" / "merge this PR?"), the
  phone **buzzes with a notification** carrying **Approve** and **Deny** actions. The owner taps once from
  the lock screen and the decision **POSTs straight back** to the agent's queue. The dedicated device is
  always on, always docked, always the fastest path to a yes/no — a laptop is asleep in a bag.

This is the payoff of a device that is *the agent's*, always docked and always synced: I/O becomes ambient
and physical instead of a chat transcript you have to go read.

## The hard problem to solve (the design's real challenge)
Three things that are genuinely difficult and are the heart of this brief:

1. **Make async, offline-ish sync feel confident.** Nothing here is a synchronous request. "Share to
   agent" only writes a file to a synced folder — the agent may pick it up in 2 seconds or 2 minutes, and
   Syncthing may be mid-sync or offline. The UI must confirm **"dropped for `<agent>`"** honestly (the
   handoff succeeded locally) without lying that the agent has *seen* it. Same on the way back: an artifact
   appearing in `~/outbox` means "synced to the phone," not "the agent is done." Design the vocabulary and
   states for **queued / synced / seen** without overclaiming.
2. **Ambient legibility across a dark room.** The outbox spotlight and the approve/deny prompt must be
   readable on a docked phone at arm's length in a dim room — big enough type, enough contrast against the
   warm-dark dock, a badge you can count at a glance. This is a *glance* surface first, a *tap* surface
   second.
3. **A framework-only previewer.** Overnight work must be legible **on the device** — an image, a text
   file, a markdown draft, a log tail — **without opening the agent's app**, and with **no rendering
   library** (see constraints). Making markdown and logs look good with only `Canvas`, `TextView`, and
   `WebView` is the craft challenge.

## Full inventory — everything that must be reachable

**(a) Share-to-Agent inbox**
- Roost appears as a target in **every app's system share sheet** (single item **and** multi-select).
- Accepts: an image/photo, an arbitrary file, a URL, or a text snippet.
- A **confirming surface** after the share — the "dropped for `<agent>`" moment.
- **Disambiguation, kept minimal:** which destination folder if more than one inbox exists (e.g.
  `~/inbox`, `~/inbox/urgent`), and an **optional one-line note** to attach alongside the file (so the
  owner can say *"summarize this"* with the drop). Design for the **zero-friction default** (one tap =
  dropped) with the note/folder as an *optional* expand, never a required form.
- The **empty/degraded case:** Syncthing folder not yet configured / not writable — a clear "point Roost
  at your synced inbox folder" state.

**(b) Agent Outbox + artifact spotlight**
- A **dock tile for the Outbox** in the home grid, **badged with an unseen count** (new artifacts since
  the owner last looked).
- A **home-screen "latest artifact" card**: thumbnail (or type glyph) + filename + **relative time**
  ("2h ago") + a one-line hint of what it is. The newest thing the agent produced, surfaced without a tap.
- An **Outbox list screen**: reverse-chronological artifacts, each a row with thumbnail/glyph, name, time,
  size, and **seen/unseen** state.
- A **framework-only previewer** for a single artifact, handling at least: **image** (jpg/png/webp),
  **plain text**, **markdown** (`.md`), and **logs** (`.log`/`.txt` tails — possibly long, possibly
  monospace). Include per-type chrome: for an image, fit-to-screen; for a long log, a jump-to-end.
- **Artifact actions** from the preview: mark seen, open-with (hand off to a real app via `ACTION_VIEW`),
  and *"send back to inbox with a note"* (close the loop — reply to an artifact).
- **Empty states:** outbox folder empty ("nothing from `<agent>` yet"); folder unconfigured.

**(c) Approve / Deny (human gates)**
- A **framework notification** with two inline actions — **Approve** and **Deny** — surfaced the moment a
  gate request arrives, tappable from lock screen / shade.
- An **in-app decision card** (on home and/or an inbox-of-decisions screen): **what's being asked**, the
  **context** (which task, a short body, maybe a diff/preview snippet), the **two buttons**, and a
  **POST-back confirmation** (brief A's `pending → success ✓ / error ↻`).
- **A queue when several gates are pending** — more than one decision waiting shouldn't collapse into one
  notification you can't tell apart. Design the stacked/queued case.
- **States:** **pending**, **deciding** (= A's `pending`, POST in flight), **approved** / **denied** (= A's
  `success`, two glyphs), **POST failed** (= A's `error`) **→ retry**, and the decision-only **`expired`**
  (the agent moved on / the gate timed out).
- **Cross-reference:** this pairs with **brief A** — both surfaces POST back over the same mechanism, so the
  decision control **is** an instance of A's HTTP-Action primitive. Use **brief A's canonical POST-state
  vocabulary verbatim** — `idle → pending → success → error (+ timeout)` — so the two-button control and its
  feedback read as **one component** across both briefs. Decisions add exactly two states on top: **`queued`**
  (accepted into the durable queue — the same success semantics as A's Fire-a-Task) and **`expired`** (the
  gate closed before you answered). Approve and Deny are two flavors of the same `pending → success`
  transition (different glyph: ✓ approved vs ⊘ denied), not new states.

## Hard constraints (the design MUST be buildable)
Framework-only per **ADR-0001** — pure Android framework, **no AndroidX, no Compose, no Material
Components, no third-party libraries**. Views built programmatically in Kotlin. `minSdk 26`. So:

- **Navigation = separate `Activity`s** (no nav component). The Outbox list, the artifact previewer, and a
  decisions inbox are each their own Activity — precedent: `SettingsActivity`, `ActionsActivity`,
  `IconPickerActivity`, `WebAppActivity`. The **share target** is its own lightweight, **transparent-ish**
  Activity that shows the confirm sheet and finishes.
- **Lists = programmatic** `LinearLayout` inside a `ScrollView` (no RecyclerView). A long outbox needs
  reverse-chron **sectioning** ("Today / Yesterday / Earlier") and lazy thumbnail decode, not an infinite
  wall of rows.
- **Surfaces = `GradientDrawable`** rounded rects via `Roost.rounded(fill, radiusPx, strokeColor,
  strokePx)`. **Icons = `VectorDrawable` / `Canvas`** (there's a hand-rolled SVG-path renderer,
  `SvgPath`, plus `IconStore`). **Type = system fonts only** (`sans-serif`, `Roost.medium()` for
  `-medium`, `Typeface.MONOSPACE` for status/log lines). **No bundled assets or fonts.**
- **Networking (the POST-back) = `HttpURLConnection` + `org.json`**, run **off the main thread** — exact
  precedent is `Hass.httpPost` (Bearer token, `doOutput`, `Thread{ … }.start()` with `runOnUiThread` for
  the result; see `Hass.kt` + `invokeAction` in `MainActivity`). **Persistence = `SharedPreferences`** via
  the typed `Prefs` wrapper (JSON arrays for lists, e.g. `webApps`, `hassAccounts`, `actionButtons`).
- **Reuse the Roost palette** (in `Roost.kt`, verified): dock `#14110D` (radial → `#1D1912` via
  `Roost.dockBackground`), panel `#1C1813`, tile `#2A241C`, text `#F3EEE4`, muted `#A29A8C`, `HAIRLINE`
  `0x14FFFFFF` (~8% white), **themeable accent** `#E7A44E` (Honey default; also Slate `#7FA6C9`, Sage
  `#93B98C`, Violet `#B79BE0`). Helper `Roost.soft(accent)` = accent @ ~16% for chips/selected/glows.
  Current type on home: greeting ~21sp, tile/label ~12.5sp, a **monospace** status line ~12sp, action
  pills 13sp with `Roost.rounded(Roost.soft(accent), 20dp, accent, 1dp)`.
- Every I/O surface must feel like the **same warm-dark place** as home — the outbox card and the decision
  card should look like siblings of the existing featured tile and the action pills.

**Flag anything that needs more than the above.** Known platform touchpoints you should call out (not
solve, but design around):
- **Notifications with actions** use framework `NotificationManager` + `PendingIntent` + a
  `BroadcastReceiver` — all framework, fine. **Android 13+ requires the `POST_NOTIFICATIONS` runtime
  permission** — design a **first-run permission ask / denied fallback** (if notifications are off, the
  decision must still be reachable via the in-app card + a home badge).
- **Ambient folder-watching + lock-screen decision delivery need a framework foreground `Service` — flag it.**
  The whole always-on premise above (a notification that **buzzes the moment a gate arrives**, tappable
  from the lock screen; the **unseen-outbox badge** and **latest-artifact card** kept current while docked)
  assumes something in Roost is actually watching. **`FileObserver`** is the right watch primitive, but it
  only fires while the launcher process holds a **live in-process observer** — the moment Roost is
  backgrounded or reaped (screen off, or **appliance mode** where the phone boots straight into the agent
  app and Roost isn't foreground), no observer fires and **no decision notification is delivered**. So the
  watch must run **inside a framework foreground `Service`** (`FOREGROUND_SERVICE` permission + a persistent
  ongoing notification), with `FileObserver` as the Service's watch mechanism — **not** an in-Activity one
  that dies with the screen. This is a **new capability beyond today's Activities** — the app has no Service
  yet — so design around it: the persistent "Roost is watching for `<agent>`" ongoing notification is itself
  an ambient surface (keep it warm-dark and calm, not a nagging system chip), and it's what makes the
  buzz-from-lock-screen, live-badge, live-card behaviour real rather than only-while-you're-looking.
- **Reaching the Syncthing folder itself is the big one to flag.** Syncthing writes to a user-chosen
  directory in shared storage; a launcher can't assume raw `~/inbox` path access under scoped storage.
  Design a **one-time "point Roost at your inbox / outbox folder" setup** (this maps to the Storage Access
  Framework folder-picker, `ACTION_OPEN_DOCUMENT_TREE`, granting a persistable tree URI — or a
  `MANAGE_EXTERNAL_STORAGE` prompt). **Assume the folders are configured for the happy path, but design
  the unconfigured empty state and the setup entry point.**
- **Markdown rendering has no library — you must pick an approach and flag it.** Two framework-only routes:
  (1) a tiny hand-rolled **markdown→HTML** string loaded into a `WebView` via `loadData` with an inline
  `<style>` in the Roost palette (best fidelity: headings, lists, code blocks, images), or (2) a
  **`Spannable`** built by hand rendered in a `TextView` (lighter, but limited). **Recommend one** and
  note what it can/can't render. Images/logs are easier: image = `BitmapFactory` decode + downscale into
  an `ImageView` (precedent: `IconStore.drawableFor`/`cacheRaster`); log = monospace `TextView` in a
  `ScrollView`.
- **Thumbnails:** images decode + downscale via `BitmapFactory`; **non-image artifacts get a Canvas-drawn
  type glyph card** (a filename extension chip — `MD`, `LOG`, `PDF`, `TXT` — on a `Roost.TILE` rounded
  rect). No third-party thumbnailing.
- **Relative time** ("2h ago") = framework `android.text.format.DateUtils.getRelativeTimeSpanString` —
  fine, no library.

## What I'd love from you
1. **An I/O information architecture.** Where do these three things live relative to the existing home
   (mascot → greeting → mono status → VPN chip → **tile grid** → **action-pill row** → "Apps & settings")?
   Propose: which pieces are **ambient on home** (the latest-artifact card, an unseen-outbox badge, a
   pending-decision card) vs. **behind a tile** (the full Outbox list, the decisions inbox, the previewer).
   Don't bloat the calm home — decide what earns a permanent spot and what is one tap away.
2. **The "dropped for `<agent>`" moment.** Design the post-share confirm — a **toast-weight** success by
   default, expandable to the optional note + folder pick. Make it feel like handing something to a robot
   that nods, not a file-copy dialog. Show the **default (one-tap)** and the **expanded (note + folder)**
   variants, plus the **share of multiple items** case.
3. **The artifact spotlight.** Design the **home latest-artifact card** and the **badged Outbox tile**,
   then the **Outbox list** (sectioned, reverse-chron, seen/unseen) and the **previewer** for image / text
   / markdown / log. This is the ambient-legibility centerpiece — make the newest thing the agent made
   feel *present* on the docked phone.
4. **The decision surface.** Design the **notification** (Approve/Deny actions, what the collapsed line
   says), the **in-app decision card** (ask + context + two buttons + POST-back confirmation), and the
   **queued/multiple-pending** case. Nail the **`pending → success ✓ → error ↻`** micro-states (brief A's
   canonical names; `queued` and `expired` are the decision-only additions) — this is the part that must
   feel trustworthy.
5. **A reusable component set**, each mapped to a framework primitive: an **artifact row** (thumbnail/glyph
   + name + time + seen dot), a **badge** (unseen count on a tile — `Canvas` overlay or a small
   `Roost.rounded` pill), a **decision card**, the **two-button decide control** (Approve = accent-filled,
   Deny = hairline/muted), a **POST-state chip** (brief A's `pending / success / error`, plus decision-only
   `queued` / `expired`, echoing the VPN chip's `Roost.soft(accent)` treatment), a **note-attach input**
   (single line, inline-commit, matching
   the settings text-field language), and the **confirm sheet**. Reuse existing motifs: the **action pill**
   (`Roost.soft(accent)` fill + accent hairline), the **tile** (`Roost.TILE`, 16dp radius, hairline or
   accent ring), and the **mono status line**.
6. **Use the mascot as presence — via brief B, not the raw `awake` flag.** Brief B replaces the single
   `awake` boolean with a full mascot state machine and defines a transient **attention/notify** cue for
   exactly this brief. Specify how I/O events **trigger B's cue/states** — eyes brighten when an artifact
   just arrived, a subtle antenna/eye cue while a decision is pending — so the *robot itself* signals
   "you've got something" using B's vocabulary, not a parallel animation. Keep it cheap (`Canvas`, at most a
   `ValueAnimator`).
7. **Honest state vocabulary.** Give me the words and visual states for **queued / synced / seen** (inbox
   and outbox) and, for decisions, **brief A's `pending / success / error`** plus the decision-only
   **`queued` / `expired`**, so nothing overclaims what the sync or the agent has actually done.

## Deliverables
- **Mockups** (name each screen/state):
  1. **Home with I/O ambient** — the existing home plus the latest-artifact card and the badged Outbox
     tile; show it with **and** without a pending-decision card.
  2. **Share confirm — default** ("dropped for `<agent>`", one tap).
  3. **Share confirm — expanded** (optional note + destination-folder pick).
  4. **Share — multiple items** (N files dropped at once).
  5. **Outbox list** (sectioned Today/Yesterday/Earlier, seen/unseen rows, empty state).
  6. **Artifact previewer — image**, **— markdown**, and **— log** (three variants; show the type chrome).
  7. **Decision notification** (collapsed + expanded with Approve/Deny actions).
  8. **In-app decision card** — pending, then the **`pending → success ✓ → error ↻`** states (brief A's vocabulary).
  9. **Decisions queue** (2–3 pending gates stacked).
  10. **Unconfigured / empty states** — "point Roost at your inbox & outbox folder" and empty outbox.
- **A component spec** — for each control (artifact row, unseen badge, decision card, two-button decide
  control, POST-state chip, note input, confirm sheet, type-glyph thumbnail): the **row/control anatomy**,
  **spacing + corner-radius tokens** (reuse the home's rhythm: tile radius 16dp, pill/chip radius 20dp,
  hairline 1dp, ~6–10dp inner padding), the **type scale** (map to `sans-serif`, `Roost.medium()`,
  `Typeface.MONOSPACE` and the sizes above), and each mapped to a **framework primitive** (color int,
  `Roost.rounded(...)` `GradientDrawable`, `VectorDrawable`/`Canvas`, system font weight). Flag anything
  needing a bundled asset, a library, or a platform capability beyond the list above.
- **Implementation notes** — how many Activities (share target, Outbox list, previewer, decisions inbox),
  which pieces ride on `FileObserver`, how the POST-back reuses the `Hass.httpPost` off-main-thread
  pattern, and your **recommended markdown approach** (WebView-HTML vs. Spannable) with its tradeoffs.

## Tone
Same as Roost: warm, calm, characterful, clean. The I/O surface should feel like **passing notes back and
forth with a helpful little robot that lives on your desk** — a conversation, not a sync client. Confident
and glanceable in a dark room; never a developer file-dump.

## Repo (read the current code)
https://gitea.stump.rocks/joestump/roost-android-launcher
- `app/src/main/java/rocks/stump/roost/Roost.kt` — palette ints + drawable helpers (`rounded`, `soft`,
  `dp`, `dockBackground`, `medium`, `withAlpha`).
- `app/src/main/java/rocks/stump/roost/MainActivity.kt` — the home surface you extend: the tile grid
  (`utilityGrid`, `tile`), the `FlowLayout` action-pill row (`actionRow`, `actionPill`), the VPN chip
  (`vpnChip`/`applyVpnChip`) as a POST-state precedent, `tileMenu` long-press, and `MascotView` usage.
- `app/src/main/java/rocks/stump/roost/MascotView.kt` — the LED-eyed robot + the `awake` glow flag
  (**brief B extends this into the state machine this brief consumes for I/O presence**).
- `app/src/main/java/rocks/stump/roost/WebAppActivity.kt` — the fullscreen `WebView` you'd reuse for the
  markdown previewer.
- `app/src/main/java/rocks/stump/roost/IconStore.kt` — raster/SVG decode + cache + `drawableFor`; the
  pattern for thumbnails.
- `app/src/main/java/rocks/stump/roost/Hass.kt` — the `HttpURLConnection` + `org.json` **POST** the
  decision POST-back copies.
- `app/src/main/java/rocks/stump/roost/Prefs.kt` — `SharedPreferences` state (JSON-array list patterns:
  `webApps`, `hassAccounts`, `actionButtons`, `hiddenItems`, `iconOverrides`).
- `app/src/main/java/rocks/stump/roost/ActionsActivity.kt` — precedent for a config/detail Activity's
  layout language.
- `app/src/main/AndroidManifest.xml` — how Activities/receivers are declared; you'll add an
  `ACTION_SEND` / `ACTION_SEND_MULTIPLE` `intent-filter` to the share-target Activity.
- `docs/adrs/` — architecture decisions (esp. **ADR-0001**, the framework-only constraint) and
  `docs/SETTINGS-DESIGN-BRIEF.md` for the settings component language your inputs should match.
