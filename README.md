# Gatekeeper

A productivity-gated app blocker for Android. Gatekeeper intercepts attempts to
open apps you've marked as distracting and makes you complete a productive
action first — finish tasks, do exercises, or read pages in the built-in EPUB
reader — before granting a timed access session.

**Privacy:** 100% on-device. The app declares no `INTERNET` permission, makes no
network calls, and has no analytics. Nothing ever leaves your phone.

## How it works

1. You open a gated app (e.g. Instagram).
2. Gatekeeper's `AccessibilityService` sees the window change and instantly
   covers it with a full-screen gate.
3. The gate offers the gate types you enabled for that app — you pick one on
   the spot: **Tasks**, **Exercises**, or **Reading**.
4. Complete the required amount (e.g. 2 tasks, or 1 exercise, or 10 pages in
   the embedded Readium EPUB reader).
5. Gatekeeper grants a timed session (default 10 min) **for that one app only**
   and opens it. When the session expires, the next open re-triggers the gate.

Optional per-app **daily caps** and **cooldowns** limit how often an app can be
unlocked. All verification is honor-system by default; optional friction
(exercise confirmation timers, strict reading mode with per-page dwell time and
net-new-progress counting) can be enabled in Settings.

## Tech stack

- Kotlin, Jetpack Compose (Material 3), MVVM + Flow
- Hilt, Room, DataStore, WorkManager
- `AccessibilityService` (event-driven detection — no polling), foreground
  service, boot receiver
- [Readium Kotlin Toolkit](https://github.com/readium/kotlin-toolkit) 3.3
  (`EpubNavigatorFragment`) for the embedded DRM-free EPUB reader; pages are
  counted via Readium synthetic positions
- Min SDK 26, target SDK 35

## Building

```
./gradlew :app:assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

## Setup on device

Gatekeeper needs (and walks you through, on first run):

1. **Accessibility service** — required; detects the foreground app. Gatekeeper
   never reads screen content (`canRetrieveWindowContent="false"`).
2. **Notifications** (Android 13+) — for the quiet persistent notification.
3. **Battery optimization exemption** — keeps aggressive OEM battery managers
   (Xiaomi, Samsung, Oppo, Huawei…) from killing the service.
4. **Display over other apps** (optional) and **usage access** (optional).

## Known platform limits

Android cannot fully prevent a determined user from disabling the
accessibility service, force-stopping the app, or uninstalling it. Gatekeeper
detects a disabled service and prompts to re-enable it; "strict mode" adds a
confirmation step before removing a gate. That's the realistic ceiling.

## v2 ideas (out of scope for v1)

- Automatic exercise rep counting (sensors/camera)
- DRM (LCP) EPUBs, PDF, audiobooks
- Cloud sync / multi-device, scheduling windows
