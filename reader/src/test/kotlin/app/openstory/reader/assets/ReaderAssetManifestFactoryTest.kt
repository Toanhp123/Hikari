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
import app.openstory.reader.engine.ReaderChapterGraphRevision
import app.openstory.reader.routing.ReaderSessionId
import java.util.ArrayDeque
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class ReaderAssetManifestFactoryTest {
    @Test
    fun `trusted manifest uses producing source and complete ordered stable identities`() {
        val factory = factory()
        val original = imageDocument(
            image("image-page-prefix", "complete/source/page-00000000000000000001", "https://cdn.example/a.jpg?token=1"),
            image("image-page-prefix", "complete/source/page-00000000000000000002", "https://cdn.example/b.jpg?token=2"),
        )
        val rotated = imageDocument(
            image("image-page-prefix", "complete/source/page-00000000000000000001", "https://rotated.example/a.jpg?token=3"),
            image("image-page-prefix", "complete/source/page-00000000000000000002", "https://rotated.example/b.jpg?token=4"),
        )

        val first = manifest(factory, original, policy = trustedPublicPolicy())
        val second = manifest(factory, rotated, policy = trustedPublicPolicy())
        val reordered = manifest(
            factory,
            imageDocument(original.blocks[1] as ReaderBlock.ImagePage, original.blocks[0] as ReaderBlock.ImagePage),
            policy = trustedPublicPolicy(),
        )
        val changedFullIdentity = manifest(
            factory,
            imageDocument(
                image(
                    "image-page-prefix",
                    "complete/source/page-00000000000000000009",
                    "https://cdn.example/a.jpg?token=1",
                ),
                original.blocks[1] as ReaderBlock.ImagePage,
            ),
            policy = trustedPublicPolicy(),
        )

        assertEquals(ReaderAssetSourceNamespace.fromPluginId(SOURCE_ID), first.sourceNamespace)
        assertEquals(ReaderCacheSecurityScope.Public, first.securityScope)
        assertEquals(ReaderAssetIdentityMode.TRUSTED_STABLE, first.identityMode)
        assertEquals(ReaderAssetPersistenceMode.DURABLE_AUTOMATIC, first.persistenceMode)
        assertNull(first.runtimeIsolationScope)
        assertEquals(original.blocks.map { (it as ReaderBlock.ImagePage).stableAssetId }, first.descriptors.map { it.stableAssetId })
        assertEquals(first.imageSetNamespace, second.imageSetNamespace)
        assertEquals(first.descriptors.map { it.key }, second.descriptors.map { it.key })
        assertNotEquals(first.imageSetNamespace, reordered.imageSetNamespace)
        assertNotEquals(first.imageSetNamespace, changedFullIdentity.imageSetNamespace)
    }

    @Test
    fun `locator bound manifest rekeys when normalized delivery facts change`() {
        val factory = factory()
        val policy = ReaderImageSourcePolicy(
            identityContract = ReaderImageIdentityContract.DELIVERY_STABLE_ONLY,
            locatorContract = ReaderImageLocatorContract.LOCATOR_CHANGES_WITH_CONTENT,
            persistenceContract = ReaderImagePersistenceContract.PUBLIC,
        )

        val first = manifest(
            factory,
            imageDocument(image("same-id", "same/stable-id", "https://CDN.Example:443/page.jpg?token=one#ignored")),
            policy,
        )
        val fragmentOnly = manifest(
            factory,
            imageDocument(image("same-id", "same/stable-id", "https://cdn.example/page.jpg?token=one#changed")),
            policy,
        )
        val changedQuery = manifest(
            factory,
            imageDocument(image("same-id", "same/stable-id", "https://cdn.example/page.jpg?token=two")),
            policy,
        )

        assertEquals(ReaderAssetIdentityMode.LOCATOR_BOUND, first.identityMode)
        assertEquals(first.imageSetNamespace, fragmentOnly.imageSetNamespace)
        assertEquals(first.descriptors.single().key, fragmentOnly.descriptors.single().key)
        assertNotEquals(first.imageSetNamespace, changedQuery.imageSetNamespace)
        assertNotEquals(first.descriptors.single().key, changedQuery.descriptors.single().key)
    }

    @Test
    fun `account scoped policy fails closed with fresh transient isolation`() {
        val nonces = ArrayDeque(
            listOf(
                UUID.fromString("00000000-0000-0000-0000-000000000010"),
                UUID.fromString("00000000-0000-0000-0000-000000000011"),
            ),
        )
        val factory = ReaderAssetManifestFactory(ReaderRuntimeAssetScopeIdFactory { nonces.removeFirst() })
        val policy = ReaderImageSourcePolicy(
            identityContract = ReaderImageIdentityContract.STABLE_ID_CHANGES_WITH_CONTENT,
            locatorContract = ReaderImageLocatorContract.MUTABLE_OR_UNKNOWN,
            persistenceContract = ReaderImagePersistenceContract.ACCOUNT_SCOPED,
        )
        val document = imageDocument(image("page", "stable/page", "https://cdn.example/page.jpg"))

        val first = manifest(factory, document, policy)
        val second = manifest(factory, document, policy)

        assertEquals(ReaderCacheSecurityScope.NonPersistentPrivate, first.securityScope)
        assertEquals(ReaderAssetIdentityMode.TRUSTED_STABLE, first.identityMode)
        assertEquals(ReaderAssetPersistenceMode.TRANSIENT_ONLY, first.persistenceMode)
        assertNotEquals(first.runtimeIsolationScope, second.runtimeIsolationScope)
        assertNotEquals(first.imageSetNamespace, second.imageSetNamespace)
        assertNotEquals(first.descriptors.single().key, second.descriptors.single().key)
    }

    @Test
    fun `image manifest rejects producing source mismatch`() {
        assertFailsWith<IllegalArgumentException> {
            factory().create(
                sessionId = ReaderSessionId(7),
                storyId = STORY_ID,
                canonicalChapterId = CHAPTER_ID,
                selectedRelease = release(),
                graphRevision = ReaderChapterGraphRevision(3),
                document = imageDocument(image("page", "stable/page", "https://cdn.example/page.jpg")),
                imageSourcePolicy = trustedPublicPolicy(),
                sourcePluginId = PluginId("different.plugin"),
            )
        }
    }

    @Test
    fun `text document produces no asset manifest`() {
        val manifest = factory().create(
            sessionId = ReaderSessionId(7),
            storyId = STORY_ID,
            canonicalChapterId = CHAPTER_ID,
            selectedRelease = release(),
            graphRevision = ReaderChapterGraphRevision(3),
            document = ReaderDocument(
                title = "text",
                blocks = listOf(ReaderBlock.Paragraph("paragraph", "body")),
                fingerprint = "text-fingerprint",
            ),
            imageSourcePolicy = trustedPublicPolicy(),
            sourcePluginId = SOURCE_ID,
        )

        assertNull(manifest)
    }

    private fun manifest(
        factory: ReaderAssetManifestFactory,
        document: ReaderDocument,
        policy: ReaderImageSourcePolicy,
    ): ReaderAssetChapterManifest = requireNotNull(
        factory.create(
            sessionId = ReaderSessionId(7),
            storyId = STORY_ID,
            canonicalChapterId = CHAPTER_ID,
            selectedRelease = release(),
            graphRevision = ReaderChapterGraphRevision(3),
            document = document,
            imageSourcePolicy = policy,
            sourcePluginId = SOURCE_ID,
        ),
    )

    private fun factory() = ReaderAssetManifestFactory(
        ReaderRuntimeAssetScopeIdFactory {
            UUID.fromString("00000000-0000-0000-0000-000000000001")
        },
    )

    private fun release() = ChapterRelease(
        id = RELEASE_ID,
        storyId = STORY_ID,
        pluginId = SOURCE_ID,
        sourceStoryId = "source-story",
        sourceReleaseId = "source-release",
        displayLabel = "1",
        parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
        languageTag = "en",
        publishedAtEpochMillis = 1L,
        canonicalChapterId = CHAPTER_ID,
    )

    private fun imageDocument(vararg images: ReaderBlock.ImagePage) = ReaderDocument(
        title = "images",
        blocks = images.toList(),
        fingerprint = "image-fingerprint",
    )

    private fun image(id: String, stableId: String, url: String) = ReaderBlock.ImagePage(id, stableId, url)

    private fun trustedPublicPolicy() = ReaderImageSourcePolicy(
        identityContract = ReaderImageIdentityContract.STABLE_ID_CHANGES_WITH_CONTENT,
        locatorContract = ReaderImageLocatorContract.MUTABLE_OR_UNKNOWN,
        persistenceContract = ReaderImagePersistenceContract.PUBLIC,
    )

    private companion object {
        val STORY_ID = StoryId("story")
        val CHAPTER_ID = CanonicalChapterId("chapter")
        val RELEASE_ID = ChapterReleaseId("release")
        val SOURCE_ID = PluginId("source.plugin")
    }
}
