package app.openstory.designsystem.control

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> HikariSegmentedControl(
    options: List<HikariSegmentedOption<T>>,
    selectedKey: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    validateSegmentedOptions(options, selectedKey)
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option.key == selectedKey,
                onClick = { onSelected(option.key) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                enabled = option.enabled,
                label = { Text(option.label) },
            )
        }
    }
}

private fun <T> validateSegmentedOptions(
    options: List<HikariSegmentedOption<T>>,
    selectedKey: T,
) {
    require(options.size in MIN_OPTION_COUNT..MAX_OPTION_COUNT)
    var selectedFound = false
    for (i in options.indices) {
        if (options[i].key == selectedKey) selectedFound = true
        for (j in (i + 1) until options.size) {
            require(options[i].key != options[j].key)
        }
    }
    require(selectedFound)
}

private const val MIN_OPTION_COUNT = 2
private const val MAX_OPTION_COUNT = 5
