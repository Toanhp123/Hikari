package app.openstory.reader.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.openstory.common.id.ChapterReleaseId
import app.openstory.designsystem.control.HikariUtilityAction
import app.openstory.designsystem.theme.hikariDimensions

@Composable
fun ReleaseSwitcher(
    releases: List<ReaderReleaseUiModel>,
    selectedReleaseId: ChapterReleaseId?,
    onSelected: (ChapterReleaseId) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = releases.firstOrNull { it.id == selectedReleaseId }
    HikariUtilityAction(
        onClick = { expanded = true },
        enabled = releases.size > 1,
        modifier = modifier.fillMaxWidth().heightIn(min = MaterialTheme.hikariDimensions.minimumTouchTarget),
    ) {
        Text(selected?.source ?: "Source")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        releases.forEach { release ->
            DropdownMenuItem(
                text = { Text("${release.source} - ${release.languageTag} - ${release.label}") },
                onClick = {
                    expanded = false
                    onSelected(release.id)
                },
            )
        }
    }
}
