#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
reader_sanitizer="$root/reader/src/main/kotlin/app/openstory/reader/document/ReaderDocumentSanitizer.kt"
reader_repository="$root/reader/src/main/kotlin/app/openstory/reader/content/ReaderDocumentRepository.kt"
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
grep -q 'loadCached' "$reader_repository" || fail "reader repository does not try cached content first"
grep -q 'loadFromSources' "$reader_repository" || fail "reader source enumeration is not lazy behind a cache miss"

[[ -f "$fixture_manifest" ]] || fail "benchmarkRelease fixture manifest is missing"
[[ -f "$fixture_activity" ]] || fail "benchmarkRelease fixture activity is missing"
grep -q 'androidComponents' "$app_build" || fail "app build does not restore benchmarkRelease fixture sources after Baseline Profile source copying"
grep -q 'finalizeDsl' "$app_build" || fail "benchmarkRelease fixture sources are not restored during final DSL configuration"
grep -q 'src/benchmarkRelease/kotlin' "$app_build" || fail "benchmarkRelease Kotlin fixture source is not explicitly reattached"
grep -q 'src/benchmarkRelease/AndroidManifest.xml' "$app_build" || fail "benchmarkRelease fixture manifest is not explicitly reattached"
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

if grep -q 'override fun onCreate' "$application"; then
  fail "production Application.onCreate work was introduced during final performance wave"
fi

echo "Performance Wave P4 policy verified."
