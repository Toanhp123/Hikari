package app.openstory.designsystem.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.openstory.designsystem.theme.hikariSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HikariActionSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest, modifier = modifier) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(
                    start = MaterialTheme.hikariSpacing.space20,
                    end = MaterialTheme.hikariSpacing.space20,
                    bottom = MaterialTheme.hikariSpacing.space24,
                ),
        ) {
            title?.let { sheetTitle ->
                Text(
                    text = sheetTitle,
                    modifier = Modifier
                        .semantics { heading() }
                        .padding(bottom = MaterialTheme.hikariSpacing.space12),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            content()
        }
    }
}
