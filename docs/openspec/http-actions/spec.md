---
status: draft
date: 2026-07-11
implements: [ADR-0004]
extends: [SPEC-0001]
---

# SPEC-0002: HTTP Actions

## Overview

HTTP actions generalize the action-button model of [SPEC-0001](../action-buttons/spec.md) into a single
provider (`ActionKind.HTTP`) that fires a user-defined HTTP request — method, URL, headers, auth, and a
JSON body with variable substitution — from the home surface, and reports the outcome **on the tile
itself**. This realizes [ADR-0004](../../adrs/ADR-0004-generalized-http-action-provider.md) under the
framework-only constraint of [ADR-0001](../../adrs/ADR-0001-framework-only-zero-dependency-launcher.md).

## Requirements

### Requirement: General HTTP-action definition

The system MUST let the user define an HTTP action consisting of an HTTP method (GET, POST, PUT, DELETE,
PATCH), a URL, zero or more request headers, an authentication scheme (None, Bearer, or HMAC), and an
optional JSON request body. The definition MUST persist in its own tolerant JSON collection in
`SharedPreferences` keyed by a stable id, and the enabled `ActionButton` of kind `HTTP` MUST reference it
by that id. Bare hostnames entered without a scheme MUST default to `https://`.

#### Scenario: An action fires its defined request

- **WHEN** the user taps an enabled HTTP action whose definition is method/URL/headers/auth/body
- **THEN** the system MUST issue exactly that request off the main thread and MUST apply the configured
  auth and body

#### Scenario: Definition survives a restart

- **WHEN** the app process is killed and relaunched
- **THEN** the HTTP action definition and its enabled button MUST still be present, and a malformed entry
  in the collection MUST be skipped rather than failing the load

### Requirement: On-tile firing state machine

An HTTP action tile MUST express its firing lifecycle on the control itself — not via a Toast or any
off-tile transient. The states MUST be `idle → pending → success` (or `queued` for a durable-task
endpoint) `→ error → timeout`. The health colors MUST come from a **fixed semantic ramp independent of
the themeable accent**: success/queued Sage `#93B98C`, timeout Amber `#D98F3C`, error Clay `#CF6B5A`.
A pending request MUST ignore further taps (no double-fire). A request that returns no response within
8 seconds MUST enter the timeout state. Success and queued MUST briefly hold and then decay to idle;
error and timeout MUST remain until acknowledged.

#### Scenario: Pending is shown on the tile and blocks re-fire

- **WHEN** the user taps an idle action and the request is in flight
- **THEN** the tile MUST show a pending indication on the control, and additional taps MUST be ignored
  until the request resolves

#### Scenario: Success and queued read distinctly and decay

- **WHEN** a plain action succeeds, or a durable-task action is accepted into a queue
- **THEN** the tile MUST show success ("done") or queued ("accepted") respectively in the Sage semantic
  color, and MUST decay back to idle after a brief hold

#### Scenario: Failure is sticky and explains itself

- **WHEN** a request fails or times out
- **THEN** the tile MUST show the error (Clay) or timeout (Amber) state, MUST remain in that state until
  the user acknowledges it, and MUST offer, on demand and without leaving home, the status/reason plus a
  way to re-fire or dismiss

### Requirement: Actions zone placement

Enabled HTTP action tiles MUST render in a dedicated "Actions" zone on the home surface, below the app
grid and visually distinct from both the app tiles (which launch) and the VPN chip (a persistent
toggle). Durable-task actions MUST be marked as such. The zone MUST appear only when at least one action
is enabled and MUST NOT occupy space otherwise.

#### Scenario: No actions, no zone

- **WHEN** no HTTP actions (or other action buttons) are enabled
- **THEN** the home screen MUST NOT show an Actions zone

### Requirement: Action builder

The system MUST provide a dedicated builder screen to create or edit an HTTP action: a title and icon
(the icon reuses the existing remote icon picker), a method segmented control, a URL field, an auth
selector that swaps its fields per scheme, add/remove header rows, and a JSON body editor with tappable
variable chips (`{{battery}}`, `{{timestamp}}`, `{{agent}}`, `{{device}}`, and free-form values) plus a
live JSON validity check. The builder MUST offer a test-fire that issues the real request and shows the
real response (status + truncated body) inline.

#### Scenario: Variable insertion and JSON validation

- **WHEN** the user inserts a variable chip into the body and the body is not valid JSON
- **THEN** the builder MUST indicate the invalid-JSON state and MUST NOT allow a test-fire until it is
  valid; inserted `{{var}}` placeholders MUST NOT themselves be treated as invalid

#### Scenario: Test-fire shows a real, redacted result

- **WHEN** the user test-fires a valid action
- **THEN** the builder MUST perform the real request off-thread and display the status and a truncated
  body, with any Authorization header or HMAC signature redacted

### Requirement: Endpoint templates and HASS migration

The builder MUST be reachable via a "Pick from my endpoints" picker offering pre-wired templates
(grouped, e.g. Switchboard durable tasks and known services) that pre-fill the builder, and via a raw
request path. The existing Home Assistant scene authoring MUST survive as one such template that pre-fills
an HTTP action, and existing enabled `HASS_SCENE` buttons MUST continue to render and fire unchanged.

#### Scenario: A template pre-fills the builder

- **WHEN** the user picks a pre-wired endpoint template
- **THEN** the builder MUST open pre-populated with that template's method, URL, auth, and body, ready to
  adjust and save

#### Scenario: Existing HASS scene buttons keep working

- **WHEN** the user already has enabled Home Assistant scene buttons before this feature
- **THEN** those buttons MUST continue to render on the home and activate their scene when tapped

### Requirement: Secret handling

Secrets (Bearer tokens, HMAC shared secrets) MUST be entered masked, stored in `SharedPreferences`, and
MUST NEVER appear in a tile label, a request preview, a test-fire echo, or an error detail. After entry a
secret MUST be shown only as an obscured summary (e.g. `•••• last4`) with a Replace affordance. HMAC
signing MUST use the platform `javax.crypto.Mac` (`HmacSHA256`) with no third-party dependency.

#### Scenario: A saved secret is never re-displayed

- **WHEN** a secret has been saved and the user returns to the builder
- **THEN** the field MUST show only an obscured summary and a Replace action, never the secret value

#### Scenario: Echoes redact secrets

- **WHEN** a test-fire or an error detail is shown
- **THEN** the Authorization header and any HMAC signature MUST be redacted in what is displayed

### Requirement: Threading and error handling

All HTTP action network operations MUST run off the main thread with UI updates marshaled back to the
main thread. Failures (network errors, non-success responses, timeouts) MUST be surfaced on the tile per
the state machine and MUST NOT crash the app and MUST NOT be silently swallowed.

#### Scenario: Network never blocks the UI thread

- **WHEN** any HTTP action fires or test-fires
- **THEN** the request MUST execute on a background thread and the UI MUST never perform network I/O on
  the main thread
