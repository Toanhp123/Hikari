package app.openstory.designsystem.sheet

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import app.openstory.designsystem.theme.HikariDimensions

@Composable
fun <T : Any> HikariChoiceSheet(
    title: String,
    options: List<T>,
    selectedOption: T?,
    optionLabel: (T) -> String,
    onOptionSelected: (T) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    require(options.toSet().size == options.size) {
        "HikariChoiceSheet options must have unique identity."
    }

    HikariActionSheet(
        title = title,
        onDismissRequest = onDismissRequest,
        modifier = modifier.selectableGroup(),
    ) {
        options.forEach { option ->
            val selected = option == selectedOption
            ListItem(
                headlineContent = { Text(optionLabel(option)) },
                trailingContent = { RadioButton(selected = selected, onClick = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = HikariDimensions.MinimumTouchTarget)
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onOptionSelected(option) },
                    ),
            )
        }
    }
}
