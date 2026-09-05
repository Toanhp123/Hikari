package app.openstory.reader.assets

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.reader.routing.ReaderSessionId
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReaderAssetManifestTest {
    @Test
    fun assetGraphRevisionMustBeNonNegative() {
        assertEquals(0L, ReaderAssetGraphRevision(0L).value)
        assertFailsWith<IllegalArgumentException> { ReaderAssetGraphRevision(-1L) }
    }

    @Test
    fun manifestRequiresContiguousOrdinalsAndMatchingIdentityFacts() {
        val descriptor = descriptor(0)

        val manifest = manifest(listOf(descriptor))

        assertEquals(1, manifest.descriptors.size)
        assertFailsWith<IllegalArgumentException> { manifest(listOf(descriptor.copy(imageOrdinal = 1))) }
        assertFailsWith<IllegalArgumentException> {
            manifest(listOf(descriptor.copy(stableAssetId = "other/page.jpg")))
        }
    }

    @Test
    fun transientManifestRequiresRuntimeIsolationAndDurableManifestRejectsIt() {
        assertFailsWith<IllegalArgumentException> {
            manifest(
                descriptors = listOf(descriptor(0)),
                persistenceMode = ReaderAssetPersistenceMode.TRANSIENT_ONLY,
                runtimeIsolationScope = null,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            manifest(
                descriptors = listOf(descriptor(0)),
                runtimeIsolationScope = ReaderRuntimeAssetScopeId("a".repeat(64)),
            )
        }
    }

    @Test
    fun payloadIsBoundedDefensiveAndHashesSourceIntegrityEvidence() {
        val source = byteArrayOf(1, 2, 3)
        val payload = ReaderAssetPayload.verifiedBounded(source, "image/jpeg", "etag-secret")
        source[0] = 9

        assertContentEquals(byteArrayOf(1, 2, 3), payload.bytes())
        assertEquals(3, payload.sizeBytes)
        assertEquals(64, payload.sourceIntegrityHash?.length)
        assertEquals(false, payload.sourceIntegrityHash?.contains("etag-secret"))
        assertFailsWith<IllegalArgumentException> {
            ReaderAssetPayload.verifiedBounded(ByteArray(ReaderAssetRuntimePolicy.MAX_READER_ASSET_BYTES + 1), null, null)
        }
        assertFailsWith<IllegalArgumentException> {
            ReaderAssetPayload.verifiedBounded(ByteArray(0), null, null)
        }
    }

    @Test
    fun readLeaseContractKeepsStreamOwnershipExplicit() {
        val lease = object : ReaderAssetReadLease {
            override val sizeBytes = 3L
            override fun openStream() = ByteArrayInputStream(byteArrayOf(1, 2, 3))
            override fun close() = Unit
        }

        assertContentEquals(byteArrayOf(1, 2, 3), lease.openStream().use { it.readBytes() })
    }

    private fun manifest(
        descriptors: List<ReaderPageAssetDescriptor>,
        persistenceMode: ReaderAssetPersistenceMode = ReaderAssetPersistenceMode.DURABLE_AUTOMATIC,
        runtimeIsolationScope: ReaderRuntimeAssetScopeId? = null,
    ) = ReaderAssetChapterManifest(
        sessionId = ReaderSessionId(1L),
        storyId = StoryId("story"),
        canonicalChapterId = CanonicalChapterId("chapter"),
        selectedReleaseId = ChapterReleaseId("release"),
        sourceNamespace = descriptors.first().key.sourceNamespace,
        securityScope = ReaderCacheSecurityScope.Public,
        contentVariant = ReaderContentVariant.ORIGINAL,
        identityMode = ReaderAssetIdentityMode.TRUSTED_STABLE,
        persistenceMode = persistenceMode,
        graphRevision = ReaderAssetGraphRevision(1L),
        imageSetNamespace = descriptors.first().key.imageSetNamespace,
        runtimeIsolationScope = runtimeIsolationScope,
        descriptors = descriptors,
    )

    private fun descriptor(ordinal: Int): ReaderPageAssetDescriptor {
        val stableId = "hash/page.jpg"
        val fingerprint = ReaderAssetIdentity.locatorFingerprint("https://cdn.example/page.jpg")
        val context = ReaderAssetKeyContext(
            sourceNamespace = ReaderAssetSourceNamespace.fromPluginId(PluginId("source.plugin")),
            selectedReleaseId = ChapterReleaseId("release"),
            securityScope = ReaderCacheSecurityScope.Public,
            contentVariant = ReaderContentVariant.ORIGINAL,
            identityMode = ReaderAssetIdentityMode.TRUSTED_STABLE,
            persistenceMode = ReaderAssetPersistenceMode.DURABLE_AUTOMATIC,
            runtimeIsolationScope = null,
        )
        val input = ReaderAssetPageIdentityInput(stableId, fingerprint)
        val set = ReaderAssetIdentity.imageSetNamespace(context, listOf(input))
        return ReaderPageAssetDescriptor(
            key = ReaderAssetIdentity.pageKey(context, set, 0, input),
            uiBlockId = "image-0",
            stableAssetId = stableId,
            imageOrdinal = ordinal,
            deliveryLocator = "https://cdn.example/page.jpg",
            locatorFingerprint = fingerprint,
        )
    }
}
