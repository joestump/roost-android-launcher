# Design handoff — the Generic HTTP-Action Tile (Roost's universal trigger primitive)

> Paste this into a design-focused Claude session. It's self-contained — the designer won't have the
> build conversation, so everything needed is here. Companion to `docs/DESIGN-BRIEF.md` (Roost's overall
> look) and `docs/SETTINGS-DESIGN-BRIEF.md` (the settings language). This brief is about the single most
> important new control in Roost: a home tile that, when tapped, **fires an HTTP request** — the primitive
> every other "do a thing" feature will ride on.

## The ask (one line)
Design the **Generic HTTP-Action Tile** end-to-end: how a "does a thing" control looks and gives **live
success/failure feedback** on the home surface; the **framework-only config builder** that defines the
request (method, URL, auth, JSON body with variable substitution, test-fire); and the **Fire-a-Task**
preset flow that enqueues a durable agent job — all while keeping **secrets out of every visible label**.

## What Roost is (context)
A tiny, **vendor-neutral** Android launcher that turns a spare phone into a dedicated device for an AI
agent. It boots into the owner's agent app and keeps a curated set of apps, web apps, and action buttons
one tap away. Warm-dark "portable robot home" identity, a little LED-eyed robot mascot (`MascotView`), one
themeable accent (Honey default). Package `rocks.stump.roost`. See `docs/DESIGN-BRIEF.md`.

## Why this feature matters on a dedicated-agent phone (the "a laptop can't do this" angle)
Roost is the physical dashboard for an always-docked agent device sitting on a shelf. The owner walks by,
taps once, and something happens in their infrastructure: a scene fires, a webhook lands, a durable job is
enqueued for the agent to pick up **whether or not a live agent session is running right now**. That last
part is the magic a laptop can't match — the phone is an ambient, glanceable, physical **trigger surface**
that outlives any terminal session. Today Roost can already fire a Home Assistant scene (one hardcoded
provider). But the owner's world is a fleet of HTTP endpoints — Switchboard (a durable webhook→todo→agent
queue), Home Assistant, msgbrowse, LiteLLM, MCP servers, Gitea, Home Assistant, a dozen self-hosted
dashboards. **Every one of them is "POST some JSON to a URL."** So instead of writing a bespoke provider per
service, Roost needs **one general HTTP-action primitive** that subsumes the HASS-specific one and lets the
owner wire up any endpoint from the phone. This is Joe's favorite feature and the flagship of the whole
action-button system: get this right and "Fire-a-Task," "call this webhook," and future providers are all
just saved instances of it.

## The hard part — the UX/visual problem to solve
**An HTTP request is asynchronous and can fail, and the feedback has to live on a tile the size of a
thumbnail.** A launch tile is fire-and-forget: tap Gmail, Gmail opens, done — no result to show. An HTTP
action is different: the moment between tap and outcome is real (a slow VPN, a 500, a bad token, an
unreachable host), and the owner is standing three feet away glancing at a shelf. So the tile must express,
**inline and legibly across a dark room**, a small state machine:

- **idle** → **pending** (the request is in flight; must be unmistakable and not look frozen)
- pending → **success** (it worked; a calm, brief confirmation that decays back to idle)
- pending → **error** (it failed; a *persistent-enough* signal the owner won't miss, with a way to see why)

Today the code does the cheapest possible version of this: `invokeAction()` runs the HASS call on a
`Thread` and pops a `Toast` ("✓ Kitchen" or "Action failed"). A Toast is wrong for the flagship: it's
off-tile, it vanishes, it doesn't survive a glance-away, and it can't show "in flight." **Your central job
is to design the on-tile firing feedback** — the pending/success/error states rendered *on the control
itself* — plus the builder and preset flows around it. This is the crux; spend your best thinking here.

Secondary hard parts, each called out below: (2) where these tiles physically live on a home surface that
already has an app grid **and** a pill row; (3) a request **builder** that a non-developer can drive but a
power user trusts, built with zero form libraries; (4) making **secrets visible-by-absence** — tokens must
never appear in a label, a test-fire echo, or a screenshot.

## Full inventory — everything that must be reachable

### (a) The Action Tile on the home surface
- A tile/pill that reads as **"does a thing," not "opens an app"** — visually distinct from both the app
  grid tiles and (design decision for you) either replacing or unifying the current action-pill row.
- **Firing feedback states rendered inline:** idle, pending (in-flight), success (transient), error
  (sticky until acknowledged). Include a **timeout** appearance (request exceeded ~8s).
- An **error affordance**: tapping/expanding a failed tile shows *why* (status code + short message) without
  leaving home. Errors must be dismissible and re-fireable.
- Icon + short label (label wraps to 2 lines today; pills don't truncate — see `actionPill`).
- Long-press menu parity with every other tile: Hide, Delete/Remove, **Change icon**, Reset icon (this is
  the shared `tileMenu` contract — see below).
- Placement decision: **grid tile, pill row, or a new zone?** (Your call — argue it. See "hierarchy.")

### (b) The Config Builder (a framework-only Activity: `HttpActionActivity`, new — precedent `ActionsActivity`)
Define one request:
- **Method**: GET / POST / PUT / DELETE / PATCH (segmented control).
- **URL**: single-line, `https://` default for bare hosts (precedent: `normalizeUrl()` in `ActionsActivity`).
- **Auth**: None / **Bearer token** / **HMAC-signed** (shared-secret signature over the body — this is how a
  Switchboard webhook is authenticated). The token/secret is entered here and **stored in Prefs**, never
  shown in the label or the request preview.
- **Headers**: zero or more `key: value` rows (add/remove). Auth injects its own header; user headers are
  extra.
- **Body**: a **JSON body template** with **variable substitution** — `{{battery}}`, `{{timestamp}}`,
  `{{agent}}`, `{{device}}`, and free-form `{{prompt}}`-style values the owner names. The builder must show
  which variables are available and let the owner insert them.
- **Test fire**: a button that sends the *real* request and shows the *real* response (status + body,
  truncated) inline — **with secrets redacted** in whatever the builder echoes back.
- **Save** as a named action → becomes a tile. **Title** + **icon** (icon reuses `IconPickerActivity`, the
  existing 3-source remote picker — see below).
- **Migration**: existing **Home Assistant scene** buttons must fold into this general model. Show how a HASS
  scene (`POST {base}/api/services/scene/turn_on`, `Bearer {token}`, body `{"entity_id":"scene.x"}`) is
  *exactly* one saved HTTP action — and how the HASS account/scene picker becomes one **authoring path** on
  top of the generic builder, not a separate world.

### (c) Fire-a-Task — saved presets that enqueue durable agent work
- Presets that **POST an HMAC-signed webhook to Switchboard** to enqueue a durable todo (e.g. "groom PRs,"
  "sync dotfiles," "run the monitor") that **survives with no live agent session** and is claimed later.
- Two authoring paths for the same underlying HTTP action:
  1. **"Pick from my endpoints"** — a curated, friendly list (Switchboard task templates, known services)
     where the owner fills in a couple of fields and everything else (URL, HMAC secret, body shape) is
     pre-wired. This is the path most tiles get created through.
  2. **"Raw request"** — drop straight into the full builder (b) for anything not templated.
- A fired task should read as **"queued," not "done"** — the success state semantics differ from a scene
  (scene = it happened; task = it's been accepted into the queue). Reflect that in the success copy/glyph.

### (d) Security & privacy model (make it *visible*)
- **Secrets never in the visible label.** A tile's title is "Groom PRs," never a token or signed URL.
- **Token/secret entry**: masked input; stored in `SharedPreferences` via `Prefs` (same store as the HASS
  long-lived token today). Show how the field communicates "saved, hidden" after entry (dots, "•••• last4,"
  a "Replace" action) rather than re-displaying the value.
- **Test-fire must not leak**: the response echo, any request preview, and error details must **redact**
  `Authorization`, HMAC signatures, and any header/secret the builder knows is sensitive.
- **HMAC vs Bearer, explained in-line**: the builder should make the choice legible to a non-cryptographer
  (one line each: "Bearer = a secret token sent as-is"; "HMAC = sign the body with a shared secret; the
  secret never leaves the phone"). No jargon walls.

## Hard constraints (the design MUST be buildable)
Framework-only per **ADR-0001** — pure Android framework: **no AndroidX, no Compose, no Material Components,
no third-party libraries.** Views built programmatically in Kotlin. `minSdk 26`. So:

- **Navigation = separate `Activity`s.** The builder is its own Activity (`HttpActionActivity`, new;
  precedent: `SettingsActivity`, `ActionsActivity`, `IconPickerActivity`). No nav component, no fragments.
- **Lists = programmatic** `LinearLayout` inside a `ScrollView` — **no RecyclerView.** The endpoint picker,
  header rows, and any variable list are hand-built rows. Long lists need search/section/picker patterns,
  not a wall of rows.
- **Surfaces = `GradientDrawable`** rounded rects via `Roost.rounded(fill, radiusPx, strokeColor, strokePx)`.
  Selected/soft states via `Roost.soft(accent)` (accent at ~16% alpha). Radial dock background via
  `Roost.dockBackground()`.
- **Icons = `VectorDrawable`** (drawables like `R.drawable.ic_scene`, `ic_web`, `ic_plus`) **or `Canvas`**.
  There is a hand-rolled SVG-path renderer (`SvgPath`) and an `IconStore` + remote `IconPickerActivity`
  (sources: selfh.st, Simple Icons, Heroicons) for user-chosen icons. **No bundled raster assets/fonts.**
- **Type = system fonts only.** `Roost.medium()` = `sans-serif-medium`; `Typeface.MONOSPACE` for the status
  line and the VPN chip. Current scale: title ~22sp medium, section headers **uppercase accent ~12sp**
  (`letterSpacing = 0.08f`), body ~14sp, tile label ~12.5sp, mono status ~12sp.
- **Networking = `HttpURLConnection` + `org.json`** on a background `Thread`, results marshaled back with
  `runOnUiThread` (precedent: `Hass.activateScene` off-thread + the `invokeAction` `Thread{}` in
  `MainActivity`). **Polling / animation via `Handler(Looper.getMainLooper())`** (precedent: the 1s
  `TrafficStats` rate poll `rateTick` in `MainActivity`). Timeouts are 8s (see `Hass.open`).
- **Persistence = `SharedPreferences` via `Prefs`**, each collection a single JSON string, tolerant of
  malformed entries (precedent: `actionButtons`, `hassAccounts`). Buttons already persist through
  `ActionButton(kind, key, title, a, b)` + `Prefs.setActionEnabled`.
- Reuse the **Roost palette** exactly (from `Roost.kt`): dock `#14110D` (radial → `#1D1912`), panel
  `#1C1813`, tile `#2A241C`, text `#F3EEE4` (warm cream), muted `#A29A8C` (warm taupe), HAIRLINE `0x14FFFFFF`
  (~8% white), themeable **accent `#E7A44E`** (Honey; also Slate `#7FA6C9`, Sage `#93B98C`, Violet
  `#B79BE0`). Everything should feel like the **same warm-dark place** as home and Settings.

**FLAG anything that needs more than this** — a bundled asset, a library, or a platform capability beyond
`HttpURLConnection`/`Handler`/`Canvas`/`SharedPreferences`. In particular: **HMAC signing** needs a crypto
primitive. `javax.crypto.Mac` (HmacSHA256) ships in the platform and is framework-legal, so note it as
"platform crypto, no dependency" — but call out explicitly that this is the one place we reach past plain
`HttpURLConnection`, so Joe can confirm. If your design implies anything else exotic (a background service
for retries, a notification, a JSON-path response assertion engine), name it as an explicit ask.

## How this extends the existing code (so the designer designs something buildable)
The model is already general enough to grow into; you're extending, not inventing:

- **`ActionButton(kind, key, title, a, b)`** (`Models.kt`) — the uniform button value type. `kind` is an
  `ActionKind` enum (today `SHORTCUT`, `HASS_SCENE`). Adding HTTP = a new `ActionKind.HTTP` (per **ADR-0002**,
  a new provider = new kind + scanner/invoker + one picker section + icon/invoke dispatch — **no changes to
  persistence or the home render loop**). The two string args `a`/`b` are too thin for a full request, so
  flag that HTTP actions likely need their **definition stored in its own Prefs collection** (like
  `hassAccounts`) keyed by id, with the `ActionButton` holding a reference — mirror the HASS pattern where
  `HASS_SCENE` uses `a = HassAccount.id`, `b = scene entity_id`.
- **`invokeAction(b)`** (`MainActivity.kt`, "Threading and error handling") — the dispatch site. The HASS
  branch spawns a `Thread`, calls `Hass.activateScene`, and `runOnUiThread` pops a `Toast`. **Your on-tile
  feedback states replace that Toast** for HTTP actions (and could upgrade HASS too, since HASS becomes an
  HTTP action).
- **`Hass`** (`Hass.kt`) — the concrete precedent for the generic client: `open()` builds an
  `HttpURLConnection` with `Authorization: Bearer …`, `Content-Type`/`Accept: application/json`, 8s
  timeouts; `httpPost` writes a JSON body. A **general `HttpAction` client** generalizes exactly this
  (method, arbitrary headers, HMAC option, body template).
- **`actionRow()` / `actionPill()` / `FlowLayout`** (`MainActivity.kt`, `FlowLayout.kt`) — the current pills:
  a wrapping `FlowLayout` of soft-accent rounded pills (`Roost.rounded(Roost.soft(accent), 20dp, accent,
  1dp)`), icon + label, `hGap/vGap = 10dp`, top pad 18dp, shown only when ≥1 button is enabled. This is your
  starting canvas for the tile treatment.
- **`utilityGrid()` / `tile()`** (`MainActivity.kt`) — the 3-column app grid: 66dp rounded-16dp `TILE`
  surfaces, hairline stroke (accent ring for the featured agent, soft-accent for the "Add" tile), 12.5sp
  muted label under each, laid out by hand with weighted spacers (space-between). If you argue action tiles
  belong in the grid, this is the tile they'd match.
- **`tileMenu(anchor, key, onDelete)`** (`MainActivity.kt`) — the shared long-press `PopupMenu`: Hide /
  Delete / Change icon / Reset icon. Every home item honors it via a stable string `key` (e.g. `app:pkg`,
  `web:url`, `hass:acct:eid`). HTTP actions get a `key` like `http:<id>` and inherit this for free.
- **`IconPickerActivity` + `IconStore` + `Prefs.iconOverride`** — the existing 3-source remote icon picker.
  Reuse it verbatim for choosing an action's icon; don't design a new one.
- **`Prefs`** (`Prefs.kt`) — where everything is stored. `actionButtons`/`setActionButtons` (JSON, tolerant),
  `hassAccounts` (JSON, secret token in the clear in private prefs), `iconOverride`, `isHidden`. HTTP action
  definitions + their secrets live here the same way.
- **SPEC-0001** (`docs/openspec/action-buttons/spec.md`) — the governing spec: uniform pluggable model,
  provider-agnostic rendering, off-main-thread networking, **failures surfaced not swallowed**, tolerant
  persistence, "no buttons → no row." Your design must satisfy these; the "Threading and error handling"
  requirement is exactly the pending/success/error problem you're solving on-tile.

## What I'd love from you
1. **The firing-feedback state machine, on-tile.** The centerpiece. Design idle → pending → success → error
   (+ timeout) *rendered on the control*, legible from across a room, animated only with what a
   `Handler`-driven `invalidate()` loop can do on a `Canvas`/`GradientDrawable` (think: a pulsing accent
   ring, a sweeping border, a spinner drawn in `onDraw`, a soft-accent fill that breathes). Specify: how
   long success lingers before decaying to idle; how error persists until acknowledged; how the owner
   re-fires or opens "why it failed"; how a double-tap during "pending" is prevented. Give exact colors,
   glyphs, and timings.
2. **Placement & hierarchy.** Decide where HTTP-action tiles live — extend the pill row, join the app grid,
   or a **dedicated "Actions" zone** — and justify it against the current layout (mascot → greeting → mono
   status → VPN chip → app grid → pill row → "Apps & settings"). Fire-a-Task tiles may deserve their own
   visual weight; say so. Keep "no actions → no zone" (SPEC-0001).
3. **The builder IA & forms.** A calm, non-developer-hostile `HttpActionActivity`: method segmented control,
   URL field, an **auth section** (None/Bearer/HMAC) that swaps its fields, add/remove header rows, a JSON
   body editor with a **variable inserter**, and a **Test-fire** result panel. Replace raw `EditText`+Save
   dumps with the settings-brief component language (grouped cards, inline commit, consistent inputs). Show
   the empty, editing, testing, test-success, and test-error states of the builder.
4. **The two authoring paths for Fire-a-Task.** Design "**Pick from my endpoints**" (a friendly, sectioned
   picker of pre-wired templates — Switchboard tasks, known services — where the owner fills a field or two)
   vs "**Raw request**" (the full builder). Show the picker screen and how a template pre-fills the builder.
5. **The HASS migration.** One diagram/flow: today's HASS account + scene picker → the same thing expressed
   as saved HTTP actions. Show that the HASS scene picker survives as a **convenience authoring path** on top
   of the generic model, and existing enabled scene buttons keep working (or migrate cleanly).
6. **The privacy model, made visible.** How tokens/secrets are entered (masked), confirmed-saved without
   re-display ("•••• last4" / "Replace"), and **redacted** in test-fire echoes and error details. A one-line
   in-context explanation of Bearer vs HMAC.
7. **A reusable component set**, each mapped to a framework primitive: **action tile** (idle/pending/
   success/error/timeout), **firing spinner/ring** (`Canvas` in a `View` subclass, `Handler` tick),
   **segmented control** (method + auth type), **labeled text input**, **masked secret input**, **key/value
   header row (add/remove)**, **JSON body field + variable chip inserter**, **test-fire result panel**
   (status pill + truncated body), **endpoint-template card**, **section header**, **primary/secondary
   button**. Give the color int / `GradientDrawable` / `VectorDrawable` / font weight for each.
8. **States for everything.** Empty (no actions; no endpoints configured yet), pending, success, error,
   timeout, "secret saved," "test not yet run," malformed-JSON-in-body, and offline/no-network.

## Deliverables
- **Mockups** (name each screen/state):
  - **Home — action tiles at rest** (idle, sitting among the app grid / pill row, per your placement call).
  - **Home — tile pending** (in-flight animation captured as a frame + a note on the motion).
  - **Home — tile success** (transient confirm) and **Home — tile error** (sticky) + **error expanded**
    (status + short reason, redacted, with re-fire/dismiss).
  - **Fire-a-Task tile** in success = "queued" state (distinct from scene "done").
  - **Builder — empty/new action** (method, URL, auth=None).
  - **Builder — Bearer auth** and **Builder — HMAC auth** (fields swapped; secret masked/saved).
  - **Builder — body + variable inserter** (JSON template with `{{…}}` chips).
  - **Builder — Test-fire success** and **Builder — Test-fire error** (both with secrets redacted).
  - **Fire-a-Task — "Pick from my endpoints"** picker + **template pre-filling the builder**.
  - **HASS migration** flow (before → after as saved HTTP actions).
  - **The component set** on one board.
- **A component spec**: for every control above — row/control type, **spacing + corner-radius tokens** (the
  app uses 16dp tile corners, 20dp pill corners, ~6–24dp vertical rhythm; propose the full scale), **type
  scale**, and **each state's exact color** — each mapped to a framework primitive (color int,
  `Roost.rounded(...)`, `VectorDrawable`, `Roost.medium()`/`MONOSPACE`). Flag anything needing a bundled
  asset, a library, or a platform capability beyond the constraints (call out `javax.crypto.Mac` for HMAC).
- **Implementation notes**: how many Activities (I expect **one new** — `HttpActionActivity` — plus reuse of
  `IconPickerActivity`); how the on-tile animation is driven framework-only (`Canvas` subclass +
  `Handler`); how the request definition + secret persist in `Prefs`; how `ActionKind.HTTP` slots into
  `invokeAction`/icon dispatch per **ADR-0002** with no changes to the render loop or persistence layer.

## Tone
Same as Roost: warm, calm, characterful, clean. The tile should feel like a **satisfying physical button** —
tap it and the little robot's world visibly *does something* — not a developer's REST console bolted onto a
home screen. The builder should feel like the same gorgeous, effortless place as the redesigned Settings:
confident, quiet, never a form dump. Firing feedback should be legible and reassuring from across a dim
room, never anxious.

## Repo (read the current code)
https://gitea.stump.rocks/joestump/roost-android-launcher
- `app/src/main/java/rocks/stump/roost/Roost.kt` — palette + `rounded`/`soft`/`dockBackground`/`medium` helpers.
- `app/src/main/java/rocks/stump/roost/MainActivity.kt` — the home surface: `actionRow`/`actionPill`,
  `invokeAction` (the async fire + Toast you're replacing), `utilityGrid`/`tile`, `tileMenu`, the `rateTick`
  `Handler` poll, `applyVpnChip`.
- `app/src/main/java/rocks/stump/roost/Hass.kt` — the concrete `HttpURLConnection` client to generalize.
- `app/src/main/java/rocks/stump/roost/ActionsActivity.kt` — today's action-button management screen (HASS
  accounts + scene picker + shortcut scanner) that the builder/endpoint-picker evolves from.
- `app/src/main/java/rocks/stump/roost/Models.kt` — `ActionButton` + `ActionKind` + `HassAccount`.
- `app/src/main/java/rocks/stump/roost/Prefs.kt` — persistence + secret storage patterns.
- `app/src/main/java/rocks/stump/roost/FlowLayout.kt` — the framework-only wrapping pill container.
- `app/src/main/java/rocks/stump/roost/IconPickerActivity.kt` — the remote icon picker to reuse.
- `docs/openspec/action-buttons/spec.md` + `docs/openspec/action-buttons/design.md` — SPEC-0001 (the
  contract this feature must satisfy and generalize).
- `docs/adrs/` — **ADR-0001** (framework-only) and **ADR-0002** (pluggable providers).
- `docs/DESIGN-BRIEF.md` + `docs/SETTINGS-DESIGN-BRIEF.md` — the identity and the settings component language.
