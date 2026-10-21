package io.github.toanhp123.hikari.plugins

/** Exact upstream LNReader ID, never accepted with path separators or traversal. */
internal object NovelPluginIds {
    val VALID = Regex("[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)*")
    fun requireValid(id: String) {
        require(id.length in 1..128 && VALID.matches(id)) { "Invalid Novel plugin ID" }
    }
}
