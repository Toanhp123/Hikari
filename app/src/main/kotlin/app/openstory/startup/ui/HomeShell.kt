package app.openstory.startup.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.openstory.startup.TRACE_DESTINATION_READY
import app.openstory.startup.startupTraceMark

@Composable
internal fun HomeShell() {
    LaunchedEffect(Unit) {
        startupTraceMark(TRACE_DESTINATION_READY)
    }

    Text(
        text = "Hikari",
        modifier = Modifier.testTag("startup-home"),
    )
}
