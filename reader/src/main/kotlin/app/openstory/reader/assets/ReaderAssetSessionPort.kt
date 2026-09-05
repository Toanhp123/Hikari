package app.openstory.reader.assets

import app.openstory.common.id.CanonicalChapterId
import app.openstory.reader.routing.ReaderSessionId

interface ReaderAssetSessionPort {
    fun registerCommitted(
        sessionId: ReaderSessionId,
        proposedManifestRevision: Long,
        manifest: ReaderAssetChapterManifest,
    ): Long

    fun registerCommittedWithoutManifest(
        sessionId: ReaderSessionId,
        proposedManifestRevision: Long,
        chapterId: CanonicalChapterId,
    ): Long

    fun acceptPrefetchedArtifact(artifact: ReaderPrefetchedDocumentArtifact)

    fun registerSelectedReleaseRefreshPort(
        sessionId: ReaderSessionId,
        port: ReaderSelectedReleaseRefreshPort,
    )

    fun unregisterSelectedReleaseRefreshPort(sessionId: ReaderSessionId)

    fun releaseSession(sessionId: ReaderSessionId)

    companion object {
        val NO_OP: ReaderAssetSessionPort = object : ReaderAssetSessionPort {
            override fun registerCommitted(
                sessionId: ReaderSessionId,
                proposedManifestRevision: Long,
                manifest: ReaderAssetChapterManifest,
            ): Long = proposedManifestRevision

            override fun registerCommittedWithoutManifest(
                sessionId: ReaderSessionId,
                proposedManifestRevision: Long,
                chapterId: CanonicalChapterId,
            ): Long = proposedManifestRevision

            override fun acceptPrefetchedArtifact(artifact: ReaderPrefetchedDocumentArtifact) = Unit

            override fun registerSelectedReleaseRefreshPort(
                sessionId: ReaderSessionId,
                port: ReaderSelectedReleaseRefreshPort,
            ) = Unit

            override fun unregisterSelectedReleaseRefreshPort(sessionId: ReaderSessionId) = Unit

            override fun releaseSession(sessionId: ReaderSessionId) = Unit
        }
    }
}
