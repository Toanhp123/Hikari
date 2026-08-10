# Architecture Baseline 2 Acceptance Checkpoint

Date: 2026-08-10
Status: ACCEPTED

## Environment

| Surface | Actual value |
|---|---|
| Host | Windows 11 10.0 amd64 |
| JDK | Eclipse Temurin 17.0.20+8 |
| Gradle | 9.5.0 |
| Project Kotlin | 2.4.10 |
| Application ID | `app.openstory` |
| API 26 target | `emulator-5554`, reported SDK `26` |
| API 37 target | `emulator-5556`, reported SDK `37` |

## Room freeze

Room schema 1 fingerprint before and after local verification:

```text
45997c6e179a6d455fc38154dc8da42d110cdc95ae3a264f98638dd68faf1ab2
```

`./scripts/verify-room-schema-stability.sh` and the expected-fingerprint recheck both
passed. The exported Baseline 2 schema remained exactly `1.json`.

## Local acceptance

Command:

```bash
./gradlew --no-daemon --dependency-verification strict \
  :build-logic:test \
  :core:common:test \
  :plugins:api:test \
  :plugins:runtime:testDebugUnitTest \
  :catalog:test \
  :storage:room:testDebugUnitTest \
  :feature:catalog:testDebugUnitTest \
  :app:testDebugUnitTest \
  detekt lintDebug :app:assembleDebug --stacktrace
```

Result: **PASS**. The generated XML reports contain 134 tests, 0 failures, 0 errors,
and 0 skipped tests. Detekt, Android lint, and debug APK assembly passed.

Command:

```bash
./scripts/verify.sh
```

Result: **PASS**. Shell contracts, exact seven-module architecture, package/source
boundaries, structural policy/report, unit suites, Room stability, lint, and assembly
completed successfully.

## Android acceptance

API levels were checked directly:

```bash
adb -s emulator-5554 shell getprop ro.build.version.sdk  # 26
adb -s emulator-5556 shell getprop ro.build.version.sdk  # 37
```

Comprehensive checkpoint:

```bash
ANDROID_SERIAL_API_26=emulator-5554 \
ANDROID_SERIAL_API_37=emulator-5556 \
  ./scripts/checkpoints/architecture-baseline-2.sh
```

Result: **PASS** on both devices. Each target ran:

- `:plugins:runtime:connectedDebugAndroidTest`;
- `:storage:room:connectedDebugAndroidTest`;
- `:feature:catalog:connectedDebugAndroidTest`;
- `:app:connectedDebugAndroidTest`;
- `:app:installDebug`, launcher start, `Status: ok`, and process-presence checks.

The runtime/security, Room adapter, Compose feature, app instrumentation, installation,
and cold-launch paths all passed. The optional live MyAnimeList test was skipped by its
own assumption and is not an acceptance requirement.

Deterministic MyAnimeList reference integration:

```bash
ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.MyAnimeListCatalogContractIntegrationTest \
  --stacktrace
```

Result: **PASS**, one deterministic integration test on API 37.

An initial PowerShell `.bat` invocation parsed the `-Pandroid...` option as a task name
and stopped before instrumentation. The unchanged command was rerun through Git Bash,
which preserved the Gradle property and produced the passing result recorded above.

## Manual architecture audit

Final source ownership, public surfaces, structural signals, and the seven ownership
answers are recorded in
`../architecture-baseline-2/r6-final-audit.md`.

The audit confirms:

- exact seven-module production graph;
- no tracked source in removed modules;
- no production manual app graph or custom ViewModel factory;
- JavaScript-only plugin execution with fail-closed legacy package rejection;
- private Room entities/DAOs and storage-owned transactions;
- catalog-owned canonical matching;
- feature-owned Home/Search/Story UI state;
- no production file over 300 lines and no structural suppression debt.

## Acceptance

Architecture Baseline 2: ACCEPTED
Current production modules: 7
Next: Wave 06 Task 01 - metadata-only Library persistence and story matching foundations
