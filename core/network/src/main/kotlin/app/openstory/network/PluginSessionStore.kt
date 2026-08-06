package app.openstory.network

import okhttp3.Cookie

interface PluginSessionStore {
    fun load(
        pluginId: String,
        host: String,
    ): List<Cookie>

    fun save(
        pluginId: String,
        host: String,
        cookies: List<Cookie>,
    )
}
