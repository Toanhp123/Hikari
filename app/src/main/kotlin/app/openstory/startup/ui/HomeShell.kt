package app.openstory.startup.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.openstory.startup.TRACE_DESTINATION_READY
import app.openstory.startup.startupTraceMark

@Composable
internal fun HomeShell() {
    LaunchedEffect(Unit) { startupTraceMark(TRACE_DESTINATION_READY) }

    Box(modifier = Modifier.fillMaxSize().testTag("startup-home"), contentAlignment = Alignment.Center) {
        Text(text = "Hikari")
    }
}
