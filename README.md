# Universal Media — V1 Foundation

This repository contains the verified Android foundation for the Universal Media App. Phase 0 bootstrap is closed: the real Windows host/device/release-like gate and the clean-checkout GitHub Actions host + API-23 + Android-17 instrumentation matrix are green. Product code intentionally remains a minimal launch surface; V1 implementation now begins with the deliberately small first local vertical slice defined in §13.1 of the canonical foundation.

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

CI pushes run on the canonical `master` branch and pull requests. Both lanes use `android-actions/setup-android@v4`, pin Android command-line tools build `15859902`, request only `platform-tools` from the setup action, and explicitly install `platforms;android-37.0` plus `build-tools;37.0.0`. The host lane runs the same `verify-bootstrap.sh --host-only` command used locally. Before `android-emulator-runner@v2`, the instrumentation lane repoints the runner image's stale `cmdline-tools/latest` to the pinned setup-android toolchain so minor-version packages such as `android-37.0` are created with the correct AVD target. The lane then enables KVM and runs the launch smoke test on API 23 (`default`) and Android 17 package `37.0` (`google_apis`) emulators. `:app` explicitly consumes the catalog-pinned Espresso 3.7.0 core so Compose UI tests cannot fall back to an older transitive input-injection implementation that is incompatible with Android 17. The removed legacy SDK package `tools` must not be reintroduced.

## Evidence state

Static architecture/security/configuration checks plus execution-harness contract tests passed in the generation environment. On 2026-09-18, the canonical PowerShell verifier passed end-to-end on the real Windows development host with Temurin JDK 17.0.20, Gradle 9.4.1, SDK `platforms;android-37.0`, and a Redmi Note 9S running Android 15: doctor, Gradle runtime/help, `verifyFast`, `:app:connectedDebugAndroidTest`, and `verifyRelease` all completed successfully. GitHub Actions run `35368573651` then completed with overall `success`: host `verify`, API-23/default instrumentation, and Android-17 `37.0`/`google_apis` instrumentation all passed on a clean runner. The blocking Phase-0 bootstrap gate is therefore closed and the §13.1 first local V1 vertical slice is unlocked. Macrobenchmark device measurements remain pending as a separate, non-blocking performance gate. Current PASS/PENDING/BLOCKED truth and the next concrete action live only in the foundation **Current Control Block**. See `docs/engineering/bootstrap-verification.md` for the runbook.
