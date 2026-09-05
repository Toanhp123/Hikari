package app.openstory.downloads.assets

import app.openstory.downloads.cache.AutomaticCacheInvalidationScope
import app.openstory.downloads.cache.AutomaticCacheWriteScope
import app.openstory.reader.assets.ReaderAssetClearScope
import app.openstory.reader.assets.ReaderAssetCommitFacts
import app.openstory.reader.assets.ReaderAssetIdentityMode
import app.openstory.reader.assets.ReaderAssetPersistenceMode
import app.openstory.reader.assets.ReaderCacheSecurityScope
import java.security.MessageDigest

internal object ReaderAssetEvictionMapper {
    fun writeScope(facts: ReaderAssetCommitFacts): AutomaticCacheWriteScope? =
        if (facts.persistenceMode != ReaderAssetPersistenceMode.DURABLE_AUTOMATIC ||
            facts.identityMode == ReaderAssetIdentityMode.NON_PERSISTENT
        ) {
            null
        } else when (val securityScope = facts.securityScope) {
            ReaderCacheSecurityScope.Public ->
                AutomaticCacheWriteScope.ReaderAssetSource(facts.sourceNamespace)
            is ReaderCacheSecurityScope.AccountScoped -> AutomaticCacheWriteScope.ReaderAssetAccount(
                sourceNamespace = facts.sourceNamespace,
                securityScopeHash = accountScopeHash(securityScope.stableNonSecretNamespace),
            )
            ReaderCacheSecurityScope.NonPersistentPrivate -> null
        }

    fun securityScopeHash(scope: ReaderCacheSecurityScope): String? = when (scope) {
        ReaderCacheSecurityScope.Public -> null
        is ReaderCacheSecurityScope.AccountScoped -> accountScopeHash(scope.stableNonSecretNamespace)
        ReaderCacheSecurityScope.NonPersistentPrivate -> null
    }

    fun invalidationScope(scope: ReaderAssetClearScope): AutomaticCacheInvalidationScope = when (scope) {
        ReaderAssetClearScope.AllAutomatic -> AutomaticCacheInvalidationScope.AllAutomatic
        is ReaderAssetClearScope.Source ->
            AutomaticCacheInvalidationScope.ReaderAssetSource(scope.sourceNamespace)
        is ReaderAssetClearScope.Account -> AutomaticCacheInvalidationScope.ReaderAssetAccount(
            sourceNamespace = scope.sourceNamespace,
            securityScopeHash = accountScopeHash(scope.stableNonSecretNamespace),
        )
        is ReaderAssetClearScope.AllAccountScopesForSource ->
            AutomaticCacheInvalidationScope.AllReaderAssetAccountsForSource(scope.sourceNamespace)
    }

    private fun accountScopeHash(stableNonSecretNamespace: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(stableNonSecretNamespace.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte ->
                HEX[(byte.toInt() ushr NIBBLE_BITS) and NIBBLE_MASK].toString() +
                    HEX[byte.toInt() and NIBBLE_MASK]
            }

    private const val NIBBLE_BITS = 4
    private const val NIBBLE_MASK = 0x0f
    private val HEX = "0123456789abcdef".toCharArray()
}
