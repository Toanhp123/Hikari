package app.openstory.startup

import org.junit.Assert.assertEquals
import org.junit.Test

class StartupDestinationTest {
    @Test
    fun unknownAndFirstRunRemainUnchangedAcrossTheFirstFrameGate() {
        assertEquals(StartupDestination.UNKNOWN, startupDestination(AppLaunchState.Unknown, false))
        assertEquals(StartupDestination.UNKNOWN, startupDestination(AppLaunchState.Unknown, true))
        assertEquals(StartupDestination.FIRST_RUN, startupDestination(AppLaunchState.FirstRun, false))
        assertEquals(StartupDestination.FIRST_RUN, startupDestination(AppLaunchState.FirstRun, true))
    }

    @Test
    fun readyCreatesTheAppShellHomeOnlyAfterTheFirstFrame() {
        assertEquals(StartupDestination.UNKNOWN, startupDestination(AppLaunchState.Ready, false))
        assertEquals(StartupDestination.APP_SHELL_HOME, startupDestination(AppLaunchState.Ready, true))
    }
}
