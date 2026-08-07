package app.openstory.plugin.api.testing

import app.openstory.common.AppResult
import app.openstory.plugin.api.Page
import app.openstory.plugin.api.catalog.CatalogCard
import app.openstory.plugin.api.catalog.CatalogPlugin
import app.openstory.plugin.api.catalog.CatalogSearchRequest
import app.openstory.plugin.api.content.ChapterBlock
import app.openstory.plugin.api.content.ChapterDocument
import app.openstory.plugin.api.content.ContentPlugin
import app.openstory.plugin.api.content.ContentSearchRequest
import app.openstory.plugin.api.content.ContentStoryCandidate
import java.net.URI

data class ContractViolation(
    val code: String,
    val method: String,
    val sourceId: String?,
)

data class ContractReport(
    val violations: List<ContractViolation>,
)

class PluginContractSuite private constructor(
    private val contractRunner: suspend () -> ContractReport,
) {
    suspend fun run(): ContractReport =
        contractRunner()

    companion object {
        fun content(
            plugin: ContentPlugin,
            pluginSourceId: String = "fixture.content",
            allowedHosts: Set<String> =
                setOf("fixture.example"),
        ): PluginContractSuite =
            PluginContractSuite {
                ContentContractRunner(
                    plugin = plugin,
                    pluginSourceId = pluginSourceId,
                    allowedHosts = allowedHosts,
                ).run()
            }

        fun catalog(
            plugin: CatalogPlugin,
            pluginSourceId: String = "fixture.catalog",
            allowedHosts: Set<String> =
                setOf("fixture.example"),
        ): PluginContractSuite =
            PluginContractSuite {
                CatalogContractRunner(
                    plugin = plugin,
                    pluginSourceId = pluginSourceId,
                    allowedHosts = allowedHosts,
                ).run()
            }
    }
}

private class ContentContractRunner(
    private val plugin: ContentPlugin,
    private val pluginSourceId: String,
    allowedHosts: Set<String>,
) {
    private val normalizedAllowedHosts =
        allowedHosts.map(String::lowercase).toSet()

    suspend fun run(): ContractReport {
        val violations =
            mutableListOf<ContractViolation>()

        val (firstPage, secondPage) =
            inspectSearch(violations)

        validateStableSearchIds(
            firstPage = firstPage,
            secondPage = secondPage,
            violations = violations,
        )

        validateSearchLanguages(
            page = firstPage,
            violations = violations,
        )

        val sourceStoryId =
            firstPage
                ?.items
                ?.firstOrNull()
                ?.sourceStoryId
                ?: pluginSourceId

        validateStory(
            sourceStoryId = sourceStoryId,
            violations = violations,
        )

        val sourceReleaseId =
            loadReleaseId(sourceStoryId)

        validateSync(
            sourceStoryId = sourceStoryId,
            violations = violations,
        )

        validateChapter(
            sourceReleaseId = sourceReleaseId,
            violations = violations,
        )

        return ContractReport(
            violations =
                violations.distinctViolations(),
        )
    }

    private suspend fun inspectSearch(
        violations: MutableList<ContractViolation>,
    ): Pair<
        Page<ContentStoryCandidate>?,
        Page<ContentStoryCandidate>?,
    > {
        val request = ContentSearchRequest(
            query = "fixture-story",
            nextToken = null,
        )

        val firstSearch = runCatching {
            plugin.search(request)
        }

        val secondSearch = runCatching {
            plugin.search(request)
        }

        firstSearch.exceptionOrNull()?.let { error ->
            violations += searchExceptionViolation(error)
        }

        secondSearch.exceptionOrNull()?.let { error ->
            violations += searchExceptionViolation(error)
        }

        return firstSearch
            .getOrNull()
            ?.successValueOrNull() to
            secondSearch
                .getOrNull()
                ?.successValueOrNull()
    }

    private fun validateStableSearchIds(
        firstPage: Page<ContentStoryCandidate>?,
        secondPage: Page<ContentStoryCandidate>?,
        violations: MutableList<ContractViolation>,
    ) {
        if (firstPage == null || secondPage == null) {
            return
        }

        val firstIds =
            firstPage.items.map { it.sourceStoryId }

        val secondIds =
            secondPage.items.map { it.sourceStoryId }

        if (firstIds != secondIds) {
            violations += ContractViolation(
                code = "content.search.unstable_id",
                method = "content.search",
                sourceId =
                    secondIds.firstOrNull()
                        ?: firstIds.firstOrNull()
                        ?: pluginSourceId,
            )
        }
    }

    private fun validateSearchLanguages(
        page: Page<ContentStoryCandidate>?,
        violations: MutableList<ContractViolation>,
    ) {
        val invalidCandidate = page
            ?.items
            ?.firstOrNull { candidate ->
                candidate.languageTags.any { languageTag ->
                    !LANGUAGE_TAG_PATTERN.matches(
                        languageTag,
                    )
                }
            }
            ?: return

        violations += ContractViolation(
            code =
                "content.search.invalid_language_tag",
            method = "content.search",
            sourceId =
                invalidCandidate.sourceStoryId,
        )
    }

    private suspend fun validateStory(
        sourceStoryId: String,
        violations: MutableList<ContractViolation>,
    ) {
        val details = runCatching {
            plugin.story(sourceStoryId)
        }
            .getOrNull()
            ?.successValueOrNull()
            ?: return

        if (!isAllowedUrl(details.sourceUrl)) {
            violations += ContractViolation(
                code =
                    "content.story.undeclared_host",
                method = "content.story",
                sourceId = details.sourceStoryId,
            )
        }
    }

    private suspend fun loadReleaseId(
        sourceStoryId: String,
    ): String =
        runCatching {
            plugin.latest(
                sourceStoryId = sourceStoryId,
                limit = 1,
            )
        }
            .getOrNull()
            ?.successValueOrNull()
            ?.firstOrNull()
            ?.sourceReleaseId
            ?: DEFAULT_SOURCE_RELEASE_ID

    private suspend fun validateSync(
        sourceStoryId: String,
        violations: MutableList<ContractViolation>,
    ) {
        val delta = runCatching {
            plugin.sync(
                sourceStoryId = sourceStoryId,
                cursor = null,
            )
        }
            .getOrNull()
            ?.successValueOrNull()
            ?: return

        val cursor = delta.nextCursor ?: return

        if (!CURSOR_PATTERN.matches(cursor)) {
            violations += ContractViolation(
                code =
                    "content.sync.malformed_cursor",
                method = "content.sync",
                sourceId = sourceStoryId,
            )
        }
    }

    private suspend fun validateChapter(
        sourceReleaseId: String,
        violations: MutableList<ContractViolation>,
    ) {
        val document = runCatching {
            plugin.chapter(sourceReleaseId)
        }
            .getOrNull()
            ?.successValueOrNull()
            ?: return

        if (document.hasNoReadableContent()) {
            violations += ContractViolation(
                code =
                    "content.chapter.blank_content",
                method = "content.chapter",
                sourceId = sourceReleaseId,
            )
        }
    }

    private fun searchExceptionViolation(
        error: Throwable,
    ): ContractViolation =
        pageExceptionViolation(
            error = error,
            method = "content.search",
            codePrefix = "content.search",
            pluginSourceId = pluginSourceId,
        )

    private fun isAllowedUrl(
        url: String,
    ): Boolean {
        val host = urlHost(url) ?: return false

        return host in normalizedAllowedHosts
    }
}

private class CatalogContractRunner(
    private val plugin: CatalogPlugin,
    private val pluginSourceId: String,
    allowedHosts: Set<String>,
) {
    private val normalizedAllowedHosts =
        allowedHosts.map(String::lowercase).toSet()

    suspend fun run(): ContractReport {
        val violations =
            mutableListOf<ContractViolation>()

        val (firstPage, secondPage) =
            inspectSearch(violations)

        validateStableSearchIds(
            firstPage = firstPage,
            secondPage = secondPage,
            violations = violations,
        )

        validateImageHosts(
            page = firstPage,
            violations = violations,
        )

        return ContractReport(
            violations =
                violations.distinctViolations(),
        )
    }

    private suspend fun inspectSearch(
        violations: MutableList<ContractViolation>,
    ): Pair<Page<CatalogCard>?, Page<CatalogCard>?> {
        val request = CatalogSearchRequest(
            query = "fixture-story",
            nextToken = null,
        )

        val firstSearch = runCatching {
            plugin.search(request)
        }

        val secondSearch = runCatching {
            plugin.search(request)
        }

        firstSearch.exceptionOrNull()?.let { error ->
            violations += searchExceptionViolation(error)
        }

        secondSearch.exceptionOrNull()?.let { error ->
            violations += searchExceptionViolation(error)
        }

        return firstSearch
            .getOrNull()
            ?.successValueOrNull() to
            secondSearch
                .getOrNull()
                ?.successValueOrNull()
    }

    private fun validateStableSearchIds(
        firstPage: Page<CatalogCard>?,
        secondPage: Page<CatalogCard>?,
        violations: MutableList<ContractViolation>,
    ) {
        if (firstPage == null || secondPage == null) {
            return
        }

        val firstIds =
            firstPage.items.map { it.sourceId }

        val secondIds =
            secondPage.items.map { it.sourceId }

        if (firstIds != secondIds) {
            violations += ContractViolation(
                code = "catalog.search.unstable_id",
                method = "catalog.search",
                sourceId =
                    secondIds.firstOrNull()
                        ?: firstIds.firstOrNull()
                        ?: pluginSourceId,
            )
        }
    }

    private fun validateImageHosts(
        page: Page<CatalogCard>?,
        violations: MutableList<ContractViolation>,
    ) {
        page
            ?.items
            ?.forEach { card ->
                val image =
                    card.image ?: return@forEach

                val actualHost =
                    urlHost(image.url)

                val declaredHost =
                    image.declaredHost.lowercase()

                if (
                    actualHost == null ||
                    actualHost != declaredHost ||
                    actualHost !in normalizedAllowedHosts
                ) {
                    violations += ContractViolation(
                        code =
                            "catalog.search.undeclared_host",
                        method = "catalog.search",
                        sourceId = card.sourceId,
                    )
                }
            }
    }

    private fun searchExceptionViolation(
        error: Throwable,
    ): ContractViolation =
        pageExceptionViolation(
            error = error,
            method = "catalog.search",
            codePrefix = "catalog.search",
            pluginSourceId = pluginSourceId,
        )
}

private fun pageExceptionViolation(
    error: Throwable,
    method: String,
    codePrefix: String,
    pluginSourceId: String,
): ContractViolation {
    val message = error.message.orEmpty()

    val suffix = when {
        "unique stable source IDs" in message ->
            "duplicate_id"

        "more than 100 items" in message ->
            "oversized_page"

        "non-blank stable source IDs" in message ->
            "blank_id"

        "Continuation token" in message ->
            "malformed_cursor"

        "normalized lowercase language tag" in message ->
            "invalid_language_tag"

        else ->
            "exception"
    }

    return ContractViolation(
        code = "$codePrefix.$suffix",
        method = method,
        sourceId = pluginSourceId,
    )
}

private fun <T> AppResult<T>.successValueOrNull(): T? =
    when (this) {
        is AppResult.Success -> value
        is AppResult.Failure -> null
    }

private fun List<ContractViolation>.distinctViolations():
    List<ContractViolation> =
    distinctBy { violation ->
        Triple(
            violation.code,
            violation.method,
            violation.sourceId,
        )
    }

private fun urlHost(
    url: String,
): String? =
    runCatching {
        URI(url).host?.lowercase()
    }.getOrNull()

private fun ChapterDocument.hasNoReadableContent(): Boolean =
    blocks.none { block ->
        when (block) {
            is ChapterBlock.Paragraph ->
                block.text.value.isNotBlank()

            is ChapterBlock.Heading ->
                block.text.value.isNotBlank()

            is ChapterBlock.Note ->
                block.text.value.isNotBlank()

            is ChapterBlock.Image ->
                block.reference.url.isNotBlank()

            ChapterBlock.Divider ->
                false
        }
    }

private const val DEFAULT_SOURCE_RELEASE_ID =
    "fixture-release-1"

private val LANGUAGE_TAG_PATTERN =
    Regex(
        """[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*""",
    )

private val CURSOR_PATTERN =
    Regex("""[A-Za-z0-9._~+/=-]{1,512}""")
