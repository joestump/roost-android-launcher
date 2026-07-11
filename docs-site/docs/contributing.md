---
title: Contributing
sidebar_position: 9
---

# Contributing

## Where the code lives

Roost is developed on **Gitea** (source of truth) and **mirrors automatically to GitHub** on every push:

- Primary: [gitea.stump.rocks/joestump/roost-android-launcher](https://gitea.stump.rocks/joestump/roost-android-launcher)
- Mirror: [github.com/joestump/roost-android-launcher](https://github.com/joestump/roost-android-launcher)

Open issues or PRs on either; changes land on Gitea and propagate.

## Local development

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

There's no test suite yet — the app is small and UI-driven, so changes are verified on-device (a Pixel 7a /
Android 15 is the reference device). `uiautomator dump` is handy for confirming the view tree renders.

## Docs

This site is a Docusaurus project under `docs-site/`. It deploys to **both** GitHub Pages and Gitea Pages
on push to `main`.

```bash
cd docs-site
npm ci
npm run start   # local preview
npm run build   # production build
```

## Style

Match the surrounding code: pure Android framework (no AndroidX/Compose/Material), programmatic views,
palette tokens from `Roost.kt`. Keep it small and readable.

## License

MIT.
