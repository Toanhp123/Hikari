package app.openstory.designsystem.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class HikariLayoutPolicyTest {
    @Test
    fun screenInsetsSwitchAtTheSharedWideBreakpoint() {
        assertEquals(20.dp, HikariBreakpoints.screenHorizontalInset(599.dp))
        assertEquals(32.dp, HikariBreakpoints.screenHorizontalInset(600.dp))
    }
}
