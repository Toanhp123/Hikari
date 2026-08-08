# Hikari

Hikari is the Android application repository for the OpenStory local-first
novel library. The Android package namespace and application ID are
`app.openstory`.

## Requirements

- JDK 17
- Android SDK Platform 37
- Android SDK Platform 26 emulator image for the min-SDK checkpoint
- Android SDK Platform 37 emulator image for the target-SDK checkpoint
- SDK Build-Tools 36.0.0
- Git
- Git Bash on Windows, or Bash on Linux/macOS

The Gradle Wrapper must be used for every build. Its distribution checksum and
dependency verification metadata are committed to the repository.

## Android SDK setup

Install the required Android SDK packages through Android Studio's SDK Manager,
or with `sdkmanager`:

    sdkmanager \
      "platform-tools" \
      "emulator" \
      "platforms;android-37" \
      "build-tools;36.0.0" \
      "system-images;android-26;google_apis;x86_64" \
      "system-images;android-37;google_apis;x86_64"

The equivalent package identifiers are:

- `platform-tools`
- `emulator`
- `platforms;android-37`
- `build-tools;36.0.0`
- `system-images;android-26;google_apis;x86_64`
- `system-images;android-37;google_apis;x86_64`

Create `local.properties` in the repository root and point `sdk.dir` to the
installed Android SDK.

Windows example:

    sdk.dir=C\:\\Users\\YOUR_NAME\\AppData\\Local\\Android\\Sdk

Linux example:

    sdk.dir=/home/YOUR_NAME/Android/Sdk

Do not commit `local.properties`.

## Bootstrap

Verify that Java 17 and the Gradle Wrapper are available:

    java -version
    ./gradlew --version

On Windows PowerShell:

    .\gradlew.bat --version

## Fast verification

Linux, macOS, and Git Bash:

    ./scripts/verify.sh

Windows PowerShell with Git Bash installed:

    & "C:\Program Files\Git\bin\bash.exe" ./scripts/verify.sh

The shared fast verification command runs:

- versioned module dependency and platform-import policy;
- `app.openstory` application identity verification;
- build-logic tests;
- JVM tests across all modules;
- Android local unit tests across all Android modules;
- Android Lint across all Android modules;
- Detekt;
- strict Gradle dependency verification;
- debug APK assembly.

CI executes the same `scripts/verify.sh` command.

## Wave checkpoint verification

Wave checkpoints additionally require connected instrumentation and launcher
smoke tests on API 26 and API 37.

With both emulators running:

    ANDROID_SERIAL_API_26=emulator-5554 \
    ANDROID_SERIAL_API_37=emulator-5556 \
      ./scripts/checkpoints/app-shell.sh

To run one device independently:

    ANDROID_SERIAL=emulator-5554 ./scripts/instrumentation/android.sh 26

CI runs API 26 and API 37 as independent jobs. The Wave 01 checkpoint job is
green only when fast verification and both instrumentation jobs succeed.

Wave 02 additionally runs the complete Room/database instrumentation suite on
both required API levels:

    ANDROID_SERIAL_API_26=emulator-5556 \
    ANDROID_SERIAL_API_37=emulator-5554 \
      ./scripts/checkpoints/database.sh

To run only the database suite on one connected device:

    ANDROID_SERIAL=emulator-5556 \
      ./scripts/instrumentation/database.sh 26

The Wave 02 CI checkpoint requires the Wave 01 checkpoint plus both database
instrumentation jobs.

Wave 03 freezes the plugin contracts and validates Selector Schema 1 packages
before installation:

    ./scripts/checkpoints/plugin-contracts.sh

On Windows PowerShell:

    & "C:\Program Files\Git\bin\bash.exe" ./scripts/checkpoints/plugin-contracts.sh

The Wave 03 checkpoint runs fast verification plus explicit plugin API,
plugin-host package-inspection, and shared-fixture test suites.

## Current module graph

- `:app` — composition root, Hilt, Compose shell, navigation
- `:core:common` — typed results, errors, clocks, dispatchers, stable primitives
- `:core:model` — platform-independent canonical domain models
- `:core:database` — Room schema, DAOs, migrations, repositories
- `:core:plugin-api` — public plugin contracts, package schemas, fixtures
- `:core:network` — allowlisted plugin HTTP capability
- `:core:plugin-host` — package installation, registry, selector host
- `:core:matching` — deterministic catalog identity matching and aggregate ranking
- `:test:fixtures` — deterministic shared test data

The direct project dependency policy is stored in:

    config/architecture/module-boundaries.json

Every module included by `settings.gradle.kts` must be declared in this policy.
`core:model` remains independent from Android and Compose APIs. `core:plugin-api`
remains independent from Android and filesystem APIs. Test fixtures cannot leak
into production dependencies.

See `docs/contributing/adding-a-module.md` before adding a module.

## Dependency updates

Third-party versions are centralized in `gradle/libs.versions.toml`.

When dependencies intentionally change, regenerate SHA-256 verification
metadata with the complete verification task set, review the resulting diff,
and commit `gradle/verification-metadata.xml`.
