# Roost — project context for Claude

Roost is a tiny, **vendor-neutral** Android home-screen launcher that turns a spare phone into a dedicated
device for an AI agent. It boots into the owner's agent app and keeps a curated set of apps, web apps, and
action buttons one tap away. Package `rocks.stump.roost`.

- **Primary repo:** https://gitea.stump.rocks/joestump/roost-android-launcher (auto-mirrors to
  https://github.com/joestump/roost-android-launcher)
- **Docs site:** https://joestump.github.io/roost-android-launcher/ (and Gitea Pages)

## Architecture Context

- **Architecture Decision Records** are in `docs/adrs/` (MADR format).
- **Specifications** are in `docs/openspec/` (paired `spec.md` + `design.md` per capability).

Read these before making architectural changes. Current set:

- **ADR-0001 — Framework-only, zero-dependency launcher.** Pure Android framework: no AndroidX, no
  Compose, no Material, no third-party libraries. Programmatic views, `Canvas`/`VectorDrawable`, system
  fonts, `SharedPreferences`, `HttpURLConnection` + `org.json`. Enforced by an **empty `dependencies {}`**
  block and `android.useAndroidX=false` in `app/build.gradle.kts`. **Do not add dependencies** without
  superseding this ADR.
- **ADR-0002 — Pluggable action-button providers.** Uniform `ActionButton(kind, key, title, a, b)` model;
  each provider is a stateless object with scan + invoke. First providers: Android app-shortcuts, Home
  Assistant scenes. Governs SPEC-0001.
- **ADR-0003 — Icon rendering strategy.** Framework-only icon fetch/cache + hand-rolled SVG-path renderer.
- **ADR-0004 — Generalized HTTP-action provider.** Adds `ActionKind.HTTP`: any endpoint (method + URL +
  headers + None/Bearer/HMAC auth + `{{var}}` JSON body) is one saved instance in a tolerant `Prefs` JSON
  collection keyed by id; firing shows an on-tile `idle → pending → success/queued → error → timeout`
  state machine (fixed semantic ramp — Sage/Amber/Clay, not the accent), replacing the Toast. HASS scenes
  become an authoring path. HMAC uses platform `javax.crypto.Mac`. Governs SPEC-0002.
- **ADR-0005 — Settings navigation IA.** Monolithic `SettingsActivity` → a landing + per-category detail
  Activities (Home & behavior / Agent / Appearance / Apps-tiles-content / Network), framework-only
  drill-down via `startActivity` + the system back stack (no nav component); featured agent + Favorites
  become app pickers, not raw package fields. Realizes `docs/SETTINGS-DESIGN-BRIEF.md`.
- **ADR-0006 — Declarative action provisioning.** An agent creates HTTP actions by writing
  `actions.d/*.json` into a Syncthing-shared folder the owner grants Roost (SAF persistable tree URI);
  Roost reconciles them into `ActionKind.HTTP` actions on resume — declarative (upsert + remove-on-missing),
  scoped to a tracked synced-id set so manual actions are never touched. Framework-only via
  `DocumentsContract` + `ContentResolver` + `org.json`. Governs SPEC-0003.
- **SPEC-0001 — Action Buttons** (`docs/openspec/action-buttons/`).
- **SPEC-0002 — HTTP Actions** (`docs/openspec/http-actions/`).
- **SPEC-0003 — Synced Actions** (`docs/openspec/synced-actions/`).

When implementing code governed by an artifact, leave a governing comment:
`// Governing: ADR-0002 (pluggable action-button providers), SPEC-0001 REQ "Requirement Name"`.

## Build & run

Requires JDK 17 and the Android SDK (platform 34, build-tools 34.0.0).

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell cmd package set-home-activity rocks.stump.roost/.MainActivity
```

Reference device: Pixel 7a / Android 15. There is no test suite; verify changes on-device
(`uiautomator dump` is handy for confirming the view tree renders).

## Docs site

`docs-site/` is a Docusaurus project deploying to GitHub Pages + Gitea Pages. Gotchas: pin webpack
`5.105.2`, keep all `@docusaurus/*` at the exact same version, no mermaid (SSR ReactContextError on 3.9.2).
