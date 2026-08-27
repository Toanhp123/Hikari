#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
reader_sanitizer="$root/reader/src/main/kotlin/app/openstory/reader/document/ReaderDocumentSanitizer.kt"
reader_executor="$root/reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteExecutor.kt"
reader_remote_source_resolver="$root/reader/src/main/kotlin/app/openstory/reader/routing/ReaderRemoteSourceResolver.kt"
chapter_engine="$root/chapters/src/main/kotlin/app/openstory/chapters/aggregation/ChapterAggregationEngine.kt"
fixture_manifest="$root/app/src/benchmarkRelease/AndroidManifest.xml"
fixture_activity="$root/app/src/benchmarkRelease/kotlin/app/openstory/benchmark/BenchmarkFixtureActivity.kt"
benchmark_driver="$root/benchmark/src/main/kotlin/app/openstory/benchmark/HikariBenchmarkDriver.kt"
macrobenchmark="$root/benchmark/src/main/kotlin/app/openstory/benchmark/HikariMacrobenchmark.kt"
baseline_profile="$root/benchmark/src/main/kotlin/app/openstory/benchmark/BaselineProfileGenerator.kt"
application="$root/app/src/main/kotlin/app/openstory/OpenStoryApplication.kt"
app_build="$root/app/build.gradle.kts"

fail() { echo "Performance Wave P4 policy violation: $1" >&2; exit 1; }

! grep -q 'canonicalText(' "$reader_sanitizer" || fail "reader fingerprint still builds a whole canonical document string"
grep -q 'MessageDigest.getInstance("SHA-256")' "$reader_sanitizer" || fail "reader fingerprint is not streamed through SHA-256"
grep -q 'toLowerHex()' "$reader_sanitizer" || fail "reader SHA-256 hex encoding still uses formatter allocation"
! grep -q 'sortedWith(compareByDescending<Pair<CanonicalChapter, ChapterMatchScore>>' "$chapter_engine" || \
  fail "chapter candidate selection still sorts every candidate"
grep -q 'bestCandidate(' "$chapter_engine" || fail "chapter aggregation lacks a single-pass best-candidate path"
[[ -f "$reader_remote_source_resolver" ]] || fail "reader remote source resolver is missing"
grep -q 'ReaderRemoteSourceResolver(::loadFromSources)' "$reader_executor" || \
  fail "reader executor does not defer source enumeration through the per-execution resolver"
grep -q 'val remoteSources = newRemoteSourceResolver()' "$reader_executor" || \
  fail "reader adaptive execution does not allocate one lazy resolver per execution"
grep -q 'val sourceByPlugin = remoteSources.resolve()' "$reader_executor" || \
  fail "reader remote attempt does not resolve sources lazily at the REMOTE boundary"
grep -q 'private var resolved: Map<PluginId, ReaderDocumentSource>? = null' "$reader_remote_source_resolver" || \
  fail "reader remote source resolver does not cache the first source enumeration"
local_attempt=$(sed -n '/private suspend fun executeLocalAttempt(/,/private suspend fun executeRemoteAttempt(/p' "$reader_executor")
[[ "$local_attempt" != *"remoteSources.resolve()"* ]] || \
  fail "reader LOCAL attempt resolves remote sources"
[[ "$local_attempt" != *"sources.enabled()"* ]] || \
  fail "reader LOCAL attempt enumerates plugin sources"

[[ -f "$fixture_manifest" ]] || fail "benchmarkRelease fixture manifest is missing"
[[ -f "$fixture_activity" ]] || fail "benchmarkRelease fixture activity is missing"
grep -q 'androidComponents' "$app_build" || fail "app build does not restore benchmark fixture sources after Baseline Profile source copying"
grep -q 'finalizeDsl' "$app_build" || fail "benchmark fixture sources are not restored during final DSL configuration"
grep -q '"benchmarkRelease", "nonMinifiedRelease"' "$app_build" || \
  fail "benchmark fixture is not attached to both Macrobenchmark and Baseline Profile target variants"
grep -q 'kotlin.directories.add("src/benchmarkRelease/kotlin")' "$app_build" || \
  fail "benchmark fixture Kotlin source is not explicitly reattached with the current AGP API"
grep -q 'src/benchmarkRelease/AndroidManifest.xml' "$app_build" || fail "benchmark fixture manifest is not explicitly reattached"
grep -q 'BenchmarkFixtureActivity' "$fixture_manifest" || fail "fixture activity is not declared in benchmarkRelease"
! grep -q 'BenchmarkFixtureActivity' "$root/app/src/main/AndroidManifest.xml" || \
  fail "benchmark fixture leaked into the production manifest"
grep -q 'android:noHistory="true"' "$fixture_manifest" || fail "fixture activity may remain in the task back stack"
grep -q 'Hikari Benchmark Fixture' "$fixture_activity" || fail "deterministic benchmark story title is missing"
grep -q 'BENCHMARK_CHAPTER_COUNT = 12' "$fixture_activity" || fail "benchmark fixture does not seed 12 chapters"
grep -q 'BENCHMARK_RESUME_CHAPTER_INDEX = 1' "$fixture_activity" || \
  fail "benchmark fixture does not pin the Reader resume journey to chapter 1"
grep -q 'fixture.index == BENCHMARK_RESUME_CHAPTER_INDEX' "$fixture_activity" || \
  fail "benchmark fixture does not seed a deterministic incomplete resume chapter"
grep -q 'ReaderDocumentStore' "$fixture_activity" || fail "benchmark fixture does not preseed Reader documents"
grep -q 'ReadingProgressRepository' "$fixture_activity" || fail "benchmark fixture does not preseed Reader progress"
grep -q 'documents.read(fixture.release.id, document.fingerprint) == document' "$fixture_activity" || fail "benchmark fixture does not verify cached Reader persistence"
grep -q 'completedAtEpochMillis = if (fixture.index == BENCHMARK_RESUME_CHAPTER_INDEX)' "$fixture_activity" || \
  fail "benchmark fixture progress no longer isolates the deterministic resume chapter"
grep -q 'BENCHMARK_CHAPTER_COUNT - fixture.index' "$fixture_activity" || fail "benchmark fixture no longer makes chapter one the deterministic resume target"
grep -q 'prepareBenchmarkFixture' "$benchmark_driver" || fail "benchmark driver cannot prepare deterministic fixture"
grep -q 'prepareBenchmarkFixture()' "$macrobenchmark" || fail "macrobenchmark story/reader journey does not prepare fixture"
grep -q 'prepareBenchmarkFixture()' "$baseline_profile" || fail "baseline profile critical journey does not prepare fixture"
grep -q 'BENCHMARK_FIXTURE_STORY_CARD_TAG = "library-story-benchmark-fixture-story"' "$benchmark_driver" || \
  fail "benchmark Story navigation does not target the deterministic fixture card"
! grep -q 'benchmarkStoryTitle' "$benchmark_driver" || fail "benchmark driver still depends on title-based Story lookup"
! grep -q 'clickText(' "$macrobenchmark" || fail "macrobenchmark Story navigation still clicks child text instead of the fixture card"
! grep -q 'clickText(' "$baseline_profile" || fail "baseline profile Story navigation still clicks child text instead of the fixture card"

# Frame timing stays isolated from memory measurement.
grep -q 'FrameTimingMetric()' "$macrobenchmark" || fail "frame timing benchmark is missing"
grep -q 'StartupTimingMetric()' "$macrobenchmark" || fail "cold startup timing benchmark is missing"
grep -q 'StartupMode.COLD' "$macrobenchmark" || fail "startup benchmark is not cold-start"
grep -q 'MemoryUsageMetric(MemoryUsageMetric.Mode.Max)' "$macrobenchmark" || fail "navigation max-memory benchmark is missing"
frame_helper=$(awk '/private fun measureNavigation\(/,/^    }/' "$macrobenchmark")
[[ "$frame_helper" != *"MemoryUsageMetric"* ]] || fail "memory metric was mixed into frame timing helper"

# Wave 10 intentionally owns three bounded startup hooks. Keep them exact and reject
# unrelated blocking/heavy startup work rather than banning Application.onCreate itself.
grep -q 'NotificationChannelConfig.create(this)' "$application" || fail "notification channel startup hook missing"
grep -q 'backgroundPolicyCoordinator.start()' "$application" || fail "background policy startup hook missing"
grep -q 'notificationDrainScheduler.ensureRecoveryWork()' "$application" || fail "notification recovery startup hook missing"
! grep -Eq 'runBlocking|Thread[.]sleep|database[.]|snapshot\(' "$application" || \
  fail "blocking/heavy startup work introduced"

echo "Performance Wave P4 policy verified."
