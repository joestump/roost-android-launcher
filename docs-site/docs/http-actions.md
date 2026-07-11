---
title: HTTP Action tiles
sidebar_position: 5
---

# HTTP Action tiles

Most of the things an agent device needs to *do* are the same shape: **an HTTP request** — a method, a URL,
some headers, an auth scheme, and a JSON body. Kick a durable task into a queue, flip a Home Assistant
scene, poke a webhook, nudge a self-hosted service. Rather than a bespoke button per service, Roost has one
primitive: the **HTTP action tile**.

An HTTP action is a universal "does a thing" home tile. You define the request once in a builder; the tile
lives in the [Actions zone](./design.md#the-actions-zone) below the app grid and fires it on tap — and it
tells you how it went **right on the tile**.

This realizes [ADR-0004](https://gitea.stump.rocks/joestump/roost-android-launcher/src/branch/main/docs/adrs/ADR-0004-generalized-http-action-provider.md)
and is formalized in [SPEC-0002](https://gitea.stump.rocks/joestump/roost-android-launcher/src/branch/main/docs/openspec/http-actions/spec.md).

## The on-tile state machine

An HTTP request is asynchronous and can fail — so a `Toast` that pops and vanishes is the wrong feedback:
it's off-tile, it can't show "in flight," and it's illegible from across a dim room. Roost puts the outcome
**on the control itself**. The tile is a small `Canvas` disc driven by a `Handler` tick through a state
machine:

<img src="/roost-android-launcher/img/http-action-firing.png" alt="An action tile mid-fire — a sweeping accent ring and 'firing…' drawn on the tile" width="320" />

| State | On the tile |
| --- | --- |
| **idle** | Resting. |
| **pending** | A sweeping accent ring + "firing…" while the request is in flight. Further taps are ignored — no double-fire. |
| **success** | "done · 200 OK", briefly held, then decays back to idle. |
| **queued** | "accepted" — for durable-task endpoints that take the work and return. |
| **error** | Sticky. Tap to see why, then **Re-fire** or **Dismiss**. |
| **timeout** | After **8 seconds** with no response, sticky. |

The health colors come from the [fixed semantic ramp](./design.md#health-colors--a-fixed-semantic-ramp),
**not** the themeable accent — success/queued **Sage**, timeout **Amber**, error **Clay** — so "failed"
stays red even if you picked a red-ish accent. Success and queued decay on their own; error and timeout
stay put until you acknowledge them.

## The builder

`HttpActionActivity` is the builder — everything about a request in one focused screen:

<img src="/roost-android-launcher/img/http-action-builder.png" alt="The HTTP action builder — method and auth segmented controls, a masked secret, header rows, a JSON body with variable chips and a valid-JSON hint, and Test fire" width="320" />

- A **title** and **icon** (the icon reuses the remote icon picker).
- A **method** segmented control — GET, POST, PUT, DELETE, PATCH.
- A **URL** field — a bare hostname without a scheme defaults to `https://`.
- An **auth** selector that swaps its fields per scheme (below).
- **Add / remove header rows.**
- A **JSON body** editor with tappable **variable chips** and a live **"valid JSON"** check.
- **Test fire** — issues the real request off-thread and shows the real response (status + a truncated
  body) inline.

### Variables

The body can carry `{{var}}` placeholders that Roost substitutes at fire time:

- `{{battery}}` — current battery level
- `{{timestamp}}` — the moment of firing
- `{{agent}}` — the agent name
- `{{device}}` — the device identity

Tap a chip to insert one. Inserted `{{var}}` placeholders don't count against the JSON-validity check; if
the body is otherwise not valid JSON, the builder flags it and won't let you test-fire until it is.

## Auth & secrets

Three auth schemes:

- **None**
- **Bearer** — an `Authorization: Bearer …` token.
- **HMAC** — the body is signed with a shared secret. Roost uses the platform's own `javax.crypto.Mac`
  (`HmacSHA256`) — no third-party dependency, staying inside the framework-only rule.

Secrets are handled carefully. You enter them **masked**; they're stored in `SharedPreferences` and shown
afterward only as an obscured summary — `•••• last4` — with a **Replace** affordance. A secret never appears
in a tile label, a request preview, a test-fire echo, or an error detail: the `Authorization` header and any
HMAC signature are **redacted** from everything Roost shows you.

## Pick from my endpoints

You don't have to build every action from raw parts. The builder is reachable via a **"Pick from my
endpoints"** picker that pre-fills it from a template:

<img src="/roost-android-launcher/img/http-actions-endpoints.png" alt="The 'Pick from my endpoints' picker — grouped pre-wired templates plus a raw-request path" width="320" />

- **Durable tasks** — endpoints that accept work into a queue and return "accepted".
- **Known services** — pre-wired shapes for things like Home Assistant, a LiteLLM endpoint, or a Gitea
  webhook (generic `example.com` hosts you point at your own).
- **Raw request** — start from a blank method/URL/body and wire it up yourself.

Pick a template and the builder opens pre-populated with its method, URL, auth, and body, ready to adjust
and save.

## Home Assistant scenes are just an HTTP action now

Firing a Home Assistant scene was always *"POST some JSON to a URL with a bearer token"* — so it's no longer
special. A HASS scene is now **one saved HTTP action**, fired through the same client and the same on-tile
state machine as everything else. The Home Assistant **scene picker survives as an authoring path**: it
pre-fills the builder from your account and scenes. And any Home Assistant scene buttons you'd already
enabled **keep rendering and firing unchanged** — nothing to migrate by hand.

## Framework-only, of course

No libraries were added for any of this. The animated disc is a `Canvas` view on a `Handler` tick;
networking is `HttpURLConnection` + `org.json` off the main thread; HMAC is `javax.crypto.Mac` from the
platform. See [How it works](./architecture.md) for the files, and
[ADR-0001](https://gitea.stump.rocks/joestump/roost-android-launcher/src/branch/main/docs/adrs/ADR-0001-framework-only-zero-dependency-launcher.md)
for the constraint.
