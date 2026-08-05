package app.openstory.network

import okhttp3.HttpUrl

class RedactingNetworkLogger(
    private val sink: (String) -> Unit,
) {
    fun logRequest(
        pluginId: String,
        url: HttpUrl,
    ) {
        sink(
            "network.request " +
                "plugin=$pluginId " +
                "host=${url.host} " +
                "path=${url.encodedPath}",
        )
    }

    fun logResponse(
        pluginId: String,
        url: HttpUrl,
        status: Int,
    ) {
        sink(
            "network.response " +
                "plugin=$pluginId " +
                "host=${url.host} " +
                "path=${url.encodedPath} " +
                "status=$status",
        )
    }
}
