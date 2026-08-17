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
private const val DISABLE_BACKDROP_EXTRA = "app.openstory.benchmark.DISABLE_BACKDROP"
private const val UI_TIMEOUT_MILLIS = 10_000L
private const val SWIPE_EDGE_DIVISOR = 5
private const val SWIPE_STEPS = 20

internal fun prepareBenchmarkFixture() {
    val device = benchmarkDevice()
    device.executeShellCommand("am force-stop $HIKARI_PACKAGE")
    val launchResult = device.executeShellCommand("am start -W -n $BENCHMARK_FIXTURE_COMPONENT")
    check("Error" !in launchResult && "Exception" !in launchResult) {
        "Benchmark fixture activity failed to launch: $launchResult"
    }
    check(device.wait(Until.hasObject(By.text(BENCHMARK_FIXTURE_READY_TEXT)), UI_TIMEOUT_MILLIS)) {
        "Benchmark fixture did not become ready."
    }
    device.pressHome()
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.startHikari(
    backdropDisabled: Boolean = false,
) {
    startActivityAndWait { intent ->
        intent.putExtra(DISABLE_BACKDROP_EXTRA, backdropDisabled)
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
