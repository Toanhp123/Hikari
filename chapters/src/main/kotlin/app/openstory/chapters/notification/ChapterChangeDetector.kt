package app.openstory.chapters.notification

import app.openstory.chapters.repository.ChapterGraphSnapshot
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class ChapterChangeDetector {
    fun detect(
        before: ChapterGraphSnapshot,
        after: ChapterGraphSnapshot,
        chapterCommitFingerprint: String,
        occurredAtEpochMillis: Long,
    ): List<ChapterChangeFact> {
        require(chapterCommitFingerprint.isNotBlank())
        require(occurredAtEpochMillis >= 0L)

        val previousChapters = before.chapters.associateBy { it.id }
        val previousReleases = before.releases.associateBy { it.id }
        val facts = buildList {
            after.chapters.asSequence()
                .filterNot { it.tombstoned }
                .forEach { chapter ->
                    val previous = previousChapters[chapter.id]
                    val kind = when {
                        previous == null -> ChapterChangeKind.CANONICAL_CHAPTER_CREATED
                        previous.tombstoned -> ChapterChangeKind.CANONICAL_CHAPTER_RESTORED
                        else -> null
                    }
                    if (kind != null) {
                        add(
                            fact(
                                storyId = chapter.storyId.value,
                                chapterId = chapter.id.value,
                                releaseId = null,
                                kind = kind,
                                fingerprint = chapterCommitFingerprint,
                                occurredAt = occurredAtEpochMillis,
                            ),
                        )
                    }
                }

            after.releases.forEach { release ->
                val chapterId = release.canonicalChapterId ?: return@forEach
                val previousChapterId = previousReleases[release.id]?.canonicalChapterId
                if (previousChapterId != chapterId) {
                    add(
                        fact(
                            storyId = release.storyId.value,
                            chapterId = chapterId.value,
                            releaseId = release.id.value,
                            kind = ChapterChangeKind.RELEASE_LINKED,
                            fingerprint = chapterCommitFingerprint,
                            occurredAt = occurredAtEpochMillis,
                        ),
                    )
                }
            }
        }
        return facts.distinctBy(ChapterChangeFact::eventKey).sortedWith(
            compareBy<ChapterChangeFact> { it.storyId.value }
                .thenBy { it.chapterId.value }
                .thenBy { if (it.kind == ChapterChangeKind.RELEASE_LINKED) 1 else 0 }
                .thenBy { it.kind.ordinal }
                .thenBy { it.releaseId?.value.orEmpty() },
        )
    }

    private fun fact(
        storyId: String,
        chapterId: String,
        releaseId: String?,
        kind: ChapterChangeKind,
        fingerprint: String,
        occurredAt: Long,
    ): ChapterChangeFact {
        val eventKey = sha256(
            listOf(storyId, chapterId, releaseId.orEmpty(), kind.name, fingerprint).joinToString("\u001f"),
        )
        return ChapterChangeFact(
            eventKey = eventKey,
            storyId = app.openstory.common.id.StoryId(storyId),
            chapterId = app.openstory.common.id.CanonicalChapterId(chapterId),
            releaseId = releaseId?.let { app.openstory.common.id.ChapterReleaseId(it) },
            kind = kind,
            chapterCommitFingerprint = fingerprint,
            occurredAtEpochMillis = occurredAt,
        )
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and UNSIGNED_BYTE_MASK) }

    private companion object {
        const val UNSIGNED_BYTE_MASK = 0xff
    }
}
