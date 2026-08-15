package app.openstory.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

internal const val HIKARI_PACKAGE = "app.openstory"
internal const val BENCHMARK_STORY_TITLE_ARGUMENT = "benchmarkStoryTitle"
private const val DISABLE_BACKDROP_EXTRA = "app.openstory.benchmark.DISABLE_BACKDROP"
private const val UI_TIMEOUT_MILLIS = 10_000L

internal fun benchmarkStoryTitle(): String? = InstrumentationRegistry.getArguments()
    .getString(BENCHMARK_STORY_TITLE_ARGUMENT)
    ?.takeIf(String::isNotBlank)

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

internal fun clickText(text: String) {
    val node = benchmarkDevice().wait(Until.findObject(By.text(text)), UI_TIMEOUT_MILLIS)
    requireNotNull(node) { "Benchmark text was not found: $text" }
    node.click()
    benchmarkDevice().waitForIdle()
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

private fun benchmarkDevice(): UiDevice = UiDevice.getInstance(
    InstrumentationRegistry.getInstrumentation(),
)
