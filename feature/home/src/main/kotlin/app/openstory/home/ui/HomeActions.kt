package app.openstory.home.ui

import app.openstory.model.PluginId
import app.openstory.model.StoryId

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
