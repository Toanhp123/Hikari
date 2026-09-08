package app.openstory.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

internal const val HIKARI_PACKAGE = "app.openstory.v2benchmark"
internal const val FIRST_RUN_TAG = "startup-first-run"
internal const val HOME_TAG = "startup-home"

internal val benchmarkCompilationMode = CompilationMode.Partial(
    baselineProfileMode = BaselineProfileMode.Require,
)

private const val FIXTURE_COMPONENT =
    "app.openstory.v2benchmark/app.openstory.benchmark.BenchmarkLaunchStateActivity"
private const val FIXTURE_READY_TEXT = "HIKARI_V2_BENCHMARK_READY"
private const val UI_TIMEOUT_MILLIS = 10_000L
private const val FIXTURE_TIMEOUT_MILLIS = 30_000L

internal fun prepareFreshInstall() {
    val device = benchmarkDevice()
    device.executeShellCommand("am force-stop $HIKARI_PACKAGE")
    val clearResult = device.executeShellCommand("pm clear $HIKARI_PACKAGE")
    check("Success" in clearResult) {
        "Unable to clear V2 benchmark package data: $clearResult"
    }
    device.waitForIdle()
}

internal fun prepareReturningLaunch() {
    prepareFreshInstall()
    val device = benchmarkDevice()
    val launchResult = device.executeShellCommand(
        "am start -W -n $FIXTURE_COMPONENT",
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
    device.pressHome()
    device.waitForIdle()
    device.executeShellCommand("am force-stop $HIKARI_PACKAGE")
}

internal fun MacrobenchmarkScope.startHikariAndWait(tag: String) {
    startActivityAndWait()
    check(
        benchmarkDevice().wait(Until.hasObject(By.res(tag)), UI_TIMEOUT_MILLIS),
    ) {
        "V2 startup destination was not found: $tag"
    }
}

private fun benchmarkDevice(): UiDevice = UiDevice.getInstance(
    InstrumentationRegistry.getInstrumentation(),
)
