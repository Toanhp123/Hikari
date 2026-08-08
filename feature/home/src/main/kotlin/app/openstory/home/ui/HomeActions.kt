package app.openstory.home.ui

import app.openstory.model.PluginId
import app.openstory.model.StoryId

data class HomeActions(
    val refresh: () -> Unit,
    val storySelected: (StoryId) -> Unit,
    val catalogSelected: (PluginId) -> Unit = {},
    val showCombined: () -> Unit = {},
)
