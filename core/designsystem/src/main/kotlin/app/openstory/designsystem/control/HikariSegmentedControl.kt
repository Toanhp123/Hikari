package app.openstory.designsystem.control

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import app.openstory.designsystem.theme.hikariDimensions

data class HikariSegmentedOption<T>(
    val key: T,
    val label: String,
    val enabled: Boolean = true,
)

@Composable
fun <T> HikariSegmentedControl(
    options: List<HikariSegmentedOption<T>>,
    selectedKey: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    firstOptionFocusRequester: FocusRequester? = null,
    firstOptionNextFocusRequester: FocusRequester? = null,
) {
    require(options.isNotEmpty()) { "Segmented control requires at least one option" }
    require(options.map { option -> option.key }.distinct().size == options.size) {
        "Segmented control option keys must be unique"
    }
    require(options.any { option -> option.key == selectedKey }) {
        "Segmented control selected key must match an option"
    }

    SingleChoiceSegmentedButtonRow(
        modifier = modifier.fillMaxWidth(),
    ) {
        options.forEachIndexed { index, option ->
            val focusModifier = if (index == 0) {
                Modifier
                    .then(
                        firstOptionFocusRequester?.let { requester ->
                            Modifier.focusRequester(requester)
                        } ?: Modifier,
                    )
                    .then(
                        firstOptionNextFocusRequester?.let { next ->
                            Modifier.focusProperties { this.next = next; down = next }
                        } ?: Modifier,
                    )
            } else {
                Modifier
            }
            SegmentedButton(
                selected = option.key == selectedKey,
                onClick = { onSelected(option.key) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                modifier = focusModifier
                    .weight(1f)
                    .heightIn(min = MaterialTheme.hikariDimensions.minimumTouchTarget),
                enabled = option.enabled,
            ) {
                Text(text = option.label)
            }
        }
    }
}
