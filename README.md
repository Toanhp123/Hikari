# Hikari

Hikari is the Android application repository for the OpenStory local-first
novel library. The Android package namespace and application ID are
`app.openstory`.

## Current repository status

The accepted Product UI and Discover semantic-feed redesigns are implemented. Discover is
source-agnostic at the presentation boundary and renders `Popular`, a full-width
`Manga | Light Novel` selector, `Latest Updates`, and `Top Rated` from cached semantic Home
feeds. Room schema **9** remains current after the Canonical Catalog Reconciliation & Fusion Engine foundation. The production capability graph remains at 14 modules.
The active pre-Wave-10 workstream is the Canonical Catalog Reconciliation & Fusion Engine.
Phases 0-6 / Tasks 1-38 and Phase 7 Tasks 39-41 are verified and closed on Room schema 9;
Phase 7 Task 42 is active as the final governance, acceptance, migration, UI, profile, and
performance certification gate. Wave 10 remains planned and its notification migration is
rebased to `9 -> 10`.

See `docs/project/current-state.md` for the exact boundary and
`docs/implementation/current-roadmap.md` for what comes next.

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

## Verification

For normal development iterations, use the fast host gate.

Linux, macOS, and Git Bash:

    ./scripts/verify-fast.sh

Windows PowerShell with Git Bash installed:

    & "C:\Program Files\Git\bin\bash.exe" ./scripts/verify-fast.sh

The fast gate runs repository/static contracts (including the fail-closed UI token
policy), application identity and module architecture verification, build-logic tests,
JVM/local Android unit tests, Detekt, strict dependency verification, and Room schema
stability. It intentionally skips
Android Lint and debug APK assembly to shorten the edit/verify loop.

Before closing a task or checkpoint, run the canonical full host gate:

Linux, macOS, and Git Bash:

    ./scripts/verify.sh

Windows PowerShell with Git Bash installed:

    & "C:\Program Files\Git\bin\bash.exe" ./scripts/verify.sh

The full gate adds Android Lint and `:app:assembleDebug`. Architecture verification is
part of the same Gradle invocation as the rest of the full Gradle workload, avoiding a
second Gradle startup. Gradle daemon reuse, configuration cache, parallel execution, and
local build cache are enabled for repeated local runs.

CI executes the full `scripts/verify.sh` command.

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
green only when full host verification and both instrumentation jobs succeed.

Run the Room storage instrumentation suite on each required API level when
storage behavior changes:

    ANDROID_SERIAL=emulator-5554 \
      ./scripts/instrumentation/storage-room.sh 26

Repeat with an API 37 device before architecture acceptance.

The shared verification command validates the current plugin protocol, package
installation rules, module boundaries, structural policy, lint, tests, and APK:

    ./scripts/verify.sh

On Windows PowerShell:

    & "C:\Program Files\Git\bin\bash.exe" ./scripts/verify.sh

## Current module graph

- `:app` — composition root, Hilt, Compose shell, navigation
- `:core:common` — Outcome, clocks, dispatchers, and stable cross-capability identifiers
- `:core:designsystem` — application-wide Compose theme, visual tokens, and domain-neutral shared UX presentation
- `:plugins:api` — public plugin protocol and package schemas
- `:plugins:runtime` — package lifecycle, bounded capabilities, and JavaScript execution
- `:catalog` — catalog models, source seam, matching, ranking, and application services
- `:library` — metadata-only Library membership and reading status
- `:chapters` — chapter synchronization, canonical grouping, and release contracts
- `:reader` — document validation/loading, release selection, and reading-progress policy
- `:downloads` — offline/cache state, quotas, integrity, and content-resolution policy
- `:storage:room` — Room schema, migrations, and durable capability persistence
- `:storage:files` — atomic app-private chapter blob storage
- `:feature:catalog` — Discover, Home, Search, Story, Library, mapping, and chapter-list presentation
- `:feature:reader` — accessible structured-text Reader presentation

The direct project dependency policy is stored in:

    config/architecture/module-boundaries.json

Every module included by `settings.gradle.kts` must be declared in this policy.
`:core:common` remains independent from Android and Compose APIs. `:plugins:api`
remains independent from Android and filesystem APIs. Test fixtures cannot leak
into production dependencies.

See `docs/contributing/adding-a-module.md` before adding a module.
See `docs/ui/design-system.md` for theme, token, shared-state, feedback, and confirmation rules.

## Dependency updates

Third-party versions are centralized in `gradle/libs.versions.toml`.

When dependencies intentionally change, regenerate SHA-256 verification
metadata with the complete verification task set, review the resulting diff,
and commit `gradle/verification-metadata.xml`.
