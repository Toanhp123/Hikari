package app.openstory.plugin.api.selector.content

import app.openstory.plugin.api.selector.IntegerBinding
import app.openstory.plugin.api.selector.OptionalBinding
import app.openstory.plugin.api.selector.SelectorBinding
import app.openstory.plugin.api.selector.SelectorTextValueBinding
import app.openstory.plugin.api.selector.SelectorTokenKind
import app.openstory.plugin.api.selector.SelectorValidation
import app.openstory.plugin.api.selector.SelectorValidationErrorCode
import app.openstory.plugin.api.selector.UrlBinding
import app.openstory.plugin.api.selector.selectorFail
import app.openstory.plugin.api.selector.validation.SelectorOutputShape
import app.openstory.plugin.api.selector.validation.SelectorOutputValidator
import app.openstory.plugin.api.selector.ListBinding
import app.openstory.plugin.api.selector.ObjectBinding

object ContentSelectorValidator {
    fun validateSearch(selector: ContentSearchSelector): Result<Unit> = runCatching {
        SelectorValidation.validateBinding(selector.items).getOrThrow()
        selector.nextToken?.let {
            SelectorValidation.validateBinding(it).getOrThrow()
            validateTokenBinding(
                binding = it,
                kind = selector.nextTokenKind,
                path = "content.search.nextToken",
            )
        }
        val items =
            requireListBinding(
                selector.items,
                "content.search.items",
            )

        SelectorOutputValidator.validateObject(
            binding =
                requireObjectBinding(
                    items.item,
                    "content.search.items[]",
                ),
            shape = STORY_CANDIDATE_SHAPE,
            path = "content.search.items[]",
        )
    }

    fun validateStory(selector: ContentStorySelector): Result<Unit> = runCatching {
        SelectorValidation.validateBinding(selector.details).getOrThrow()
        SelectorOutputValidator.validateObject(
            binding =
                requireObjectBinding(
                    selector.details,
                    "content.story",
                ),
            shape = STORY_DETAILS_SHAPE,
            path = "content.story",
        )
    }

    fun validateReleases(selector: ContentReleasesSelector): Result<Unit> = runCatching {
        SelectorValidation.validateBinding(selector.releases).getOrThrow()
        val releases =
            requireListBinding(
                selector.releases,
                "content.releases",
            )

        SelectorOutputValidator.validateObject(
            binding =
                requireObjectBinding(
                    releases.item,
                    "content.releases[]",
                ),
            shape = RELEASE_SHAPE,
            path = "content.releases[]",
        )
    }

    fun validateSync(selector: ContentSyncSelector): Result<Unit> = runCatching {
        SelectorValidation.validateBinding(selector.delta).getOrThrow()

        val delta =
            requireObjectBinding(
                selector.delta,
                "content.sync",
            )

        SelectorOutputValidator.validateObject(
            binding = delta,
            shape = SYNC_SHAPE,
            path = "content.sync",
        )

        delta.fields["nextCursor"]?.let {
            validateTokenBinding(
                binding = it,
                kind = selector.nextTokenKind,
                path = "content.sync.nextCursor",
            )
        }
    }

    fun validateChapter(selector: ContentChapterSelector): Result<Unit> = runCatching {
        selector.document.title?.let {
            SelectorValidation.validateBinding(it).getOrThrow()
            requireTextBinding(it, "content.chapter.title")
        }
        validateChapterBlocks(selector.document.blocks)
    }

    private fun validateChapterBlocks(blocks: ChapterBlockListBinding) {
        SelectorValidation.validateCssForContract(blocks.css).getOrThrow()
        if (blocks.variants.isEmpty() || blocks.variants.size > MAX_BLOCK_VARIANTS) {
            selectorFail(
                SelectorValidationErrorCode.INVALID_CONSTANT,
                "Chapter block variant count is invalid.",
            )
        }
        blocks.variants.forEach(::validateBlockVariant)
    }

    private fun validateBlockVariant(variant: ChapterBlockVariantBinding) {
        SelectorValidation.validateCssForContract(variant.matches).getOrThrow()
        when (variant) {
            is ParagraphBlockBinding -> validateChapterText(variant.text)
            is NoteBlockBinding -> validateChapterText(variant.text)
            is DividerBlockBinding -> Unit
            is HeadingBlockBinding -> {
                SelectorValidation.validateBinding(variant.level).getOrThrow()
                if (unwrapOptional(variant.level) !is IntegerBinding) {
                    selectorFail(
                        SelectorValidationErrorCode.OUTPUT_TYPE_MISMATCH,
                        "Heading level binding must produce an integer.",
                    )
                }
                validateChapterText(variant.text)
            }
            is ImageBlockBinding -> {
                SelectorValidation.validateBinding(variant.url).getOrThrow()
                if (unwrapOptional(variant.url) !is UrlBinding) {
                    selectorFail(
                        SelectorValidationErrorCode.OUTPUT_TYPE_MISMATCH,
                        "Chapter image URL binding must produce a URL.",
                    )
                }
                variant.declaredHost?.let {
                    SelectorValidation.validateBinding(it).getOrThrow()
                    requireTextBinding(it, "content.chapter.image.declaredHost")
                }
                variant.altText?.let {
                    SelectorValidation.validateBinding(it).getOrThrow()
                    requireTextBinding(it, "content.chapter.image.altText")
                }
            }
        }
    }

    private fun validateChapterText(text: ChapterTextBinding) {
        SelectorValidation.validateBinding(text.value).getOrThrow()
        requireTextBinding(text.value, "content.chapter.text")
    }

    private fun validateTokenBinding(
        binding: SelectorBinding,
        kind: SelectorTokenKind,
        path: String,
    ) {
        val unwrapped = unwrapOptional(binding)
        val matches = when (kind) {
            SelectorTokenKind.OPAQUE -> unwrapped is SelectorTextValueBinding
            SelectorTokenKind.URL -> unwrapped is UrlBinding
        }
        if (!matches) {
            selectorFail(
                SelectorValidationErrorCode.OUTPUT_TYPE_MISMATCH,
                "Output binding type mismatch at $path.",
            )
        }
    }

    private fun requireTextBinding(
        binding: SelectorBinding,
        path: String,
    ) {
        if (unwrapOptional(binding) !is SelectorTextValueBinding) {
            selectorFail(
                SelectorValidationErrorCode.OUTPUT_TYPE_MISMATCH,
                "Output binding type mismatch at $path.",
            )
        }
    }

    private fun unwrapOptional(binding: SelectorBinding): SelectorBinding =
        if (binding is OptionalBinding) binding.value else binding

    private const val MAX_BLOCK_VARIANTS = 5

    private val DIRECT_MAPPING_SHAPE = SelectorOutputShape.Object(
        required = mapOf(
            "catalogPluginId" to SelectorOutputShape.Text,
            "catalogSourceId" to SelectorOutputShape.Text,
        ),
    )

    private val STORY_CANDIDATE_SHAPE = SelectorOutputShape.Object(
        required = mapOf(
            "sourceStoryId" to SelectorOutputShape.Text,
            "title" to SelectorOutputShape.Text,
            "contentType" to SelectorOutputShape.Enum,
            "languageTags" to SelectorOutputShape.TextSet,
        ),
        optional = mapOf(
            "sourceUrl" to SelectorOutputShape.Url,
            "authors" to SelectorOutputShape.TextList,
        ),
    )

    private val STORY_DETAILS_SHAPE = SelectorOutputShape.Object(
        required = mapOf(
            "sourceStoryId" to SelectorOutputShape.Text,
            "sourceUrl" to SelectorOutputShape.Url,
            "title" to SelectorOutputShape.Text,
            "contentType" to SelectorOutputShape.Enum,
            "languageTags" to SelectorOutputShape.TextSet,
        ),
        optional = mapOf(
            "aliases" to SelectorOutputShape.TextList,
            "authors" to SelectorOutputShape.TextList,
            "description" to SelectorOutputShape.Text,
            "directCatalogMappings" to SelectorOutputShape.List(DIRECT_MAPPING_SHAPE),
        ),
    )

    private val RELEASE_SHAPE = SelectorOutputShape.Object(
        required = mapOf(
            "sourceReleaseId" to SelectorOutputShape.Text,
            "sourceUrl" to SelectorOutputShape.Url,
            "languageTag" to SelectorOutputShape.Text,
            "rawTitle" to SelectorOutputShape.Text,
        ),
        optional = mapOf(
            "rawVolume" to SelectorOutputShape.Text,
            "rawChapter" to SelectorOutputShape.Text,
            "rawPart" to SelectorOutputShape.Text,
            "kindHint" to SelectorOutputShape.Enum,
            "normalizedVolumeHint" to SelectorOutputShape.Text,
            "normalizedChapterHint" to SelectorOutputShape.Text,
            "normalizedPartHint" to SelectorOutputShape.Text,
            "normalizedTitleHint" to SelectorOutputShape.Text,
            "translatorOrUploader" to SelectorOutputShape.Text,
            "publishedAtEpochMillis" to SelectorOutputShape.Timestamp,
            "updatedAtEpochMillis" to SelectorOutputShape.Timestamp,
            "contentFingerprint" to SelectorOutputShape.Text,
        ),
    )

    private val SYNC_SHAPE = SelectorOutputShape.Object(
        required = mapOf(
            "upserts" to SelectorOutputShape.List(RELEASE_SHAPE),
        ),
        optional = mapOf(
            "tombstoneSourceReleaseIds" to SelectorOutputShape.TextSet,
            "nextCursor" to SelectorOutputShape.Token,
        ),
    )
}

private fun requireListBinding(
    binding: SelectorBinding,
    path: String,
): ListBinding =
    binding as? ListBinding
        ?: selectorFail(
            SelectorValidationErrorCode.OUTPUT_TYPE_MISMATCH,
            "Output binding type mismatch at $path.",
        )

private fun requireObjectBinding(
    binding: SelectorBinding,
    path: String,
): ObjectBinding =
    binding as? ObjectBinding
        ?: selectorFail(
            SelectorValidationErrorCode.OUTPUT_TYPE_MISMATCH,
            "Output binding type mismatch at $path.",
        )
