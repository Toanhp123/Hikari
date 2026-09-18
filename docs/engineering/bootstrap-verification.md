# Phase-0 Bootstrap Verification

## Canonical execution gate

Run on JDK 17 with Android SDK 37 installed. The canonical scripts keep local and CI execution order aligned.

### Environment doctor

```powershell
.\scripts\verify-bootstrap.ps1 -DoctorOnly
```

```sh
./scripts/verify-bootstrap.sh --doctor-only
```

This validates JDK 17, `platforms;android-37`, `build-tools;37.0.0`, wrapper properties and—when already present—the official wrapper JAR checksum. A missing wrapper JAR is not treated as green; the full gate materializes and verifies it.

### Full device gate

```powershell
.\scripts\verify-bootstrap.ps1
```

```sh
./scripts/verify-bootstrap.sh
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

Both GitHub Actions jobs explicitly provision `platform-tools`, `platforms;android-37`, and `build-tools;37.0.0`. The host job executes `./scripts/verify-bootstrap.sh --host-only`. The instrumentation matrix then runs `:app:connectedDebugAndroidTest` on API 23 and API 37 emulators.

This avoids a subtle clean-runner failure where an API-23 emulator exists but the compile SDK 37 platform is absent.

## Environment limitation of the generation session

The artifact-generation container has Java 21, no Android SDK and no system Gradle installation. Network/DNS restrictions prevent materializing the official Gradle wrapper JAR. The environment doctor therefore correctly fails with `Expected JDK 17, found Java 21`.

For that reason this revision treats build/test execution as **pending external verification**, not as proven green.

## Static/harness evidence already collected

The generation session passed:

- manifest security verifier;
- architecture dependency allow-list scan;
- XML/TOML/YAML parse checks;
- Bash syntax validation;
- placeholder/dynamic-version/built-in-Kotlin/forbidden-permission/secret scans;
- `bootstrap-execution-harness-test.sh` (JDK mismatch, valid fake JDK17+SDK37 doctor, missing API37 failure);
- `ci-bootstrap-contract-test.sh` (both CI jobs provision compile SDK/build-tools and host CI exercises the canonical host gate).

These checks do not replace real Gradle/Android execution.
