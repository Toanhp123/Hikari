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

internal fun assetManifest(
    sessionId: Long,
    chapter: String,
    pageCount: Int,
    locatorTag: String = "v1",
    persistenceContract: ReaderImagePersistenceContract = ReaderImagePersistenceContract.PUBLIC,
): ReaderAssetChapterManifest {
    val storyId = StoryId("story")
    val chapterId = CanonicalChapterId(chapter)
    val pluginId = PluginId("plugin")
    val release = ChapterRelease(
        id = ChapterReleaseId("release-$chapter"),
        storyId = storyId,
        pluginId = pluginId,
        sourceStoryId = "source-story",
        sourceReleaseId = "source-$chapter",
        displayLabel = chapter,
        parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
        languageTag = "en",
        publishedAtEpochMillis = 1L,
        canonicalChapterId = chapterId,
    )
    val document = ReaderDocument(
        title = chapter,
        blocks = List(pageCount) { ordinal ->
            ReaderBlock.ImagePage(
                id = "image-$ordinal",
                stableAssetId = "$chapter/page-$ordinal",
                imageUrl = "https://cdn.example/$locatorTag/$chapter/$ordinal.jpg",
            )
        },
        fingerprint = "fingerprint-$chapter-$locatorTag",
    )
    return requireNotNull(
        ReaderAssetManifestFactory(
            ReaderRuntimeAssetScopeIdFactory {
                UUID.nameUUIDFromBytes("$sessionId-$chapter-$locatorTag".toByteArray())
            },
        ).create(
            sessionId = ReaderSessionId(sessionId),
            storyId = storyId,
            canonicalChapterId = chapterId,
            selectedRelease = release,
            graphRevision = ReaderAssetGraphRevision(1),
            document = document,
            imageSourcePolicy = ReaderImageSourcePolicy(
                identityContract = ReaderImageIdentityContract.DELIVERY_STABLE_ONLY,
                locatorContract = ReaderImageLocatorContract.LOCATOR_CHANGES_WITH_CONTENT,
                persistenceContract = persistenceContract,
            ),
            sourcePluginId = pluginId,
        ),
    )
}

internal fun viewport(
    manifest: ReaderAssetChapterManifest,
    leading: Int,
    trailing: Int,
    direction: ReaderViewportDirection = ReaderViewportDirection.FORWARD,
    progress: Int = 5_000,
) = ReaderViewportSnapshot(
    sessionId = manifest.sessionId,
    manifestRevision = 1,
    leadingVisibleImageOrdinal = leading,
    trailingVisibleImageOrdinal = trailing,
    direction = direction,
    chapterProgressBasisPoints = progress,
)

internal fun request(
    manifest: ReaderAssetChapterManifest,
    revision: Long,
    ordinal: Int,
) = ReaderPageAssetRequest(
    sessionId = manifest.sessionId,
    manifestRevision = revision,
    descriptor = manifest.descriptors[ordinal],
)

internal fun prefetchedArtifact(
    currentManifest: ReaderAssetChapterManifest,
    targetChapter: String,
    token: Long,
): ReaderPrefetchedDocumentArtifact {
    val prefetched = assetManifest(
        sessionId = currentManifest.sessionId.value,
        chapter = targetChapter,
        pageCount = 6,
    )
    return ReaderPrefetchedDocumentArtifact(
        sessionId = currentManifest.sessionId,
        prefetchToken = token,
        graphRevision = ReaderAssetGraphRevision(2),
        targetChapterId = prefetched.canonicalChapterId,
        selectedRelease = ChapterRelease(
            id = prefetched.selectedReleaseId,
            storyId = prefetched.storyId,
            pluginId = PluginId(prefetched.sourceNamespace.value),
            sourceStoryId = "source-story",
            sourceReleaseId = "source-$targetChapter",
            displayLabel = targetChapter,
            parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
            languageTag = "en",
            publishedAtEpochMillis = 1L,
            canonicalChapterId = prefetched.canonicalChapterId,
        ),
        document = ReaderDocument(
            title = targetChapter,
            blocks = prefetched.descriptors.map { descriptor ->
                ReaderBlock.ImagePage(
                    descriptor.uiBlockId,
                    descriptor.stableAssetId,
                    descriptor.deliveryLocator,
                )
            },
            fingerprint = "prefetched-$targetChapter",
        ),
        imageSourcePolicy = ReaderImageSourcePolicy(
            identityContract = ReaderImageIdentityContract.DELIVERY_STABLE_ONLY,
            locatorContract = ReaderImageLocatorContract.LOCATOR_CHANGES_WITH_CONTENT,
            persistenceContract = ReaderImagePersistenceContract.PUBLIC,
        ),
        sourcePluginId = PluginId(prefetched.sourceNamespace.value),
    )
}
