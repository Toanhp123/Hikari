package app.openstory.network

internal fun isHostOwnedHeader(
    name: String,
): Boolean =
    name.equals(
        COOKIE_HEADER,
        ignoreCase = true,
    ) ||
        name.equals(
            USER_AGENT_HEADER,
            ignoreCase = true,
        )

internal const val COOKIE_HEADER = "Cookie"
internal const val USER_AGENT_HEADER = "User-Agent"
internal const val HOST_USER_AGENT = "OpenStory/1.0"
