package app.openstory.reader.assets

import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.plugins.api.manifest.ReaderImageIdentityContract
import app.openstory.plugins.api.manifest.ReaderImageLocatorContract
import app.openstory.plugins.api.manifest.ReaderImagePersistenceContract
import app.openstory.reader.content.ReaderImageSourcePolicy
import app.openstory.reader.routing.ReaderSessionId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class ReaderAssetIdentityTest {
    @Test
    fun explicitIdentityContractsResolveWithoutInferringTrustFromIdShape() {
        val trusted = policy(
            identity = ReaderImageIdentityContract.STABLE_ID_CHANGES_WITH_CONTENT,
            persistence = ReaderImagePersistenceContract.PUBLIC,
        )
        val hashLookingButUntrusted = ReaderImageSourcePolicy.FAIL_CLOSED

        assertEquals(
            ReaderAssetIdentityMode.TRUSTED_STABLE,
            ReaderAssetIdentity.resolveMode(trusted, listOf("hash/page-1.jpg")),
        )
        assertEquals(
            ReaderAssetIdentityMode.NON_PERSISTENT,
            ReaderAssetIdentity.resolveMode(hashLookingButUntrusted, listOf("hash/page-1.jpg")),
        )
        assertEquals(
            ReaderAssetIdentityMode.NON_PERSISTENT,
            ReaderAssetIdentity.resolveMode(trusted, listOf("duplicate", "duplicate")),
        )
        assertEquals(
            ReaderAssetIdentityMode.NON_PERSISTENT,
            ReaderAssetIdentity.resolveMode(trusted, listOf(" ")),
        )
    }

    @Test
    fun sourceNamespaceComesOnlyFromCanonicalPluginId() {
        val first = ReaderAssetSourceNamespace.fromPluginId(PluginId("source.plugin"))
        val same = ReaderAssetSourceNamespace.fromPluginId(PluginId("source.plugin"))
        val other = ReaderAssetSourceNamespace.fromPluginId(PluginId("other.plugin"))

        assertEquals(first, same)
        assertNotEquals(first, other)
        assertEquals("source.plugin", first.value)
    }

    @Test
    fun locatorFingerprintUsesConservativeCanonicalHttpsFields() {
        val canonical = ReaderAssetIdentity.locatorFingerprint(
            "https://CDN.Example:443/a.jpg?token=one#ignored",
        )

        assertEquals(
            "499b0f6180ba74c731a1b74a74c5bcd200148c47c745b0b492b9a88d777b3d2f",
            canonical.value,
        )
        assertEquals(
            canonical,
            ReaderAssetIdentity.locatorFingerprint("https://cdn.example/a.jpg?token=one#other"),
        )
        assertNotEquals(canonical, ReaderAssetIdentity.locatorFingerprint("https://cdn.example/a.jpg?token=two"))
        assertNotEquals(
            ReaderAssetIdentity.locatorFingerprint("https://cdn.example/a%2Fb.jpg"),
            ReaderAssetIdentity.locatorFingerprint("https://cdn.example/a/b.jpg"),
        )
    }

    @Test
    fun trustedKeysIgnoreLocatorRotationButImageSetOrderAndIdentityRemainBound() {
        val firstPages = listOf(
            page("hash/page-1.jpg", "https://cdn.example/a.jpg?token=one"),
            page("hash/page-2.jpg", "https://cdn.example/b.jpg?token=two"),
        )
        val rotatedPages = listOf(
            page("hash/page-1.jpg", "https://other.example/a.jpg?token=rotated"),
            page("hash/page-2.jpg", "https://other.example/b.jpg?token=rotated"),
        )
        val context = context(
            identityMode = ReaderAssetIdentityMode.TRUSTED_STABLE,
            persistenceMode = ReaderAssetPersistenceMode.DURABLE_AUTOMATIC,
        )

        val firstSet = ReaderAssetIdentity.imageSetNamespace(context, firstPages)
        val rotatedSet = ReaderAssetIdentity.imageSetNamespace(context, rotatedPages)
        val firstKey = ReaderAssetIdentity.pageKey(context, firstSet, 0, firstPages.first())
        val rotatedKey = ReaderAssetIdentity.pageKey(context, rotatedSet, 0, rotatedPages.first())

        assertEquals("79786d529b947fdd07f9e9b12f7e0c29faffaaca3514c6d7add6fe4c183e7de8", firstSet.value)
        assertEquals("e89b734a6c1b04ab7014b420cf432786694fe46b2fe1bd78b98865da6d6ed516", firstKey.hash.value)
        assertEquals(firstSet, rotatedSet)
        assertEquals(firstKey, rotatedKey)
        assertNotEquals(firstSet, ReaderAssetIdentity.imageSetNamespace(context, firstPages.reversed()))
    }

    @Test
    fun locatorBoundAndTransientKeysHaveStableGoldenVectors() {
        val locatorPage = page("delivery/page-1", "https://CDN.Example:443/a.jpg?token=one#ignored")
        val locatorContext = context(
            identityMode = ReaderAssetIdentityMode.LOCATOR_BOUND,
            persistenceMode = ReaderAssetPersistenceMode.DURABLE_AUTOMATIC,
        )
        val locatorSet = ReaderAssetIdentity.imageSetNamespace(locatorContext, listOf(locatorPage))
        val locatorKey = ReaderAssetIdentity.pageKey(locatorContext, locatorSet, 0, locatorPage)

        assertEquals("55d23625c2daac190522ca9a8e3c024b7c4938e6171e8e8f5417fae153ccec24", locatorSet.value)
        assertEquals("e61acdbcfb1e6f6bb9570db7e1077ce6c5827608f26e2c8bc03debb059484811", locatorKey.hash.value)
        val changedLocatorPage = page("delivery/page-1", "https://cdn.example/a.jpg?token=changed")
        val changedLocatorSet = ReaderAssetIdentity.imageSetNamespace(locatorContext, listOf(changedLocatorPage))
        assertNotEquals(locatorKey, ReaderAssetIdentity.pageKey(locatorContext, changedLocatorSet, 0, changedLocatorPage))

        val runtimeScope = ReaderRuntimeAssetScopeIdFactory {
            UUID.fromString("00000000-0000-0000-0000-000000000007")
        }.create(ReaderSessionId(7L), ReaderAssetSourceNamespace.fromPluginId(PluginId("source.plugin")))
        val transientPage = page("hash/page-1.jpg", "https://CDN.Example:443/a.jpg?token=one#ignored")
        val transientContext = context(
            identityMode = ReaderAssetIdentityMode.NON_PERSISTENT,
            persistenceMode = ReaderAssetPersistenceMode.TRANSIENT_ONLY,
            securityScope = ReaderCacheSecurityScope.NonPersistentPrivate,
            runtimeScope = runtimeScope,
        )
        val transientSet = ReaderAssetIdentity.imageSetNamespace(transientContext, listOf(transientPage))
        val transientKey = ReaderAssetIdentity.pageKey(transientContext, transientSet, 0, transientPage)

        assertEquals("d3877cc304393e8d65c75121775e99088afd8888fbd3cf15713329bf3320879c", runtimeScope.value)
        assertEquals("4d40ea2762f2f39f4b453c33af10927f042e879db2dc80db6e2286e07c2abd07", transientSet.value)
        assertEquals("654d63bb2cddbd57cc6d1c6a11e6730f276812f4d9f7af754259cda23aa21262", transientKey.hash.value)
        assertFalse(transientKey.hash.value.contains("token=one"))

        val otherScope = ReaderRuntimeAssetScopeIdFactory {
            UUID.fromString("00000000-0000-0000-0000-000000000008")
        }.create(ReaderSessionId(7L), ReaderAssetSourceNamespace.fromPluginId(PluginId("source.plugin")))
        val otherContext = transientContext.copy(runtimeIsolationScope = otherScope)
        val otherSet = ReaderAssetIdentity.imageSetNamespace(otherContext, listOf(transientPage))
        assertNotEquals(transientKey, ReaderAssetIdentity.pageKey(otherContext, otherSet, 0, transientPage))
    }

    @Test
    fun persistencePermissionIsIndependentFromIdentityQuality() {
        val trustedNonPersistent = policy(
            identity = ReaderImageIdentityContract.STABLE_ID_CHANGES_WITH_CONTENT,
            persistence = ReaderImagePersistenceContract.NON_PERSISTENT,
        )
        val trustedPublic = policy(
            identity = ReaderImageIdentityContract.STABLE_ID_CHANGES_WITH_CONTENT,
            persistence = ReaderImagePersistenceContract.PUBLIC,
        )
        val accountScoped = policy(
            identity = ReaderImageIdentityContract.STABLE_ID_CHANGES_WITH_CONTENT,
            persistence = ReaderImagePersistenceContract.ACCOUNT_SCOPED,
        )

        assertEquals(
            ReaderAssetPersistenceMode.TRANSIENT_ONLY,
            ReaderAssetIdentity.resolvePersistence(
                trustedNonPersistent,
                ReaderAssetIdentityMode.TRUSTED_STABLE,
                ReaderCacheSecurityScope.Public,
            ),
        )
        assertEquals(
            ReaderAssetPersistenceMode.DURABLE_AUTOMATIC,
            ReaderAssetIdentity.resolvePersistence(
                trustedPublic,
                ReaderAssetIdentityMode.TRUSTED_STABLE,
                ReaderCacheSecurityScope.Public,
            ),
        )
        assertEquals(
            ReaderAssetPersistenceMode.TRANSIENT_ONLY,
            ReaderAssetIdentity.resolvePersistence(
                accountScoped,
                ReaderAssetIdentityMode.TRUSTED_STABLE,
                ReaderCacheSecurityScope.NonPersistentPrivate,
            ),
        )
    }

    @Test
    fun unsupportedSchemaIsARepairCandidate() {
        val page = page("hash/page-1.jpg", "https://cdn.example/a.jpg")
        val context = context(
            identityMode = ReaderAssetIdentityMode.TRUSTED_STABLE,
            persistenceMode = ReaderAssetPersistenceMode.DURABLE_AUTOMATIC,
        )
        val imageSet = ReaderAssetIdentity.imageSetNamespace(context, listOf(page))
        val key = ReaderAssetIdentity.pageKey(context, imageSet, 0, page)

        assertEquals(false, key.copy(schemaVersion = ReaderAssetKeySchemaVersion(2)).isSupportedSchema())
    }

    @Test
    fun sourceAndSecurityScopesCannotAliasKeys() {
        val page = page("hash/page-1.jpg", "https://cdn.example/a.jpg")
        val publicContext = context(
            identityMode = ReaderAssetIdentityMode.TRUSTED_STABLE,
            persistenceMode = ReaderAssetPersistenceMode.DURABLE_AUTOMATIC,
        )
        val otherSourceContext = publicContext.copy(
            sourceNamespace = ReaderAssetSourceNamespace.fromPluginId(PluginId("other.plugin")),
        )
        val accountContext = publicContext.copy(
            securityScope = ReaderCacheSecurityScope.AccountScoped("account-safe-hash"),
        )

        val publicSet = ReaderAssetIdentity.imageSetNamespace(publicContext, listOf(page))
        val sourceSet = ReaderAssetIdentity.imageSetNamespace(otherSourceContext, listOf(page))
        val accountSet = ReaderAssetIdentity.imageSetNamespace(accountContext, listOf(page))
        val publicKey = ReaderAssetIdentity.pageKey(publicContext, publicSet, 0, page)
        val sourceKey = ReaderAssetIdentity.pageKey(otherSourceContext, sourceSet, 0, page)
        val accountKey = ReaderAssetIdentity.pageKey(accountContext, accountSet, 0, page)

        assertNotEquals(publicKey, sourceKey)
        assertNotEquals(publicKey, accountKey)
        assertFalse(accountKey.hash.value.contains("account-safe-hash"))
    }

    private fun context(
        identityMode: ReaderAssetIdentityMode,
        persistenceMode: ReaderAssetPersistenceMode,
        securityScope: ReaderCacheSecurityScope = ReaderCacheSecurityScope.Public,
        runtimeScope: ReaderRuntimeAssetScopeId? = null,
    ) = ReaderAssetKeyContext(
        sourceNamespace = ReaderAssetSourceNamespace.fromPluginId(PluginId("source.plugin")),
        selectedReleaseId = ChapterReleaseId("release-1"),
        securityScope = securityScope,
        contentVariant = ReaderContentVariant.ORIGINAL,
        identityMode = identityMode,
        persistenceMode = persistenceMode,
        runtimeIsolationScope = runtimeScope,
    )

    private fun page(stableId: String, locator: String) = ReaderAssetPageIdentityInput(
        stableAssetId = stableId,
        locatorFingerprint = ReaderAssetIdentity.locatorFingerprint(locator),
    )

    private fun policy(
        identity: ReaderImageIdentityContract,
        persistence: ReaderImagePersistenceContract,
    ) = ReaderImageSourcePolicy(
        identityContract = identity,
        locatorContract = ReaderImageLocatorContract.MUTABLE_OR_UNKNOWN,
        persistenceContract = persistence,
    )
}
