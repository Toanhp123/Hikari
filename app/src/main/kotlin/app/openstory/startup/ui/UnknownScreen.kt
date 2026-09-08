package app.openstory.startup.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
internal fun UnknownScreen() {
    Text(
        text = "Hikari",
        modifier = Modifier.testTag("startup-unknown"),
    )
}
