package app.openstory.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

internal const val HIKARI_PACKAGE = "app.openstory"
internal const val BENCHMARK_FIXTURE_STORY_CARD_TAG = "library-story-benchmark-fixture-story"
internal const val READER_NEXT_ACTION_COUNT = 10
private const val BENCHMARK_FIXTURE_COMPONENT =
    "app.openstory/app.openstory.benchmark.BenchmarkFixtureActivity"
private const val BENCHMARK_FIXTURE_READY_TEXT = "HIKARI_BENCHMARK_READY"
private const val BENCHMARK_SEARCH_QUERY = "hikari deterministic search"
private const val BENCHMARK_SEARCH_RESULT_TITLE = "Hikari Deterministic Search Result"
private const val SEARCH_INPUT_DESCRIPTION = "Search stories"
private const val DISABLE_BACKDROP_EXTRA = "app.openstory.benchmark.DISABLE_BACKDROP"
private const val DISABLE_SURFACE_SHADOWS_EXTRA = "app.openstory.benchmark.DISABLE_SURFACE_SHADOWS"
private const val LEGACY_NAVIGATION_TRANSITIONS_EXTRA =
    "app.openstory.benchmark.LEGACY_NAVIGATION_TRANSITIONS"
private const val UI_TIMEOUT_MILLIS = 10_000L
private const val FIXTURE_TIMEOUT_MILLIS = 300_000L
private const val SWIPE_EDGE_DIVISOR = 5
private const val SWIPE_STEPS = 20

internal fun prepareBenchmarkFixture(
    profile: BenchmarkFixtureProfileRequest = BenchmarkFixtureProfileRequest.SMALL,
) {
    val device = benchmarkDevice()
    device.executeShellCommand("am force-stop $HIKARI_PACKAGE")
    val launchResult = device.executeShellCommand(
        "am start -W -n $BENCHMARK_FIXTURE_COMPONENT ${profile.shellExtras()}",
    )
    check("Error" !in launchResult && "Exception" !in launchResult) {
        "Benchmark fixture activity failed to launch: $launchResult"
    }
    check(device.wait(Until.hasObject(By.text(BENCHMARK_FIXTURE_READY_TEXT)), FIXTURE_TIMEOUT_MILLIS)) {
        "Benchmark fixture did not become ready."
    }
    device.pressHome()
    device.waitForIdle()
}

private fun BenchmarkFixtureProfileRequest.shellExtras(): String = listOf(
    "app.openstory.benchmark.CATALOG_STORIES" to catalogStories,
    "app.openstory.benchmark.PROGRESS_ROWS" to progressRows,
    "app.openstory.benchmark.REDIRECT_ROWS" to redirectRows,
    "app.openstory.benchmark.LIBRARY_ENTRIES" to libraryEntries,
    "app.openstory.benchmark.EXPLICIT_DOWNLOAD_RECORDS" to explicitDownloadRecords,
    "app.openstory.benchmark.READER_IMAGE_PAGES" to readerImagePages,
    "app.openstory.benchmark.READER_ASSET_METADATA_ROWS" to readerAssetMetadataRows,
    "app.openstory.benchmark.AUTOMATIC_CACHE_ROWS" to automaticCacheRows,
    "app.openstory.benchmark.CHAPTER_COUNT" to chapterCount,
    "app.openstory.benchmark.CHAPTER_PAGE_SIZE" to chapterPageSize,
    "app.openstory.benchmark.METADATA_WIDTH" to metadataWidth,
).joinToString(" ") { (key, value) -> "--ei $key $value" }

internal fun MacrobenchmarkScope.startHikari(
    backdropDisabled: Boolean = false,
    surfaceShadowsDisabled: Boolean = false,
    legacyNavigationTransitions: Boolean = false,
) {
    startActivityAndWait { intent ->
        intent.putExtra(DISABLE_BACKDROP_EXTRA, backdropDisabled)
        intent.putExtra(DISABLE_SURFACE_SHADOWS_EXTRA, surfaceShadowsDisabled)
        intent.putExtra(LEGACY_NAVIGATION_TRANSITIONS_EXTRA, legacyNavigationTransitions)
    }
    benchmarkDevice().waitForIdle()
}

internal fun clickTag(tag: String) {
    val node = benchmarkDevice().wait(Until.findObject(By.res(tag)), UI_TIMEOUT_MILLIS)
    requireNotNull(node) { "Benchmark node was not found: $tag" }
    node.click()
    benchmarkDevice().waitForIdle()
}

internal fun openBenchmarkFixtureStory() {
    clickTag("navigation-library")
    clickTag(BENCHMARK_FIXTURE_STORY_CARD_TAG)
    waitForTag("story-overview-pull-refresh")
}

internal fun waitForTag(tag: String) {
    check(benchmarkDevice().wait(Until.hasObject(By.res(tag)), UI_TIMEOUT_MILLIS)) {
        "Benchmark node was not found: $tag"
    }
}

internal fun waitForDiscoverReady() {
    waitForTag("discover-ready-content")
    waitForTag("discover-popular-pager")
}

internal fun enterBenchmarkSearchQueryAndWaitForResult() {
    val device = benchmarkDevice()
    check(device.wait(Until.hasObject(By.desc(SEARCH_INPUT_DESCRIPTION)), UI_TIMEOUT_MILLIS)) {
        "Benchmark Search semantics were not found."
    }
    val input = device.wait(Until.findObject(By.clazz("android.widget.EditText")), UI_TIMEOUT_MILLIS)
    requireNotNull(input) { "Benchmark Search input was not found." }
    input.click()
    input.text = BENCHMARK_SEARCH_QUERY
    check(device.wait(Until.hasObject(By.text(BENCHMARK_SEARCH_QUERY)), UI_TIMEOUT_MILLIS)) {
        "Benchmark Search query was not entered."
    }
    check(device.wait(Until.gone(By.res("search-progress")), UI_TIMEOUT_MILLIS)) {
        "Benchmark Search did not leave the pending state."
    }
    check(device.wait(Until.hasObject(By.descContains(BENCHMARK_SEARCH_RESULT_TITLE)), UI_TIMEOUT_MILLIS)) {
        val visibleContent = device.findObjects(By.pkg(HIKARI_PACKAGE))
            .flatMap { node -> listOfNotNull(node.text, node.contentDescription) }
            .distinct()
        "Benchmark Search result was not found. Visible content: $visibleContent"
    }
}

internal fun pressBackAndWait() {
    benchmarkDevice().pressBack()
    benchmarkDevice().waitForIdle()
}

internal fun swipeUpOnTag(tag: String, repetitions: Int = 1) {
    require(repetitions > 0)
    val device = benchmarkDevice()
    repeat(repetitions) {
        val node = device.wait(Until.findObject(By.res(tag)), UI_TIMEOUT_MILLIS)
        requireNotNull(node) { "Benchmark node was not found: $tag" }
        val bounds = node.visibleBounds
        val inset = (bounds.height() / SWIPE_EDGE_DIVISOR).coerceAtLeast(1)
        val startY = bounds.bottom - inset
        val endY = bounds.top + inset
        check(startY > endY) { "Benchmark node is too small to swipe: $tag $bounds" }
        device.swipe(bounds.centerX(), startY, bounds.centerX(), endY, SWIPE_STEPS)
        device.waitForIdle()
    }
}

private fun benchmarkDevice(): UiDevice = UiDevice.getInstance(
    InstrumentationRegistry.getInstrumentation(),
)
