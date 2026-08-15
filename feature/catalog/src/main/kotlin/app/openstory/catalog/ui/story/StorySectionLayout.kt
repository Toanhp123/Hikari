package app.openstory.catalog.ui.story

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import app.openstory.designsystem.theme.hikariSpacing

@Composable
internal fun storySectionContentPadding(): PaddingValues =
    PaddingValues(MaterialTheme.hikariSpacing.space16)
