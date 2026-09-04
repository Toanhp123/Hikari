package app.openstory.reader.assets

import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.plugins.api.manifest.ReaderImageIdentityContract
import app.openstory.plugins.api.manifest.ReaderImageLocatorContract
import app.openstory.plugins.api.manifest.ReaderImagePersistenceContract
import app.openstory.reader.content.ReaderImageSourcePolicy
import app.openstory.reader.document.ReaderBlock
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.routing.ReaderSessionId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class ReaderAssetLocatorRefreshTest {
    @Test
    fun `trusted durable locator rotation keeps logical page keys`() {
        val current = manifest(
            policy = TRUSTED_DURABLE,
            locatorTag = "old",
            runtimeNonce = "current",
        )
        val refreshed = manifest(
            policy = TRUSTED_DURABLE,
            locatorTag = "new",
            runtimeNonce = "refreshed",
        )

        val changed = assertIs<ReaderRefreshedManifestDecision.Changed>(
            ReaderAssetLocatorRefresh.compare(current, refreshed),
        )

        assertEquals(ReaderAssetIdentityMode.TRUSTED_STABLE, current.identityMode)
        assertEquals(current.descriptors.map { it.key }, changed.manifest.descriptors.map { it.key })
        assertNotEquals(
            current.descriptors.map { it.locatorFingerprint },
            changed.manifest.descriptors.map { it.locatorFingerprint },
        )
    }

    @Test
    fun `locator bound rotation creates a new image set and page key set`() {
        val current = manifest(
            policy = LOCATOR_BOUND_DURABLE,
            locatorTag = "old",
            runtimeNonce = "current",
        )
        val refreshed = manifest(
            policy = LOCATOR_BOUND_DURABLE,
            locatorTag = "new",
            runtimeNonce = "refreshed",
        )

        val changed = assertIs<ReaderRefreshedManifestDecision.Changed>(
            ReaderAssetLocatorRefresh.compare(current, refreshed),
        )

        assertEquals(ReaderAssetIdentityMode.LOCATOR_BOUND, current.identityMode)
        assertNotEquals(current.imageSetNamespace, changed.manifest.imageSetNamespace)
        assertNotEquals(current.descriptors.map { it.key }, changed.manifest.descriptors.map { it.key })
    }

    @Test
    fun `unchanged delivery facts do not publish a transient manifest merely because runtime scope rotated`() {
        val current = manifest(
            policy = TRUSTED_TRANSIENT,
            locatorTag = "same",
            runtimeNonce = "current",
        )
        val refreshed = manifest(
            policy = TRUSTED_TRANSIENT,
            locatorTag = "same",
            runtimeNonce = "refreshed",
        )

        assertNotEquals(current.runtimeIsolationScope, refreshed.runtimeIsolationScope)
        assertNotEquals(current.descriptors.map { it.key }, refreshed.descriptors.map { it.key })
        assertEquals(
            ReaderRefreshedManifestDecision.Unchanged,
            ReaderAssetLocatorRefresh.compare(current, refreshed),
        )
    }

    @Test
    fun `stable identity or page structure drift is route invalidation`() {
        val current = manifest(
            policy = TRUSTED_DURABLE,
            locatorTag = "old",
            runtimeNonce = "current",
        )
        val changedStableIdentity = manifest(
            policy = TRUSTED_DURABLE,
            locatorTag = "new",
            runtimeNonce = "refreshed",
            stableIdPrefix = "changed",
        )
        val changedPageCount = manifest(
            policy = TRUSTED_DURABLE,
            locatorTag = "new",
            runtimeNonce = "refreshed-2",
            pageCount = 3,
        )

        assertEquals(
            ReaderRefreshedManifestDecision.RouteInvalidated,
            ReaderAssetLocatorRefresh.compare(current, changedStableIdentity),
        )
        assertEquals(
            ReaderRefreshedManifestDecision.RouteInvalidated,
            ReaderAssetLocatorRefresh.compare(current, changedPageCount),
        )
    }

    private fun manifest(
        policy: ReaderImageSourcePolicy,
        locatorTag: String,
        runtimeNonce: String,
        stableIdPrefix: String = "stable",
        pageCount: Int = 2,
    ): ReaderAssetChapterManifest {
        val storyId = StoryId("story")
        val chapterId = CanonicalChapterId("chapter")
        val pluginId = PluginId("plugin")
        val release = ChapterRelease(
            id = ChapterReleaseId("release"),
            storyId = storyId,
            pluginId = pluginId,
            sourceStoryId = "source-story",
            sourceReleaseId = "source-release",
            displayLabel = "chapter",
            parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
            languageTag = "en",
            publishedAtEpochMillis = 1L,
            canonicalChapterId = chapterId,
        )
        val document = ReaderDocument(
            title = "chapter",
            blocks = List(pageCount) { ordinal ->
                ReaderBlock.ImagePage(
                    id = "image-$ordinal",
                    stableAssetId = "$stableIdPrefix/page-$ordinal",
                    imageUrl = "https://cdn.example/$locatorTag/$ordinal.jpg",
                )
            },
            fingerprint = "semantic-fingerprint",
        )
        return requireNotNull(
            ReaderAssetManifestFactory(
                ReaderRuntimeAssetScopeIdFactory {
                    UUID.nameUUIDFromBytes(runtimeNonce.toByteArray())
                },
            ).create(
                sessionId = ReaderSessionId(1),
                storyId = storyId,
                canonicalChapterId = chapterId,
                selectedRelease = release,
                graphRevision = ReaderAssetGraphRevision(1),
                document = document,
                imageSourcePolicy = policy,
                sourcePluginId = pluginId,
            ),
        )
    }

    private companion object {
        val TRUSTED_DURABLE = ReaderImageSourcePolicy(
            identityContract = ReaderImageIdentityContract.STABLE_ID_CHANGES_WITH_CONTENT,
            locatorContract = ReaderImageLocatorContract.MUTABLE_OR_UNKNOWN,
            persistenceContract = ReaderImagePersistenceContract.PUBLIC,
        )
        val TRUSTED_TRANSIENT = TRUSTED_DURABLE.copy(
            persistenceContract = ReaderImagePersistenceContract.NON_PERSISTENT,
        )
        val LOCATOR_BOUND_DURABLE = ReaderImageSourcePolicy(
            identityContract = ReaderImageIdentityContract.DELIVERY_STABLE_ONLY,
            locatorContract = ReaderImageLocatorContract.LOCATOR_CHANGES_WITH_CONTENT,
            persistenceContract = ReaderImagePersistenceContract.PUBLIC,
        )
    }
}
