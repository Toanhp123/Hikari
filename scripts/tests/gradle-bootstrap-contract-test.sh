#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

ATTRS="$ROOT/.gitattributes"
[[ -f "$ATTRS" ]] || fail ".gitattributes is required to make Spotless/Git line endings deterministic"
grep -Eq '^\* text=auto eol=lf$' "$ATTRS" || fail ".gitattributes must pin text files to LF"
grep -Eq '^\*\.bat text eol=crlf$' "$ATTRS" || fail ".gitattributes must keep Windows batch wrappers CRLF"

EDITORCONFIG="$ROOT/.editorconfig"
grep -Eq '^ktlint_function_naming_ignore_when_annotated_with[[:space:]]*=[[:space:]]*Composable[[:space:]]*$' "$EDITORCONFIG" || fail "ktlint must allow PascalCase @Composable function names"

ROOT_BUILD="$ROOT/build.gradle.kts"
[[ "$(grep -c 'lineEndings = LineEnding.UNIX' "$ROOT_BUILD" || true)" -eq 3 ]] || fail "Spotless must pin LF for Kotlin, Gradle Kotlin DSL, and misc formats"
grep -Fq '".idea/**"' "$ROOT_BUILD" || fail "Spotless misc must exclude IDE-local .idea state"
grep -Fq '".gradle/**"' "$ROOT_BUILD" || fail "Spotless misc must exclude Gradle-local .gradle state"
grep -Fq 'id("universalmedia.root-verification")' "$ROOT_BUILD" || fail "root verification convention plugin is not applied"
! grep -Fq 'tasks.register("verifyArchitecture")' "$ROOT_BUILD" || fail "verifyArchitecture must not be a build-script doLast task"
! grep -Fq 'tasks.register("verifySecurityBaseline")' "$ROOT_BUILD" || fail "verifySecurityBaseline must not be a build-script doLast task"

PLUGIN="$ROOT/build-logic/src/main/kotlin/universalmedia.root-verification.gradle.kts"
ARCH_TASK="$ROOT/build-logic/src/main/kotlin/app/universalmedia/buildlogic/VerifyArchitectureTask.kt"
SEC_TASK="$ROOT/build-logic/src/main/kotlin/app/universalmedia/buildlogic/VerifySecurityBaselineTask.kt"
for file in "$PLUGIN" "$ARCH_TASK" "$SEC_TASK"; do
  [[ -f "$file" ]] || fail "missing configuration-cache-safe verification build logic: ${file#"$ROOT"/}"
done

grep -Fq '@TaskAction' "$ARCH_TASK" || fail "VerifyArchitectureTask must use a typed task action"
grep -Fq '@TaskAction' "$SEC_TASK" || fail "VerifySecurityBaselineTask must use a typed task action"
! grep -Eq '(^|[^A-Za-z])project\.|rootProject|subprojects' "$ARCH_TASK" || fail "VerifyArchitectureTask execution type must not capture Project APIs"
! grep -Eq '(^|[^A-Za-z])project\.|rootProject|subprojects' "$SEC_TASK" || fail "VerifySecurityBaselineTask execution type must not capture Project APIs"

VERSION_CATALOG="$ROOT/gradle/libs.versions.toml"
APP_BUILD="$ROOT/app/build.gradle.kts"
grep -Eq '^espresso[[:space:]]*=[[:space:]]*"3\.7\.0"$' "$VERSION_CATALOG" || fail "Espresso must stay on the Android-17-compatible 3.7.0 baseline"
grep -Fq 'androidTestImplementation(libs.androidx.test.espresso.core)' "$APP_BUILD" || fail "app instrumentation must consume the pinned Espresso core instead of relying on an older Compose-test transitive"

# First-slice Task 0 build contract: AGP 9 built-in Kotlin stays KSP-only, and the
# compile API level remains distinct from the installed Android 17 package name.
if grep -R -n -E '(^|[^A-Za-z])(kotlin-kapt|org\.jetbrains\.kotlin\.kapt|kapt\()' \
  --include='*.gradle.kts' "$ROOT" --exclude-dir=.gradle --exclude-dir=build >/dev/null; then
  fail "production Gradle scripts must not apply/use kapt under AGP 9 built-in Kotlin"
fi

ANDROID_APP_PLUGIN="$ROOT/build-logic/src/main/kotlin/universalmedia.android.application.gradle.kts"
ANDROID_LIBRARY_PLUGIN="$ROOT/build-logic/src/main/kotlin/universalmedia.android.library.gradle.kts"
BENCHMARK_BUILD="$ROOT/benchmark/build.gradle.kts"
grep -Fq 'compileSdk = 37' "$ANDROID_APP_PLUGIN" || fail "application convention must keep compileSdk = 37"
grep -Fq 'compileSdk = 37' "$ANDROID_LIBRARY_PLUGIN" || fail "library convention must keep compileSdk = 37"
grep -Fq 'compileSdk = 37' "$BENCHMARK_BUILD" || fail "benchmark module must keep compileSdk = 37"
grep -Fq 'platforms/android-37.0/android.jar' "$ROOT/scripts/verify-bootstrap.sh" || fail "Bash verifier must keep platforms/android-37.0 distinct from compileSdk 37"
grep -Fq 'platforms\android-37.0\android.jar' "$ROOT/scripts/verify-bootstrap.ps1" || fail "PowerShell verifier must keep platforms/android-37.0 distinct from compileSdk 37"

for pin in \
  'room = "2.8.5"' \
  'ksp = "2.3.12"' \
  'work = "2.11.2"' \
  'media3 = "1.11.1"' \
  'coroutines = "1.11.0"' \
  'lifecycle = "2.11.0"'; do
  grep -Fq "$pin" "$VERSION_CATALOG" || fail "missing first-slice dependency pin: $pin"
done

DATA_BUILD="$ROOT/data/build.gradle.kts"
INGESTION_BUILD="$ROOT/ingestion/local/build.gradle.kts"
PLAYBACK_MEDIA3_BUILD="$ROOT/playback/media3/build.gradle.kts"
grep -Fq 'alias(libs.plugins.ksp)' "$DATA_BUILD" || fail "Room data module must apply KSP"
grep -Fq 'alias(libs.plugins.androidx.room)' "$DATA_BUILD" || fail "Room data module must apply the Room Gradle plugin"
grep -Fq 'schemaDirectory("$projectDir/schemas")' "$DATA_BUILD" || fail "Room data module must export schemas to a committed project directory"
grep -Fq 'ksp(libs.androidx.room.compiler)' "$DATA_BUILD" || fail "Room compiler must use KSP"
grep -Fq 'implementation(libs.androidx.room.runtime)' "$DATA_BUILD" || fail "data module must consume Room runtime"
grep -Fq 'implementation(libs.androidx.work.runtime.ktx)' "$INGESTION_BUILD" || fail "ingestion module must consume WorkManager Kotlin/coroutines runtime"
grep -Fq 'implementation(libs.androidx.media3.exoplayer)' "$PLAYBACK_MEDIA3_BUILD" || fail "playback implementation must consume Media3 ExoPlayer"
grep -Fq 'implementation(libs.androidx.media3.session)' "$PLAYBACK_MEDIA3_BUILD" || fail "playback implementation must consume Media3 session"

DAEMON_JVM="$ROOT/gradle/gradle-daemon-jvm.properties"
if [[ -f "$DAEMON_JVM" ]]; then
  grep -Eq '^toolchainVersion=17$' "$DAEMON_JVM" || fail "Gradle daemon JVM criteria must match the JDK 17 bootstrap baseline"
fi

AGENTS="$ROOT/AGENTS.md"
FOUNDATION="$ROOT/docs/foundation/android-universal-media-app-foundation.md"
[[ -f "$AGENTS" ]] || fail "missing root AGENTS.md"
[[ -f "$FOUNDATION" ]] || fail "missing stable-path canonical foundation"
[[ ! -d "$ROOT/docs/state" ]] || fail "docs/state must not become a parallel source of truth"
[[ "$(find "$ROOT/docs/foundation" -maxdepth 1 -type f -name '*.md' | wc -l | tr -d ' ')" -eq 1 ]] || fail "docs/foundation must contain exactly one canonical markdown foundation"
[[ "$(wc -c < "$AGENTS" | tr -d ' ')" -le 8192 ]] || fail "AGENTS.md exceeds the 8 KiB repository instruction budget"
grep -Fq 'docs/foundation/android-universal-media-app-foundation.md' "$AGENTS" || fail "AGENTS.md must route to the canonical foundation"
grep -Fq 'Current Control Block' "$AGENTS" || fail "AGENTS.md must route startup reading to the Current Control Block"
grep -Fq 'Do **not** create revision-suffixed foundation copies' "$AGENTS" || fail "AGENTS.md must forbid parallel foundation revisions"
grep -Fq '# Current Control Block — Read First' "$FOUNDATION" || fail "foundation must expose a compact Current Control Block"
grep -Fq 'sửa **chính file này tại stable path hiện tại**' "$FOUNDATION" || fail "foundation must require in-place canonical updates"

BENCHMARK_SOURCE="$ROOT/benchmark/src/main/kotlin/app/universalmedia/benchmark/StartupBenchmark.kt"
[[ -f "$BENCHMARK_SOURCE" ]] || fail "startup benchmark source is missing"
! grep -Fq 'measureStartup' "$BENCHMARK_SOURCE" || fail "Benchmark 1.5.0 startup benchmark must use MacrobenchmarkRule.measureRepeated, not removed measureStartup helper"
grep -Fq 'benchmarkRule.measureRepeated(' "$BENCHMARK_SOURCE" || fail "startup benchmark must use MacrobenchmarkRule.measureRepeated"
grep -Fq 'startActivityAndWait()' "$BENCHMARK_SOURCE" || fail "startup benchmark must explicitly launch the measured app"

echo "gradle-bootstrap-contract-test: PASS"
