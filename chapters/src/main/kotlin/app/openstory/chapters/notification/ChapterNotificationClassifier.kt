package app.openstory.chapters.notification

class ChapterNotificationClassifier {
    @Suppress("ReturnCount")
    fun classify(
        fact: ChapterChangeFact,
        context: ChapterNotificationContext?,
        policy: ChapterNotificationPolicy,
        coveredByNewChapterEvent: Boolean = false,
    ): ChapterNotificationDecision {
        context ?: return ChapterNotificationDecision.Consume("notification.target_missing")
        if (context.chapterTombstoned) {
            return ChapterNotificationDecision.Consume("notification.chapter_tombstoned")
        }
        if (context.chapterRead) {
            return ChapterNotificationDecision.Consume("notification.chapter_already_read")
        }

        return when (fact.kind) {
            ChapterChangeKind.CANONICAL_CHAPTER_CREATED,
            ChapterChangeKind.CANONICAL_CHAPTER_RESTORED,
            -> classifyNewChapter(context, policy)

            ChapterChangeKind.RELEASE_LINKED -> classifyRelease(fact, context, policy, coveredByNewChapterEvent)
        }
    }

    private fun classifyNewChapter(
        context: ChapterNotificationContext,
        policy: ChapterNotificationPolicy,
    ): ChapterNotificationDecision {
        if (!policy.notifyNewCanonicalChapters) {
            return ChapterNotificationDecision.Consume("notification.new_chapters_disabled")
        }
        val preferred = preferredRelease(context, policy.contentLanguageOrder)
        return ChapterNotificationDecision.Publish(
            ChapterNotificationCandidate(
                context.storyId,
                context.chapterId,
                preferred?.releaseId,
                preferred?.languageTag,
            ),
        )
    }

    @Suppress("ReturnCount")
    private fun classifyRelease(
        fact: ChapterChangeFact,
        context: ChapterNotificationContext,
        policy: ChapterNotificationPolicy,
        coveredByNewChapterEvent: Boolean,
    ): ChapterNotificationDecision {
        if (coveredByNewChapterEvent) {
            return ChapterNotificationDecision.Consume("notification.covered_by_new_chapter")
        }
        if (!policy.notifyPreferredLanguageReleases) {
            return ChapterNotificationDecision.Consume("notification.preferred_releases_disabled")
        }
        val preferred = preferredRelease(context, policy.contentLanguageOrder)
            ?: return ChapterNotificationDecision.Consume("notification.no_preferred_language_release")
        if (fact.releaseId != preferred.releaseId) {
            return ChapterNotificationDecision.Consume("notification.release_not_preferred")
        }
        return ChapterNotificationDecision.Publish(
            ChapterNotificationCandidate(
                context.storyId,
                context.chapterId,
                preferred.releaseId,
                preferred.languageTag,
            ),
        )
    }

    private fun preferredRelease(
        context: ChapterNotificationContext,
        languageOrder: List<String>,
    ): ChapterNotificationRelease? {
        val normalized = languageOrder.map(String::trim).map(String::lowercase).filter(String::isNotBlank)
        return normalized.firstNotNullOfOrNull { language ->
            context.releases.firstOrNull { it.languageTag.trim().lowercase() == language }
        }
    }
}
