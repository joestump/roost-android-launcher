---
status: draft
date: 2026-07-12
implements: [ADR-0006]
extends: [SPEC-0002]
---

# SPEC-0003: Synced Actions

## Overview

Synced Actions let an agent provision HTTP action buttons by writing JSON files into a Syncthing-shared
`actions.d/` directory the owner has granted Roost access to. Roost reconciles those files into ordinary
`ActionKind.HTTP` actions ([SPEC-0002](../http-actions/spec.md)) — declaratively, without disturbing
manually-created actions. Realizes [ADR-0006](../../adrs/ADR-0006-declarative-action-provisioning.md) under
the framework-only constraint of [ADR-0001](../../adrs/ADR-0001-framework-only-zero-dependency-launcher.md).

## Requirements

### Requirement: Grant a synced folder

The system MUST let the owner grant Roost a directory via the Storage Access Framework and persist that
grant across restarts (a persistable tree URI). The system MUST expose a "Synced actions" settings screen
showing the granted folder, a sync-now action, the count/last-sync status, and the ability to clear the
grant.

#### Scenario: Granting persists

- **WHEN** the owner picks a folder and the app is later relaunched
- **THEN** the granted folder MUST still be usable without re-picking

### Requirement: Declarative import from actions.d/*.json

The system MUST read every `*.json` file under the granted folder's `actions.d/` subdirectory and upsert
each into an HTTP action keyed by the file's required stable `id`: create it if new, update it if the
definition changed, and enable an `ActionButton(HTTP)` for it. A file MUST define at least `id`, `title`,
`method`, and `url`; `auth`, `headers`, `body`, `secret`, and `icon` are optional. Malformed or
`id`-less files MUST be skipped without failing the rest of the sync.

#### Scenario: A new file becomes an action

- **WHEN** a well-formed `actions.d/deploy.json` appears and a sync runs
- **THEN** an enabled HTTP action with that id/title/request MUST exist and render on the home Actions zone

#### Scenario: Editing a file updates the action

- **WHEN** a synced file's url/body/etc. changes and a sync runs
- **THEN** the corresponding action MUST reflect the new definition (same id, updated request)

#### Scenario: A malformed file is tolerated

- **WHEN** one file is invalid JSON or lacks an id
- **THEN** that file MUST be skipped and all valid files MUST still import

### Requirement: Declarative removal, scoped to synced ids only

The system MUST track the set of ids it has imported from the folder. On each reconcile, any id that was
previously synced but whose file is now absent MUST be removed (definition + secret + button). Actions the
owner created on-device (ids never in the synced set) MUST NEVER be modified or removed by a sync.

#### Scenario: Deleting a file removes its action

- **WHEN** a previously-synced `actions.d/deploy.json` is deleted and a sync runs
- **THEN** the corresponding action MUST be removed from Roost

#### Scenario: Manual actions are untouched

- **WHEN** the owner has a hand-built HTTP action and any sync runs (including one that removes synced items)
- **THEN** the hand-built action MUST remain exactly as it was

### Requirement: Reconcile trigger and ordering

A reconcile MUST run when the owner taps "sync now" and SHOULD run automatically when the home surface
resumes (no live file-watch is required on a tree URI). Reconciliation MUST preserve the existing home
order of already-present actions and append newly-imported actions; it MUST NOT reshuffle the owner's
arrangement on every sync.

#### Scenario: Order is stable across syncs

- **WHEN** the owner has arranged their actions and an unrelated sync runs
- **THEN** the existing actions' relative order MUST be preserved

### Requirement: Secrets and framework-only I/O

An optional `secret` in a file MUST be stored in Roost's normal per-id secret store and MUST NEVER be
re-exported or shown in a label/echo. All folder access MUST use the Storage Access Framework +
`DocumentsContract` + `ContentResolver` + `org.json` — no third-party library.

#### Scenario: A provided secret works but never leaks

- **WHEN** a synced file includes a bearer/HMAC secret and its action fires
- **THEN** the request MUST authenticate with that secret, and the secret MUST NOT appear in any tile
  label, request preview, or error detail
