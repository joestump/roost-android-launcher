---
status: accepted
date: 2026-07-12
decision-makers: [joestump, Claude]
extends: [ADR-0001, ADR-0004]
governs: [SPEC-0003]
---

# ADR-0006: Declarative action provisioning from a synced directory

## Context and Problem Statement

The owner wants a **dead-simple way for an agent (Claude, running on a laptop or the agent box) to create
HTTP action buttons on the phone** without hand-driving the on-device builder. The devices already share a
WireGuard network and the agent box's `$HOME` is Syncthing-mirrored to the phone, so the phone can *see*
files the agent writes. How should Roost ingest agent-authored actions — a network server on the phone,
Bluetooth, or something file-based — while staying framework-only ([ADR-0001](ADR-0001-framework-only-zero-dependency-launcher.md))
and not clobbering actions the owner made by hand?

## Decision Drivers

* **"Claude just writes a file."** The simplest possible authoring surface — no pairing, no on-phone
  server to secure, no always-on listener.
* **Rides existing infra.** Syncthing already carries the agent box's `$HOME` to the phone; a watched
  folder needs zero new transport.
* **Declarative + drop-in.** A directory of one-file-per-action (`actions.d/*.json`) so the agent can add,
  edit, or delete an action by adding/editing/removing a file — the directory is the source of truth for
  synced actions.
* **Framework-only.** No AndroidX/library. Scoped storage means reading a user-granted directory via the
  Storage Access Framework + `DocumentsContract` + `ContentResolver`, and `org.json`.
* **Don't disturb manual actions.** Actions the owner built on-device must never be touched by a sync.

## Considered Options

* **A. Synced `actions.d/` directory (declarative import).** The owner points Roost at a synced folder
  (SAF persistable tree URI); Roost reconciles `actions.d/*.json` into HTTP actions on resume: upsert each
  file's action, and remove any *previously-synced* action whose file is gone. A "Synced actions" settings
  page manages the folder + shows status.
* **B. HTTP pull from a config URL.** Roost periodically GETs an action-config endpoint and imports it.
* **C. On-phone HTTP/MCP server.** Roost runs an authenticated `ServerSocket` in a foreground service; a
  laptop-side MCP shim POSTs actions to it.

## Decision Outcome

Chosen option: **A**. It is the simplest authoring surface (write a JSON file), needs no new transport
(Syncthing is already planned), no on-phone network listener to secure, and is declarative — the agent
manages actions by managing files. B is a fine fallback when Syncthing isn't set up but adds a polling
loop and a hosted endpoint; C is the most "live" but is the most to secure (an always-on service + a
network listener + a shim) and is deferred to a later ADR if instant push is wanted. Bluetooth is rejected
outright (range-limited, pairing-fragile, solves nothing WireGuard doesn't).

### Consequences

* Good, because the agent's authoring API is "write `~/.roost/actions.d/<name>.json`"; Syncthing does the
  rest and the buttons appear on the next home resume.
* Good, because it's declarative: editing a file updates the action, deleting it removes the action —
  reconciled against a tracked set of synced ids so **manual actions are never touched**.
* Good, because it's framework-only: SAF `ACTION_OPEN_DOCUMENT_TREE` yields a persistable tree URI; the
  folder is enumerated with `DocumentsContract.buildChildDocumentsUriUsingTree` + `ContentResolver.query`,
  files read with `ContentResolver.openInputStream`, parsed with `org.json`.
* Bad, because a synced JSON file **may carry a secret** (a bearer token / HMAC shared secret) so the
  action works end-to-end. That is acceptable only because the folder is the owner's own private,
  device-to-device Syncthing share — the ADR flags it, secrets are optional, and Roost stores any provided
  secret in its normal secret store (never re-exported).
* Bad, because reconciliation runs on resume (no live `FileObserver` on a tree URI), so a newly-synced file
  appears on the next return to home, not instantly. Acceptable for a provisioning flow.
* Neutral, because the imported actions are ordinary `ActionKind.HTTP` actions ([ADR-0004](ADR-0004-generalized-http-action-provider.md)) —
  they render, fire, and can be arranged exactly like hand-made ones.

### Confirmation

A "Synced actions" settings screen lets the owner grant a folder (persisted tree URI) and sync now; a
`SyncedActions` reconciler reads `actions.d/*.json`, upserts each into `Prefs.httpActions` + an enabled
`ActionButton(HTTP)` keyed by the file's id, records the synced-id set in `Prefs`, and removes only
previously-synced ids whose file has disappeared. A reviewer confirms: a manual action is never modified
by a sync; deleting a synced file removes its action on the next reconcile; and no non-framework API is
used. Formalized in [SPEC-0003](../openspec/synced-actions/spec.md).

## More Information

* The one-file schema (per `actions.d/<name>.json`), keyed by a stable `id`:
  `{ "id", "title", "method", "url", "auth": "none|bearer|hmac", "headers": {…}, "body", "secret"? , "icon"? }`.
  `id` is required and stable (the reconcile key); `icon` is an optional selfh.st/Simple-Icons/Heroicons
  slug or URL fetched via `IconStore`.
* Realizes the owner request captured in this session; complements — does not replace — the on-device
  builder ([SPEC-0002](../openspec/http-actions/spec.md)).
* Option C (live on-phone endpoint) remains a possible future ADR if instant push is desired.
