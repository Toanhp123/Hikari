package app.openstory.catalog.ui.discover

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import app.openstory.catalog.model.ContentType
import app.openstory.designsystem.control.HikariSegmentedControl
import app.openstory.designsystem.control.HikariSegmentedOption

@Composable
fun DiscoverMediaTypeSelector(
    options: List<DiscoverMediaTypeOption>,
    selectedContentType: ContentType,
    onSelected: (ContentType) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    nextFocusRequester: FocusRequester? = null,
) {
    HikariSegmentedControl(
        options = options.map { option ->
            HikariSegmentedOption(
                key = option.contentType,
                label = option.contentType.discoverLabel(),
                enabled = option.enabled,
            )
        },
        selectedKey = selectedContentType,
        onSelected = onSelected,
        modifier = modifier.testTag("discover-media-selector"),
        firstOptionFocusRequester = focusRequester,
        firstOptionNextFocusRequester = nextFocusRequester,
    )
}

private fun ContentType.discoverLabel(): String = when (this) {
    ContentType.MANGA -> "Manga"
    ContentType.LIGHT_NOVEL -> "Light Novel"
    ContentType.WEB_NOVEL -> "Web Novel"
    ContentType.ANIME -> "Anime"
}
