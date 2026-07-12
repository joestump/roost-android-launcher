---
status: draft
date: 2026-07-11
implements: [ADR-0004]
---

# SPEC-0002 Design Notes: HTTP Actions

Design rationale and framework-only implementation shape for [SPEC-0002](spec.md). Realizes
[ADR-0004](../../adrs/ADR-0004-generalized-http-action-provider.md) and the brief
`docs/design-briefs/a-http-action-tile.md`.

## Data model

- `HttpAction(id, method, url, headers: List<Pair<String,String>>, auth: HttpAuth, body)` — the full
  request definition. `HttpAuth = { NONE | BEARER | HMAC }`; the secret is stored separately (keyed by
  the action id), never inline in a way that can leak into an echo.
- The enabled control stays an `ActionButton(kind = ActionKind.HTTP, key = "http:<id>", title, a = id,
  b = "")` — the ADR-0002 model is unchanged; `a` references the `HttpAction` by id, exactly as
  `HASS_SCENE` uses `a = HassAccount.id`.
- Persistence: a tolerant JSON collection `http_actions` in `Prefs` keyed by id (mirrors `hass_accounts`);
  secrets in a parallel keyed store. A malformed entry is skipped, not fatal.

## The client

`HttpActionClient` generalizes `Hass`: build an `HttpURLConnection` for the method, apply user headers,
apply auth (`Bearer` → `Authorization: Bearer …`; `HMAC` → sign the resolved body with
`javax.crypto.Mac` `HmacSHA256` and send the signature header), substitute `{{var}}` tokens in the body
at fire time, 8 s connect/read timeouts, read status + (truncated) body. Runs on a `Thread`; results
marshaled back with `runOnUiThread`.

## On-tile state machine (the centerpiece)

`ActionTileView` is a `Canvas` `View` subclass. A `Handler(Looper.getMainLooper())` posts an
`invalidate()` tick (~16 ms) only while pending, drawing the sweeping accent arc; on success it morphs the
arc to a check (Sage), on error/timeout it settles to a static disc (Clay/Amber). The animation loop MUST
start on fire and stop when the result lands or the Activity is not resumed (battery — same lifecycle
discipline as the 1 s `rateTick` in `MainActivity`). Taps are ignored while `state == pending`. Semantic
ramp is fixed: Sage `#93B98C` (success/queued), Amber `#D98F3C` (timeout), Clay `#CF6B5A` (error) — never
the themeable accent.

## Screens

- **Actions zone** on home: a labeled band below the app grid; each action is a full-width rounded tile
  (`Roost.rounded`) with a status disc + label + mono status line, an expand affordance for the error/why
  path (status code + reason + redacted auth + Re-fire/Dismiss). `task`-tagged tiles read "queued", not
  "done".
- **`HttpActionActivity`** (new, one Activity): the builder — title/icon, method + auth segmented
  controls (rows of `Roost.rounded` pills), URL field, add/remove header rows (plain `LinearLayout`, no
  RecyclerView), JSON body `EditText` + tappable `{{var}}` chips + live validity, masked secret field
  (`•••• last4` / Replace), and a test-fire result panel (status pill + truncated, redacted body).
- **Endpoints picker**: sectioned pre-wired templates (Switchboard tasks, known services incl. the HASS
  scene authoring path) that pre-fill the builder, plus a "Raw request" entry. Reuses `IconPickerActivity`
  for the action icon.

## Dispatch

`ActionKind.HTTP` adds exactly one branch to each of the `icon` and `invoke` `when(kind)` sites
(ADR-0002 invariant). `invokeAction()` for `HTTP` runs the client off-thread and drives the tile's state
machine instead of popping a Toast. The home render loop's contract is unchanged.
