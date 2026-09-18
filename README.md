# Universal Media — Pre-V1 Bootstrap

This repository skeleton implements the current Phase-0 bootstrap baseline in `docs/foundation/android-universal-media-app-foundation.md` for the Android Universal Media App. It intentionally contains only a minimal launch surface; scanner, Room schema, source resolution, playback and reader feature breadth begin only after the executable bootstrap gate is green.

## Toolchain

- JDK 17
- Gradle 9.4.1
- Android Gradle Plugin 9.2.1
- Kotlin 2.4.20
- Compose Compiler plugin 2.4.20
- Compose BOM 2026.08.00
- compileSdk/targetSdk 37
- minSdk 23

## First setup

The generated archive does not fabricate a Gradle Wrapper binary. If `gradle/wrapper/gradle-wrapper.jar` is absent, the full bootstrap verifier materializes the official wrapper with the pinned Gradle distribution/wrapper SHA-256 values.

Before running builds, validate the machine:

```powershell
.\scripts\verify-bootstrap.ps1 -DoctorOnly
```

or on POSIX/Git Bash:

```sh
bash scripts/verify-bootstrap.sh --doctor-only
```

The doctor requires JDK 17, `ANDROID_SDK_ROOT`/`ANDROID_HOME`, `platforms;android-37.0`, and `build-tools;37.0.0`. Missing wrapper JAR is reported as `PENDING` because the full gate owns safe wrapper materialization.

## Canonical verification

Full bootstrap execution gate with exactly one connected Android device (or `ANDROID_SERIAL` set):

```powershell
.\scripts\verify-bootstrap.ps1
```

```sh
bash scripts/verify-bootstrap.sh
```

Host-only gate when no device is available:

```powershell
.\scripts\verify-bootstrap.ps1 -HostOnly
```

```sh
bash scripts/verify-bootstrap.sh --host-only
```

The host gate runs wrapper verification/materialization, `gradlew --version`, `help`, `verifyFast`, and `verifyRelease`. The full gate additionally runs `:app:connectedDebugAndroidTest` before the release-like gate.

Macrobenchmark requires a supported device/emulator and is intentionally a separate, slower performance gate.

## Agent workflow

`AGENTS.md` is intentionally small and only routes Codex/agents to the canonical foundation. Read the foundation's **Current Control Block** first, then search only the decision record(s) relevant to the task. Do not read the entire foundation by default and do not create parallel state/handoff documents.

## Architecture

The dependency graph is intentionally asymmetric: core contracts point inward; Android/storage/source/playback implementations point toward contracts; feature modules do not depend on one another; `:app` is the top-level composition root. `verifyArchitecture` guards the bootstrap allow-list.

## CI

Both CI lanes explicitly install `platforms;android-37.0`, `build-tools;37.0.0`, and platform-tools. The host lane runs the same `verify-bootstrap.sh --host-only` command used locally; instrumentation then proves the launch smoke test on API 23 and API 37 emulators.

## Evidence state

Static architecture/security/configuration checks plus execution-harness contract tests passed in the generation environment. The generation container itself has Java 21 and no Android SDK, so Gradle sync/build, JVM tests, Android lint/instrumentation, release assembly, CI execution, and Macrobenchmark execution are still **not** claimed green. Current PASS/PENDING/BLOCKED truth and the next concrete action live only in the foundation **Current Control Block**. See `docs/engineering/bootstrap-verification.md` for the runbook.
