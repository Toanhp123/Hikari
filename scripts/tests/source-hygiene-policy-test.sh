#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

fail() { echo "Source hygiene policy violation: $1" >&2; exit 1; }

grep -qx '/.kotlin/' "$root/.gitignore" || fail "/.kotlin/ must be ignored"

mapping="$root/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/mapping/MappingSheet.kt"
nav_state="$root/app/src/main/kotlin/app/openstory/navigation/AppNavigationState.kt"
reader_store="$root/reader/src/main/kotlin/app/openstory/reader/content/ReaderDocumentStore.kt"
http_capability="$root/plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/capabilities/http/PluginHttpCapability.kt"
discover_state="$root/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverUiState.kt"
nav_test="$root/app/src/androidTest/kotlin/app/openstory/navigation/AppNavigationTest.kt"
catalog_match_result="$root/catalog/src/main/kotlin/app/openstory/catalog/matching/MatchResult.kt"
catalog_matcher="$root/catalog/src/main/kotlin/app/openstory/catalog/matching/StoryMatcher.kt"
content_matcher="$root/library/src/main/kotlin/app/openstory/library/matching/ContentStoryMatcher.kt"
app_route="$root/app/src/main/kotlin/app/openstory/navigation/AppRoute.kt"
cache_models="$root/downloads/src/main/kotlin/app/openstory/downloads/cache/CacheModels.kt"
reader_screen="$root/feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderScreen.kt"
p5_checkpoint="$root/docs/internal/checkpoints/performance-wave-p5.md"
baseline_profile="$root/app/src/release/generated/baselineProfiles/baseline-prof.txt"
startup_profile="$root/app/src/release/generated/baselineProfiles/startup-prof.txt"
plugin_packager="$root/build-logic/src/main/kotlin/app/openstory/build/packaging/CanonicalPluginPackageTask.kt"
app_build="$root/app/build.gradle.kts"

! grep -Eq '^fun MappingSheet\(' "$mapping" || fail "retired standalone MappingSheet wrapper is still in production"
grep -q '^fun LazyListScope.mappingItems(' "$mapping" || fail "production mapping lazy-item API is missing"

! grep -Eq '^[[:space:]]*val stacksInUse:' "$nav_state" || fail "retired aggregate top-level stack path is still present"
! grep -Eq '^fun AppNavigationState[.]decoratedEntries\(' "$nav_state" || fail "retired aggregate decoratedEntries path is still present"
grep -q 'PersistentTopLevelNavDisplay(' "$nav_test" || fail "navigation instrumentation does not exercise the shipping persistent host"
! grep -q 'navigationState[.]decoratedEntries(provider)' "$nav_test" || fail "navigation instrumentation still uses the retired aggregate path"

! grep -q '^object NoOpReaderDocumentStore' "$reader_store" || fail "test-only no-op ReaderDocumentStore remains in production"
! grep -Eq '^[[:space:]]*internal fun validateTarget\(' "$http_capability" || fail "test-only HTTP validation seam remains in production"
! grep -Eq '^[[:space:]]*val selectedCatalog:' "$discover_state" || fail "test-only Discover selectedCatalog convenience property remains in production"

! grep -q 'CatalogMatchExplanation' "$catalog_match_result" || fail "unused catalog match explanation payload remains in production"
! grep -q 'matchedTitle' "$catalog_matcher" || fail "catalog matcher still computes an unused matched title"
! grep -q 'matchedNormalizedTitle' "$catalog_matcher" || fail "catalog matcher still retains a dead title tie-break winner"
! grep -q 'winsTieBreak' "$catalog_matcher" || fail "catalog matcher still evaluates a dead title tie-break"
! grep -q 'hasTitle' "$catalog_matcher" || fail "catalog matcher still tracks redundant title-presence state"
! grep -Eq 'val display: String' "$catalog_matcher" || fail "prepared catalog titles still retain unused display text"

! grep -q 'ContentTitleEvidence' "$content_matcher" || fail "content matcher still allocates a title-evidence wrapper"
! grep -q 'TitleEvidence' "$content_matcher" || fail "content matcher still allocates internal title-pair evidence"
grep -q 'bestTitleSimilarity' "$content_matcher" || fail "content matcher is missing allocation-free best-title similarity scan"
! grep -q 'bestTitleEvidence' "$content_matcher" || fail "content matcher still uses the retired title-evidence sorter"
! grep -Eq '^[[:space:]]*val reasons:' "$content_matcher" || fail "content matcher still builds unused reason strings"
! grep -Eq 'val (canonicalTitle|candidateTitle):' "$content_matcher" || fail "content matcher still retains unused title display payload"

grep -q 'data object Discover : AppRoute' "$app_route" || fail "Discover route is missing"
grep -q 'data object Settings : AppRoute' "$app_route" || fail "Wave 10 Settings route is missing"
! grep -q 'data object Plugins : AppRoute' "$app_route" || fail "future-only Plugins route placeholder remains"

! grep -q 'protectedBytesExceedQuota' "$cache_models" || fail "test-only cache eviction convenience property remains in production"
! grep -q 'ReaderProgressNavigation' "$reader_screen" || fail "single-hop Reader progress navigation wrapper remains in production"
! grep -q 'UNREACHABLE_CODE' "$http_capability" || fail "HTTP redirect flow still relies on unreachable-code suppression"

[[ -f "$plugin_packager" ]] || fail "canonical bundled-plugin packager task is missing from build logic"
grep -q 'tasks.registering(CanonicalPluginPackageTask::class)' "$app_build" ||
  fail "MangaDex packaging must use the configuration-cache-safe custom task"
! grep -q 'val mangaDexPluginSources = listOf' "$app_build" ||
  fail "MangaDex packaging still captures build-script source objects"

! grep -q 'skips `HikariBackdropHost` entirely' "$p5_checkpoint" || fail "P5 checkpoint still describes the retired focused-route backdrop branch"

for profile in "$baseline_profile" "$startup_profile"; do
  ! grep -Eq 'ReaderProgressNavigation|CatalogMatchExplanation|ContentTitleEvidence|TitleEvidence|bestTitleEvidence|CatalogDetailsService|AppRoute[$]Plugins' "$profile" ||
    fail "generated profile still references retired source symbols: $profile"

  stale_reader_profile_descriptors=(
    'InstalledPlugin;-><init>(Ljava/lang/String;Ljava/lang/String;Ljava/util/Set;Ljava/util/Set;)V'
    'ChapterListViewModel;-><init>(Lapp/openstory/catalog/ui/chapters/ChapterListAssistedArgs;Lapp/openstory/chapters/repository/ChapterRepository;)V'
    'ChapterReleaseUiModel;-><init>(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Long;)V'
    'ReaderDownloadContentSource;-><init>(Lapp/openstory/chapters/repository/ChapterReleaseLookup;Lapp/openstory/reader/content/ReaderDocumentSourceRegistry;)V'
    'PluginCapabilities;-><init>(ILapp/openstory/plugins/api/manifest/NetworkCapability;Lkotlinx/serialization/internal/SerializationConstructorMarker;)V'
    'ReaderContent(Lapp/openstory/reader/document/ReaderDocument;FLjava/lang/String;ILandroidx/compose/foundation/layout/PaddingValues;'
  )
  for descriptor in "${stale_reader_profile_descriptors[@]}"; do
    ! grep -Fq "$descriptor" "$profile" ||
      fail "generated profile still contains a stale Reader/image ABI descriptor: $profile"
  done
done

echo "Source hygiene policy verified."
