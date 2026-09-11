#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$ROOT_DIR"

fail() {
  echo "V2 Step 2 Design System violation: $1" >&2
  exit 1
}

EXPECTED_SOURCES=$(cat <<'EOF'
core/designsystem/src/main/kotlin/app/openstory/designsystem/content/HikariSectionHeader.kt
core/designsystem/src/main/kotlin/app/openstory/designsystem/feedback/HikariInlineFeedback.kt
core/designsystem/src/main/kotlin/app/openstory/designsystem/refresh/HikariPullToRefresh.kt
core/designsystem/src/main/kotlin/app/openstory/designsystem/state/HikariEmptyState.kt
core/designsystem/src/main/kotlin/app/openstory/designsystem/state/HikariErrorState.kt
core/designsystem/src/main/kotlin/app/openstory/designsystem/state/HikariSkeleton.kt
core/designsystem/src/main/kotlin/app/openstory/designsystem/theme/HikariPalette.kt
core/designsystem/src/main/kotlin/app/openstory/designsystem/theme/HikariSpacing.kt
core/designsystem/src/main/kotlin/app/openstory/designsystem/theme/HikariTheme.kt
core/designsystem/src/main/kotlin/app/openstory/designsystem/theme/HikariTypography.kt
EOF
)
ACTUAL_SOURCES=$(find core/designsystem/src/main -type f -name '*.kt' | sort)
[[ "$ACTUAL_SOURCES" == "$EXPECTED_SOURCES" ]] || fail "production source/API budget changed"

if grep -E -i -n \
  'project\s*\(|coil|okhttp|java\.net|androidx[.-]room|androidx[.-]work|javascriptengine|backdrop|blur|glass|roborazzi|robolectric' \
  core/designsystem/build.gradle.kts; then
  fail "forbidden dependency entered core/designsystem"
fi

if grep -R -E -n --include='*.kt' \
  'LaunchedEffect|DisposableEffect|SideEffect|produceState|rememberCoroutineScope|CoroutineScope|\.launch\s*\(|\.async\s*\(|StateFlow|SharedFlow|collectAsState|LocalContext|LocalLifecycleOwner|LocalViewModelStoreOwner|CompositionLocalProvider|staticCompositionLocalOf|compositionLocalOf|remember\s*\(|rememberSaveable|rememberUpdatedState|derivedStateOf|mutableStateOf|mutableStateListOf|mutableStateMapOf|LazyColumn|LazyRow|LazyVerticalGrid|LazyHorizontalGrid|verticalScroll|horizontalScroll|rememberScrollState|LazyListState|ScrollState|HorizontalPager|VerticalPager|fillMaxSize|rememberInfiniteTransition|infiniteRepeatable|Animatable|AnimatedVisibility|Crossfade|animate[A-Za-z]*AsState|shimmer|Brush\.|Shader|RenderEffect|graphicsLayer|drawWithCache|Modifier\.shadow\s*\(|dropShadow|googlefonts|GoogleFont|ResourcesCompat' \
  core/designsystem/src/main; then
  fail "hidden work/state/scroll/effect surface entered core/designsystem"
fi

if grep -R -E -n --include='*.kt' \
  '^\s*import\s+(app\.openstory\.(catalog|plugins)\.|androidx\.(lifecycle|room|work|javascriptengine)\.|coil\.|okhttp3\.|java\.net\.|kotlinx\.coroutines\.)' \
  core/designsystem/src/main; then
  fail "forbidden ownership import entered core/designsystem"
fi

APP_IMPORTS=$(grep -R -h -E '^\s*import\s+app\.openstory\.designsystem\.' app/src/main || true)
[[ "$APP_IMPORTS" == "import app.openstory.designsystem.theme.HikariTheme" ]] || \
  fail "app may import only designsystem.theme.HikariTheme"

ENTRY_POINT='feature/catalog/src/main/kotlin/app/openstory/catalog/feature/CatalogEntryPoint.kt'
if grep -E -n 'HikariTheme|MaterialTheme' "$ENTRY_POINT"; then
  fail "CatalogEntryPoint must not install a nested theme"
fi

DISCOVER_DIR='feature/catalog/src/main/kotlin/app/openstory/catalog/feature/discover'
STORY_SCREEN='feature/catalog/src/main/kotlin/app/openstory/catalog/feature/story/StoryDetailScreen.kt'
if grep -R -E -n --include='*.kt' 'PullToRefreshBox|LinearProgressIndicator|DiscoverMediaOption|mediaOptions|DiscoverViewportRow|viewportRows|flatMap\s*\(' "$DISCOVER_DIR"; then
  fail "Discover bypassed the admitted shared surface or rebuilt flattened rows"
fi
if grep -E -n 'HikariPullToRefresh|PullToRefreshBox' "$STORY_SCREEN"; then
  fail "Story Detail is not pull-refreshable in Step 2"
fi

PULL='core/designsystem/src/main/kotlin/app/openstory/designsystem/refresh/HikariPullToRefresh.kt'
grep -F -q 'if (!enabled)' "$PULL" || fail "pull refresh lacks an explicit disabled branch"
grep -F -q 'Box(modifier = modifier, content = content)' "$PULL" || \
  fail "disabled pull refresh is not a plain caller-sized Box"
if grep -E -n 'fillMaxSize|verticalScroll|horizontalScroll|rememberScrollState|LazyColumn|LazyRow|rememberPullToRefreshState' "$PULL"; then
  fail "pull refresh owns sizing, scroll, or remembered gesture state"
fi

THEME='core/designsystem/src/main/kotlin/app/openstory/designsystem/theme/HikariTheme.kt'
if sed -n '/fun HikariTheme/,$p' "$THEME" | grep -E -n 'lightColorScheme|darkColorScheme|Typography\s*\(|Shapes\s*\('; then
  fail "theme token graph is constructed inside composition"
fi

for forbidden_api in \
  HikariDimensions HikariSemanticShapes HikariText HikariRow HikariColumn HikariBox \
  HikariArtwork HikariNavigationHost HikariStateContent; do
  if grep -R -F -n --include='*.kt' "$forbidden_api" core/designsystem/src/main; then
    fail "unreviewed public API found: $forbidden_api"
  fi
done

for symbol in \
  HikariTheme hikariSpacing HikariSectionHeader \
  HikariSkeleton HikariEmptyState HikariErrorState HikariInlineFeedback HikariPullToRefresh; do
  if ! grep -R -q -F --include='*.kt' "$symbol" app/src/main feature/catalog/src/main; then
    fail "admitted public symbol has no production caller: $symbol"
  fi
done

if grep -E -n 'hikariDimensions|hikariShapes|HikariSemanticShapes|hikariSurfaceShadow|HikariBackdrop|blanket.*local.*dp|Story.*pull-to-refresh|Chapters.*pull-to-refresh' docs/ui/design-system.md; then
  fail "active design-system policy still contains V1-only requirements"
fi

echo "V2 Step 2 Design System slice verified."
