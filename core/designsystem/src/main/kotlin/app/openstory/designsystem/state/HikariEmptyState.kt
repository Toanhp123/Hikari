package app.openstory.designsystem.state

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import app.openstory.designsystem.theme.hikariSpacing

@Composable
fun HikariEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionFocusRequester: FocusRequester? = null,
) {
    HikariStateContent(
        title = title,
        modifier = modifier,
        message = message,
        actionLabel = actionLabel,
        onAction = onAction,
        actionFocusRequester = actionFocusRequester,
        titleColor = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
internal fun HikariStateContent(
    title: String,
    modifier: Modifier,
    message: String?,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    actionFocusRequester: FocusRequester? = null,
    titleColor: Color,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(MaterialTheme.hikariSpacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            color = titleColor,
            style = MaterialTheme.typography.titleMedium,
        )
        if (message != null) {
            Spacer(modifier = Modifier.height(MaterialTheme.hikariSpacing.small))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(MaterialTheme.hikariSpacing.large))
            Button(
                onClick = onAction,
                modifier = actionFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier,
            ) {
                Text(text = actionLabel)
            }
        }
    }
}
