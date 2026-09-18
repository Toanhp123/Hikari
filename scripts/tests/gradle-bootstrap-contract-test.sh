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

BENCHMARK_SOURCE="$ROOT/benchmark/src/main/kotlin/app/universalmedia/benchmark/StartupBenchmark.kt"
[[ -f "$BENCHMARK_SOURCE" ]] || fail "startup benchmark source is missing"
! grep -Fq 'measureStartup' "$BENCHMARK_SOURCE" || fail "Benchmark 1.5.0 startup benchmark must use MacrobenchmarkRule.measureRepeated, not removed measureStartup helper"
grep -Fq 'benchmarkRule.measureRepeated(' "$BENCHMARK_SOURCE" || fail "startup benchmark must use MacrobenchmarkRule.measureRepeated"
grep -Fq 'startActivityAndWait()' "$BENCHMARK_SOURCE" || fail "startup benchmark must explicitly launch the measured app"

echo "gradle-bootstrap-contract-test: PASS"
