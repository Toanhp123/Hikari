package app.openstory.plugin.host.selector.validation

import app.openstory.common.AppResult
import app.openstory.plugin.api.Page
import app.openstory.plugin.api.content.ChapterBlock
import app.openstory.plugin.api.content.ChapterDocument
import app.openstory.plugin.api.content.ChapterSyncDelta
import app.openstory.plugin.api.content.ChapterText
import app.openstory.plugin.api.content.ContentStoryCandidate
import app.openstory.plugin.api.content.ContentStoryDetails
import app.openstory.plugin.api.content.SourceChapterRelease

internal class ContentWireDtoValidator(
    private val support: OutputValidationSupport,
    private val limits: PluginOutputLimits,
) {
    fun validateSearch(
        value: Page<ContentStoryCandidate>,
    ): AppResult<Page<ContentStoryCandidate>> = validateOutput(value) {
        support.requireLimit(value.items.size <= limits.maxOutputItems, "items")
        support.requireUnique(
            value.items.map(ContentStoryCandidate::sourceStoryId),
            "items",
            "sourceStoryId",
        )
        value.items.forEachIndexed { index, item ->
            item.sourceUrl?.let { support.validateUrl(it, "items.$index.sourceUrl") }
        }
    }

    fun validateStory(value: ContentStoryDetails): AppResult<ContentStoryDetails> =
        validateOutput(value) {
            support.validateUrl(value.sourceUrl, "story.sourceUrl")
        }

    fun validateReleases(
        value: List<SourceChapterRelease>,
    ): AppResult<List<SourceChapterRelease>> = validateOutput(value) {
        support.requireLimit(value.size <= limits.maxReleaseItems, "releases")
        validateReleasesOrThrow(value, "releases")
    }

    fun validateSync(value: ChapterSyncDelta): AppResult<ChapterSyncDelta> =
        validateOutput(value) {
            support.requireLimit(value.upserts.size <= limits.maxReleaseItems, "delta.upserts")
            support.requireLimit(
                value.tombstoneSourceReleaseIds.size <= limits.maxTombstoneIds,
                "delta.tombstoneSourceReleaseIds",
            )
            validateReleasesOrThrow(value.upserts, "delta.upserts")
        }

    fun validateChapter(value: ChapterDocument): AppResult<ChapterDocument> =
        validateOutput(value) {
            support.requireLimit(value.blocks.size <= limits.maxChapterBlocks, "blocks")
            var characters = 0
            var spans = 0
            value.blocks.forEachIndexed { index, block ->
                when (block) {
                    is ChapterBlock.Paragraph -> validateText(block.text, index).also {
                        characters += block.text.value.length
                        spans += block.text.spans.size
                    }
                    is ChapterBlock.Heading -> validateText(block.text, index).also {
                        characters += block.text.value.length
                        spans += block.text.spans.size
                    }
                    is ChapterBlock.Note -> validateText(block.text, index).also {
                        characters += block.text.value.length
                        spans += block.text.spans.size
                    }
                    is ChapterBlock.Image -> support.validateUrl(
                        block.reference.url,
                        "blocks.$index.reference.url",
                        block.reference.declaredHost,
                    )
                    ChapterBlock.Divider -> Unit
                }
            }
            support.requireLimit(characters <= limits.maxChapterTextCharacters, "blocks")
            support.requireLimit(spans <= limits.maxTotalSpans, "blocks")
        }

    private fun validateText(text: ChapterText, index: Int) {
        support.requireLimit(
            text.spans.size <= limits.maxSpansPerBlock,
            "blocks.$index.text.spans",
        )
    }

    private fun validateReleasesOrThrow(
        releases: List<SourceChapterRelease>,
        path: String,
    ) {
        support.requireUnique(
            releases.map(SourceChapterRelease::sourceReleaseId),
            path,
            "sourceReleaseId",
        )
        releases.forEachIndexed { index, release ->
            support.validateUrl(release.sourceUrl, "$path.$index.sourceUrl")
        }
    }
}
