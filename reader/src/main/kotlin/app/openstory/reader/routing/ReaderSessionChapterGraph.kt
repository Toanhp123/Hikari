package app.openstory.reader.routing

import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.repository.CanonicalChapterGroup
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId

internal class ReaderSessionChapterGraph private constructor(
    val groups: List<CanonicalChapterGroup>,
    private val chapterIndexById: Map<CanonicalChapterId, Int>,
    private val groupByChapterId: Map<CanonicalChapterId, CanonicalChapterGroup>,
    private val releaseById: Map<ChapterReleaseId, ChapterRelease>,
    val releaseIds: Set<ChapterReleaseId>,
) {
    fun indexOf(chapterId: CanonicalChapterId): Int? = chapterIndexById[chapterId]

    fun group(chapterId: CanonicalChapterId): CanonicalChapterGroup? = groupByChapterId[chapterId]

    fun previousBefore(chapterId: CanonicalChapterId): CanonicalChapterGroup? =
        chapterIndexById[chapterId]?.let { groups.getOrNull(it - 1) }

    fun nextAfter(chapterId: CanonicalChapterId): CanonicalChapterGroup? =
        chapterIndexById[chapterId]?.let { groups.getOrNull(it + 1) }

    fun release(releaseId: ChapterReleaseId): ChapterRelease? = releaseById[releaseId]

    companion object {
        fun create(
            storyId: StoryId,
            groups: List<CanonicalChapterGroup>,
        ): ReaderSessionChapterGraph {
            val owned = groups.map { group ->
                require(group.chapter.storyId == storyId) {
                    "Reader session chapter graph must contain only story ${storyId.value}."
                }
                require(group.releases.all { it.storyId == storyId }) {
                    "Reader session releases must contain only story ${storyId.value}."
                }
                group.copy(
                    chapter = group.chapter.copy(releaseIds = group.chapter.releaseIds.toSet()),
                    releases = group.releases.toList(),
                )
            }
            val chapterIndexById = linkedMapOf<CanonicalChapterId, Int>()
            val groupByChapterId = linkedMapOf<CanonicalChapterId, CanonicalChapterGroup>()
            val releaseById = linkedMapOf<ChapterReleaseId, ChapterRelease>()
            owned.forEachIndexed { index, group ->
                chapterIndexById.putIfAbsent(group.chapter.id, index)
                groupByChapterId.putIfAbsent(group.chapter.id, group)
                group.releases.forEach { release ->
                    releaseById.putIfAbsent(release.id, release)
                }
            }
            return ReaderSessionChapterGraph(
                groups = owned,
                chapterIndexById = chapterIndexById.toMap(),
                groupByChapterId = groupByChapterId.toMap(),
                releaseById = releaseById.toMap(),
                releaseIds = releaseById.keys.toSet(),
            )
        }
    }
}
