# Hikari

Hikari is the Android application shell for the OpenStory local-first novel
library.

## Requirements

- JDK 17
- Android SDK Platform 37
- SDK Build-Tools 36.0.0
- Git
- Git Bash on Windows, or Bash on Linux/macOS

The Gradle Wrapper must be used for every build. Its distribution checksum and
dependency verification metadata are committed to the repository.

## Android SDK setup

Install the required Android SDK packages through Android Studio's SDK Manager,
or with `sdkmanager`:

    sdkmanager "platforms;android-37" "build-tools;36.0.0"

The equivalent package identifiers are:

- `platforms;android-37`
- `build-tools;36.0.0`

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

## Verify

Linux, macOS, and Git Bash:

    ./scripts/verify.sh

Windows PowerShell with Git Bash installed:

    & "C:\Program Files\Git\bin\bash.exe" ./scripts/verify.sh

The shared verification command runs:

- module dependency boundary checks;
- build-logic tests;
- JVM tests across all modules;
- Android unit tests across all Android modules;
- Android Lint across all Android modules;
- Detekt;
- strict Gradle dependency verification;
- debug APK assembly.

CI executes the same `scripts/verify.sh` command.

## Module boundaries

The Wave 01 bootstrap modules are:

- `:app`
- `:core:common`
- `:core:model`
- `:test:fixtures`

`core:model` must remain independent from Android and Compose APIs.

`test:fixtures` may depend only on `core:common` and `core:model`.

## Dependency updates

Third-party versions are centralized in `gradle/libs.versions.toml`.

When dependencies intentionally change, regenerate SHA-256 verification
metadata with the complete verification task set, review the resulting diff,
and commit `gradle/verification-metadata.xml`.
