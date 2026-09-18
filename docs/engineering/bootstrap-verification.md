# Phase-0 Bootstrap Verification

## Canonical execution gate

Run on JDK 17 with the Android SDK `platforms;android-37.0` package installed. The canonical scripts keep local and CI execution order aligned.

### Environment doctor

```powershell
.\scripts\verify-bootstrap.ps1 -DoctorOnly
```

```sh
bash scripts/verify-bootstrap.sh --doctor-only
```

This validates JDK 17, `platforms;android-37.0`, `build-tools;37.0.0`, wrapper properties and—when already present—the official wrapper JAR checksum. A missing wrapper JAR is not treated as green; the full gate materializes and verifies it.

### Full device gate

```powershell
.\scripts\verify-bootstrap.ps1
```

```sh
bash scripts/verify-bootstrap.sh
```

The full sequence is:

1. validate JDK 17 + SDK 37;
2. materialize/verify the official Gradle 9.4.1 wrapper if required;
3. `gradlew --version --no-daemon`;
4. `gradlew help --no-daemon`;
5. `gradlew verifyFast --no-daemon`;
6. `gradlew :app:connectedDebugAndroidTest --no-daemon`;
7. `gradlew verifyRelease --no-daemon`.

When multiple devices are connected, set `ANDROID_SERIAL`; otherwise the script requires exactly one online adb device to avoid ambiguous test execution.

### Host-only gate

Use `-HostOnly` / `--host-only` when no device is attached. This proves every host/build gate but intentionally does not satisfy the Android instrumentation checklist entry.

## CI contract

Both GitHub Actions jobs explicitly provision `platform-tools`, `platforms;android-37.0`, and `build-tools;37.0.0`. The host job executes `bash scripts/verify-bootstrap.sh --host-only`. The instrumentation matrix then runs `:app:connectedDebugAndroidTest` on API 23 and API 37 emulators.

This avoids a subtle clean-runner failure where an API-23 emulator exists but the compile SDK 37 platform is absent.

## Evidence ownership

This file is a runbook, not the current-state authority. Current `PASS` / `PENDING` / `BLOCKED` evidence and the next action live only in the **Current Control Block** of `docs/foundation/android-universal-media-app-foundation.md`. Static contract checks never substitute for real Gradle/Android execution.

## Consistency rules

- Gradle still uses `compileSdk = 37` / `targetSdk = 37`, while the required installed platform package/folder is `platforms;android-37.0` / `<SDK>/platforms/android-37.0/android.jar`.
- `gradle/gradle-daemon-jvm.properties` is not required. If it is introduced by `updateDaemonJvm`, `toolchainVersion` must remain `17`; the canonical verifier rejects a contradictory daemon JVM baseline.
- Do not infer executable PASS from this runbook, historical plans, or static inspection; use the foundation Current Control Block.
