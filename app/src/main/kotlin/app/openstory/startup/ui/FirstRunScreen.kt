package app.openstory.startup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun FirstRunScreen(
    isSaving: Boolean,
    saveFailed: Boolean,
    onComplete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("startup-first-run"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Hikari")
        Button(
            onClick = onComplete,
            enabled = !isSaving,
            modifier = Modifier
                .padding(top = 24.dp)
                .testTag("startup-complete"),
        ) {
            Text(text = if (isSaving) "Saving" else "Continue")
        }
        if (saveFailed) {
            Text(
                text = "Could not save. Try again.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .testTag("startup-completion-error"),
            )
        }
    }
}
