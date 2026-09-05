package app.openstory.plugins.runtime.auth

import app.openstory.common.id.PluginId

@JvmInline
value class SecretCookieValue private constructor(val raw: String) {
    override fun toString(): String = "<redacted>"

    companion object {
        fun of(raw: String): SecretCookieValue {
            require(raw.isNotBlank() && raw.none(Char::isISOControl))
            return SecretCookieValue(raw)
        }
    }
}

data class PluginSessionRecord(
    val pluginId: PluginId,
    val targetHost: String,
    val targetPathPrefix: String,
    val cookieName: String,
    val cookieValue: SecretCookieValue,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val authenticationPolicyFingerprint: String,
) {
    init {
        require(targetHost.isNotBlank() && targetHost == targetHost.lowercase())
        require(targetPathPrefix.startsWith('/'))
        require(cookieName.isNotBlank())
        require(expiresAtEpochMillis > createdAtEpochMillis)
        require(authenticationPolicyFingerprint.matches(Regex("[0-9a-f]{64}")))
    }
}

enum class PluginSessionStatus { LOGGED_OUT, AUTHENTICATED, EXPIRED }

data class PluginSessionSummary(
    val pluginId: PluginId,
    val status: PluginSessionStatus,
    val expiresAtEpochMillis: Long?,
    val credentialGeneration: Long,
)
