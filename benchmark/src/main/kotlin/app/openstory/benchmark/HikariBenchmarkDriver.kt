package app.openstory.benchmark

import android.graphics.Rect
import android.os.SystemClock
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

internal const val HIKARI_PACKAGE = "app.openstory.v2benchmark"
internal const val FIRST_RUN_TAG = "startup-first-run"
internal const val DISCOVER_TAG = "catalog-discover"
internal const val STORY_TAG = "story-root"

internal val benchmarkCompilationMode = CompilationMode.Partial(
    baselineProfileMode = BaselineProfileMode.Require,
)

private const val FIXTURE_COMPONENT =
    "app.openstory.v2benchmark/app.openstory.benchmark.BenchmarkLaunchStateActivity"
private const val FIXTURE_READY_TEXT = "HIKARI_V2_BENCHMARK_READY"
private const val FIXTURE_PREPARATION_EXTRA = "catalog-preparation"
private const val DIAGNOSTICS_URI = "content://$HIKARI_PACKAGE.benchmark-diagnostics/snapshot"
private const val UI_TIMEOUT_MILLIS = 10_000L
private const val FIXTURE_TIMEOUT_MILLIS = 30_000L
private const val FIRST_STORY_DESCRIPTION = "Aster Gate"
private const val FINAL_SECTION_TAG = "discover-final-top-rated-row"
private const val PAGE_IDENTITY_TAG = "discover-page-identity"
private const val MAX_SCROLL_ATTEMPTS = 12
private const val SWIPE_EDGE_DIVISOR = 5
private const val SWIPE_STEPS = 20

internal enum class BenchmarkPreparation(val wireValue: String) {
    NORMAL("normal"),
    PERSISTED_EMPTY("persisted-empty"),
    REPEATED_REFRESH("repeated-refresh"),
    DISK_HIT("disk-hit"),
    AGED_STORAGE("aged-storage"),
    OVERSIZED_DETAIL("oversized-detail"),
    ORPHAN_OVERFLOW("orphan-overflow"),
    PIN_PRUNE_RACE("pin-prune-race"),
    PATHOLOGICAL_IMAGE_BOUNDS("pathological-image-bounds"),
}

internal fun prepareFreshInstall() {
    val device = benchmarkDevice()
    device.executeShellCommand("am force-stop $HIKARI_PACKAGE")
    val clearResult = device.executeShellCommand("pm clear $HIKARI_PACKAGE")
    check("Success" in clearResult) {
        "Unable to clear V2 benchmark package data: $clearResult"
    }
    device.waitForIdle()
}

internal fun prepareReturningLaunch(
    preparation: BenchmarkPreparation = BenchmarkPreparation.NORMAL,
    clearPackageData: Boolean = true,
) {
    prepareReturningFixture(preparation, clearPackageData)
    val device = benchmarkDevice()
    device.pressHome()
    device.waitForIdle()
    device.executeShellCommand("am force-stop $HIKARI_PACKAGE")
}

internal fun prepareReturningFixture(
    preparation: BenchmarkPreparation,
    clearPackageData: Boolean = true,
) {
    if (clearPackageData) {
        prepareFreshInstall()
    } else {
        benchmarkDevice().executeShellCommand("am force-stop $HIKARI_PACKAGE")
    }
    val device = benchmarkDevice()
    val launchResult = device.executeShellCommand(
        "am start -W -n $FIXTURE_COMPONENT --es $FIXTURE_PREPARATION_EXTRA ${preparation.wireValue}",
    )
    check("Error" !in launchResult && "Exception" !in launchResult) {
        "V2 launch-state fixture failed to launch: $launchResult"
    }
    check(
        device.wait(
            Until.hasObject(By.text(FIXTURE_READY_TEXT)),
            FIXTURE_TIMEOUT_MILLIS,
        ),
    ) {
        "V2 launch-state fixture did not become ready."
    }
}

internal fun MacrobenchmarkScope.startHikariAndWait(tag: String) {
    startActivityAndWait()
    check(
        benchmarkDevice().wait(Until.hasObject(By.res(tag)), UI_TIMEOUT_MILLIS),
    ) {
        "V2 startup destination was not found: $tag"
    }
}

internal fun scrollDiscoverToEndAndBack() {
    val device = benchmarkDevice()
    val root = requireNotNull(device.wait(Until.findObject(By.res(DISCOVER_TAG)), UI_TIMEOUT_MILLIS)) {
        "Discover root was unavailable for scrolling."
    }
    check(
        scrollUntilVisible(
            maxAttempts = MAX_SCROLL_ATTEMPTS,
            isVisible = { device.hasObject(By.res(FINAL_SECTION_TAG)) },
            scroll = { swipeDiscover(device, root, towardEnd = true) },
        ),
    ) { "Discover did not reach the final section." }
    check(
        scrollUntilVisible(
            maxAttempts = MAX_SCROLL_ATTEMPTS,
            isVisible = { device.hasObject(By.res(PAGE_IDENTITY_TAG)) },
            scroll = { swipeDiscover(device, root, towardEnd = false) },
        ),
    ) { "Discover did not return to item 0." }
}

internal fun scrollUntilVisible(
    maxAttempts: Int,
    isVisible: () -> Boolean,
    scroll: () -> Unit,
): Boolean {
    repeat(maxAttempts) {
        if (isVisible()) return true
        scroll()
    }
    return isVisible()
}

private fun swipeDiscover(device: UiDevice, root: UiObject2, towardEnd: Boolean) {
    val swipe = discoverSwipeCoordinates(root.visibleBounds, towardEnd)
    check(swipe.startY != swipe.endY) { "Discover root is too small to swipe: ${root.visibleBounds}" }
    device.swipe(swipe.x, swipe.startY, swipe.x, swipe.endY, SWIPE_STEPS)
    device.waitForIdle()
}

internal fun discoverSwipeCoordinates(bounds: Rect, towardEnd: Boolean): SwipeCoordinates {
    val inset = (bounds.height() / SWIPE_EDGE_DIVISOR).coerceAtLeast(1)
    val upperY = bounds.top + inset
    val lowerY = bounds.bottom - inset
    return if (towardEnd) {
        SwipeCoordinates(bounds.centerX(), lowerY, upperY)
    } else {
        SwipeCoordinates(bounds.centerX(), upperY, lowerY)
    }
}

internal data class SwipeCoordinates(
    val x: Int,
    val startY: Int,
    val endY: Int,
)

internal class FrameFixturePreparationTracker {
    private var prepared = false

    fun shouldClearData(): Boolean {
        if (prepared) return false
        prepared = true
        return true
    }
}

internal fun openFirstStory() {
    val device = benchmarkDevice()
    val story = requireNotNull(
        device.wait(Until.findObject(By.desc(FIRST_STORY_DESCRIPTION)), UI_TIMEOUT_MILLIS),
    ) { "First deterministic Story was unavailable." }
    story.click()
    check(device.wait(Until.hasObject(By.res(STORY_TAG)), UI_TIMEOUT_MILLIS)) {
        "Story destination did not become visible."
    }
}

internal fun backToDiscover() {
    val device = benchmarkDevice()
    device.pressBack()
    check(device.wait(Until.hasObject(By.res(DISCOVER_TAG)), UI_TIMEOUT_MILLIS)) {
        "Discover did not become visible after Story back."
    }
}

internal fun longBrowseCycle() {
    repeat(20) {
        scrollDiscoverToEndAndBack()
        openFirstStory()
        backToDiscover()
    }
}

internal fun benchmarkDiagnostic(name: String): Int {
    val output = benchmarkDevice().executeShellCommand("content query --uri $DIAGNOSTICS_URI")
    return Regex("(?:^|[ ,])$name=([0-9]+)")
        .find(output)
        ?.groupValues
        ?.get(1)
        ?.toInt()
        ?: error("Benchmark diagnostic '$name' missing from: $output")
}

internal fun waitForBenchmarkDiagnostic(name: String, expected: Int) {
    val deadline = SystemClock.uptimeMillis() + UI_TIMEOUT_MILLIS
    while (SystemClock.uptimeMillis() < deadline) {
        if (benchmarkDiagnostic(name) == expected) return
        SystemClock.sleep(DIAGNOSTIC_RETRY_MILLIS)
    }
    error("Benchmark diagnostic '$name' did not reach $expected.")
}

internal fun waitForBenchmarkDiagnosticAtLeast(name: String, minimum: Int) {
    val deadline = SystemClock.uptimeMillis() + UI_TIMEOUT_MILLIS
    while (SystemClock.uptimeMillis() < deadline) {
        if (benchmarkDiagnostic(name) >= minimum) return
        SystemClock.sleep(DIAGNOSTIC_RETRY_MILLIS)
    }
    error("Benchmark diagnostic '$name' did not reach at least $minimum.")
}

internal fun resetTransportDiagnostic() {
    val output = benchmarkDevice().executeShellCommand(
        "content call --uri $DIAGNOSTICS_URI --method reset-transport",
    )
    check("ok=true" in output) { "Unable to reset benchmark transport counter: $output" }
}

internal fun trimHikariDecodedMemory() {
    val output = benchmarkDevice().executeShellCommand(
        "am send-trim-memory $HIKARI_PACKAGE RUNNING_LOW",
    )
    check("Error" !in output && "Exception" !in output) {
        "Unable to trim benchmark decoded-memory cache: $output"
    }
}

internal fun benchmarkDevice(): UiDevice = UiDevice.getInstance(
    InstrumentationRegistry.getInstrumentation(),
)

private const val DIAGNOSTIC_RETRY_MILLIS = 50L
