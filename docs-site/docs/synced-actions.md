---
title: Synced actions
sidebar_position: 6
---

# Synced actions

The fastest way to put an action button on the phone is to **not touch the phone at all**. An agent —
Claude on a laptop, say — creates a [HTTP action tile](./http-actions.md) by writing a small JSON file into
a folder that syncs to the device. No pairing, no on-phone server, no on-device builder: the agent just
writes a file, and the button appears.

Roost imports HTTP actions from a folder the owner shares to the phone with **Syncthing**. It reads every
`actions.d/*.json` file in that folder and turns each into an HTTP action button. Add a file and a button
appears; edit it and the button changes; delete it and the button goes away — each on the next sync.
Actions you built by hand on the device are **never touched**.

This realizes [ADR-0006](https://gitea.stump.rocks/joestump/roost-android-launcher/src/branch/main/docs/adrs/ADR-0006-declarative-action-provisioning.md)
and is formalized in [SPEC-0003](https://gitea.stump.rocks/joestump/roost-android-launcher/src/branch/main/docs/openspec/synced-actions/spec.md).

## One file, one action

Each `actions.d/<name>.json` file describes a single HTTP action, keyed by a stable `id`:

```json
{
  "id": "deploy-prod",
  "title": "Deploy prod",
  "method": "POST",
  "url": "https://ci.example.com/hooks/deploy",
  "auth": "bearer",
  "secret": "ghp_your_token_here",
  "headers": { "X-Environment": "production" },
  "body": "{ \"ref\": \"main\" }",
  "icon": "rocket"
}
```

- `id`, `title`, `method`, and `url` are **required**. Everything else is optional.
- `auth` is `none`, `bearer`, or `hmac`, and behaves exactly like the [on-device builder's auth](./http-actions.md#auth--secrets).
- `secret` is **optional**. When present, Roost stores it in its own secret store — the same masked,
  redacted handling as a hand-entered secret. Because the raw token lives in the file until it's imported,
  only put a `secret` in a **private, device-to-device** synced folder.
- `icon` is a [selfh.st](https://selfh.st/icons/) / Simple Icons / Heroicons slug or a full URL, fetched
  through the same icon store the builder uses.

The `id` is the reconcile key, so keep it stable across edits — changing a file's `id` reads as *delete the
old action, add a new one*.

## How it reconciles

The folder is the source of truth for synced actions, and the reconcile is **declarative** — Roost makes
the device match the files, not the other way around:

- **Add or edit a file** → the action is upserted (created if new, updated in place if the definition
  changed, same `id`).
- **Delete a file** → the action is removed. Removal is driven by a file actually being **gone**, so a
  mid-write or malformed file never nukes a working action.
- **Scoped to synced ids only.** Roost tracks the set of ids it imported and only ever touches those.
  Actions you made on the device have ids that were never in that set, so a sync — even one that removes
  synced items — leaves them exactly as they were.
- **Order is preserved.** A reconcile keeps the existing home order of actions already present and appends
  newly-imported ones; it never reshuffles your arrangement.

A reconcile runs when you tap **Sync now** on the Synced actions screen, and automatically when the home
surface resumes — so a file that synced while the phone was idle shows up the next time you return home.

## Setup

Point Roost at the shared folder once:

**Settings → Apps, tiles & content → Action buttons → Synced actions → Grant a folder.**

<img src="/roost-android-launcher/img/synced-actions.png" alt="The Synced actions screen before a folder is granted — an explainer for the actions.d/ layout and a Grant a folder button" width="300" />

**Grant a folder** opens the system folder picker (the Storage Access Framework). Choose the
Syncthing-shared folder that contains your `actions.d/` directory. The grant is a **persistable tree URI**,
so it survives restarts — you pick the folder once, not every launch.

Once granted, the screen shows the folder, the imported-action count and last-sync status, a **Sync now**
button, and a way to clear the grant:

<img src="/roost-android-launcher/img/synced-actions-inuse.png" alt="The Synced actions screen with a folder granted — the synced actions listed with their status, a last-sync line, and Sync now" width="300" />

## Framework-only, of course

No libraries here either. The folder is a Storage Access Framework persistable tree URI; Roost enumerates
`actions.d/` with `DocumentsContract`, reads each file through `ContentResolver`, and parses it with
`org.json`. See [How it works](./architecture.md) for the files and
[ADR-0001](https://gitea.stump.rocks/joestump/roost-android-launcher/src/branch/main/docs/adrs/ADR-0001-framework-only-zero-dependency-launcher.md)
for the constraint.
