package app.openstory.reader.ui

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.openstory.common.id.ChapterReleaseId

@Composable
fun ReleaseSwitcher(
    releases: List<ReaderReleaseUiModel>,
    selectedReleaseId: ChapterReleaseId?,
    onSelected: (ChapterReleaseId) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = releases.firstOrNull { it.id == selectedReleaseId }
    TextButton(onClick = { expanded = true }, enabled = releases.size > 1) {
        Text(selected?.source ?: "Source")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        releases.forEach { release ->
            DropdownMenuItem(
                text = { Text("${release.source} · ${release.languageTag} · ${release.label}") },
                onClick = {
                    expanded = false
                    onSelected(release.id)
                },
            )
        }
    }
}
