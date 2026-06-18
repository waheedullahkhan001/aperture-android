# Aperture — Emergency Camera (Android client)

Aperture turns a phone into an instant, discreet evidence recorder. A configurable **volume-button
sequence starts recording without unlocking the device** — even with the screen off — saves locally,
optionally **live-streams to a self-hosted server**, and can run a **cancelable countdown** that alerts
your contacts if you don't stop it.

Final Year Project (BS Computer Science, FUUAST Islamabad). This repository is the **Android client**;
accounts, contact management, server-recording browsing, and alert email dispatch live in the companion
web app and backend.

## Highlights

- **Rapid activation** — press a configurable volume pattern (default: volume-up ×3) to start recording
  from locked/screen-off, via an `AccessibilityService`.
- **Offline-first** — recording and local save never depend on the network. Streaming and alerts are
  opt-in enhancement.
- **Dual capture pipeline** — local-only (CameraX → gallery) or stream + parallel local
  (RootEncoder → RTSP/RTMP/SRT, local file preserved even if the push drops).
- **Emergency shell** — start → cancelable countdown → (on expiry) contacts are alerted with a stream
  link. Cancelling during the grace period stops recording and suppresses the alert.
- **Full configuration** — activation pattern, camera/quality/fps, streaming, server (`baseUrl + token`),
  emergency countdown/message, notification style (discreet/clear), metadata, and storage policy.
- **In-app recordings library** with an auto-delete-oldest storage cap, and a **system-readiness
  dashboard** that flags anything (permissions, accessibility, battery optimisation) that would quietly
  break reliability.

## Architecture

Simple, layered MVVM + repositories, applied consistently:

```
core/        DI (Hilt) modules, theme
data/        settings (DataStore) · recordings (MediaStore) · server (OkHttp) · metadata
domain/      models + the pure trigger/ detector (framework-free, unit-tested)
recording/   thin foreground service + Recorder strategy (CameraX | Streaming) + state holder + publisher
trigger/     volume AccessibilityService (delegates to the pure detector)
launcher/    transparent bridge activity (background → foreground service)
ui/          Navigation Compose · home · readiness · settings (one screen per concern)
```

The defining quality move: the proof-of-concept's single ~560-line `RecordingService` god-object is
decomposed into a thin service that only orchestrates, a `Recorder` strategy with two pipelines, an
injected state holder (replacing global static state), and a pure `TriggerPatternDetector` with real
unit tests.

**Stack:** Kotlin 2.2 · Jetpack Compose (Material 3) · Hilt · DataStore · Navigation Compose ·
CameraX · RootEncoder · OkHttp · Coroutines/Flow. Min SDK 28, target/compile SDK 36, AGP 9.

## Build & run

```bash
./gradlew :app:assembleDebug        # build the debug APK
./gradlew :app:testDebugUnitTest    # run the unit tests
./gradlew installDebug              # install on a connected device
```

First launch walks through the mandatory permissions (camera, microphone, notifications) and enabling
the accessibility service. Optional metadata/location lives under Settings ▸ Metadata.

## Scope

Implemented here (native client): rapid activation & configuration, local + streaming recording,
the in-app recordings library + storage policy, server connection, the emergency countdown/cancel
shell, metadata capture, and the readiness dashboard.

Out of scope by design (web app / backend): user accounts (register/login/recover), emergency-contact
management, browsing/downloading server recordings, and the actual alert-email dispatch.

## Status & known limitations

- Built and unit-tested green; **on-device behaviour (camera, streaming, trigger) should be verified on
  real hardware** — this is the one thing CI/unit tests can't cover.
- CameraX doesn't expose a simple frame-rate override, so configured fps applies to the streaming
  pipeline only. Streaming currently always uses the back camera. Both are noted in code.
- The emergency `dispatchAlert()` is an on-device stub; sending the email is the backend's job.

## Documentation

- `docs/reference/project-doc.md` — the full SRS (6 modules, 18 use cases).
- `docs/superpowers/specs/2026-06-08-aperture-android-design.md` — design decisions, architecture, plan.
- `CLAUDE.md` — working notes and conventions for contributors.
