package app.openstory.home.ui

import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId

data class HomeStorySelection(
    val storyId: StoryId,
    val pluginId: PluginId,
    val sourceId: String,
)

data class HomeActions(
    val refresh: () -> Unit,
    val storySelected: (HomeStorySelection) -> Unit,
    val search: () -> Unit = {},
    val catalogSelected: (PluginId) -> Unit = {},
    val showCombined: () -> Unit = {},
)
