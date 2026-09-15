# Hikari

Hikari is the Android application repository for the OpenStory local-first
novel library. The Android package namespace and application ID are
`app.openstory`.

## Current repository status

Hikari keeps current implementation state and next-work status in canonical documents instead of
duplicating a fast-moving execution ledger in this README.

- See `docs/project/current-state.md` for what is implemented now.
- See the `Current position` section of `docs/implementation/current-roadmap.md` for the active
  engineering program, resume boundary, and owning checkpoint/plan.
- Agentic work should start from `AGENTS.md` so bounded tasks do not recursively load historical docs.

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

The fast gate runs the current repository/static contracts, application identity and module
architecture verification, build-logic tests, the live Step 3 fast module aggregate, Detekt, and
strict dependency verification. Historical Step 2 freeze scripts are not wildcard-discovered as
current law. The static stage does not launch Gradle; the entrypoint performs one top-level Gradle
invocation for the Gradle-backed work.

Before closing a task or checkpoint, run the canonical full host gate:

Linux, macOS, and Git Bash:

    ./scripts/verify.sh

Windows PowerShell with Git Bash installed:

    & "C:\Program Files\Git\bin\bash.exe" ./scripts/verify.sh

The full gate uses the live Step 3 full module aggregate. Architecture verification remains part of
the same Gradle invocation as the rest of the full Gradle workload, avoiding a second Gradle startup.
Gradle configuration cache, parallel execution, and local build cache are enabled where the build
permits them.

CI executes the full `scripts/verify.sh` command.

## Task and checkpoint verification

Connected-device, migration, screenshot, benchmark, profile, and other acceptance gates are owned by
the active task/checkpoint rather than by a permanent Wave 01 command list in this README. Read
`docs/implementation/current-roadmap.md` to resolve the active task, then follow its named owning plan
and checkpoint for exact commands and device/API requirements.

Repository-wide host verification remains:

    ./scripts/verify-fast.sh
    ./scripts/verify.sh

On Windows PowerShell, run those scripts through Git Bash as shown above. Do not infer a device or
performance PASS from host implementation presence.

## Current module graph authority

The exact included modules are defined by `settings.gradle.kts`; exact direct project dependency and
forbidden-import policy is defined by:

    config/architecture/module-boundaries.json

`docs/project/current-state.md` describes the responsibilities and live/retained/quarantined status
of that graph. This README intentionally does not duplicate a fast-moving module list.

`:core:common` remains independent from Android APIs, `:plugins:api` remains a retained pure-JVM
protocol boundary, and test-only dependencies must not become production edges. See
`docs/contributing/adding-a-module.md` before adding a module,
`docs/project/file-package-ownership-policy.md` before moving/reorganizing source, and
`docs/ui/design-system.md` for the current shared presentation policy.

## Dependency updates

Third-party versions are centralized in `gradle/libs.versions.toml`.

When dependencies intentionally change, regenerate SHA-256 verification
metadata with the complete verification task set, review the resulting diff,
and commit `gradle/verification-metadata.xml`.
