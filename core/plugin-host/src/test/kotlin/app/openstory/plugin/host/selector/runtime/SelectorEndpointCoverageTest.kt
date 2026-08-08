package app.openstory.plugin.host.selector.runtime

import app.openstory.common.AppResult
import app.openstory.network.PluginHttpGateway
import app.openstory.network.PluginHttpRequest
import app.openstory.network.PluginHttpResponse
import app.openstory.network.RequestBudget
import app.openstory.plugin.api.PluginApiVersion
import app.openstory.plugin.api.PluginCapability
import app.openstory.plugin.api.PluginKind
import app.openstory.plugin.api.PluginManifest
import app.openstory.plugin.api.PluginRuntime
import app.openstory.plugin.api.catalog.CatalogHomeRequest
import app.openstory.plugin.api.catalog.CatalogSearchRequest
import app.openstory.plugin.api.content.ContentSearchRequest
import app.openstory.plugin.api.selector.AttributeBinding
import app.openstory.plugin.api.selector.EnumBinding
import app.openstory.plugin.api.selector.HttpGet
import app.openstory.plugin.api.selector.ListBinding
import app.openstory.plugin.api.selector.ObjectBinding
import app.openstory.plugin.api.selector.SelectorDefinition
import app.openstory.plugin.api.selector.SelectorRequestPlan
import app.openstory.plugin.api.selector.SelectorValidation
import app.openstory.plugin.api.selector.TextBinding
import app.openstory.plugin.api.selector.TextSetBinding
import app.openstory.plugin.api.selector.UrlBinding
import app.openstory.plugin.api.selector.catalog.CatalogDetailsSelector
import app.openstory.plugin.api.selector.catalog.CatalogFiltersSelector
import app.openstory.plugin.api.selector.catalog.CatalogHomeSelector
import app.openstory.plugin.api.selector.catalog.CatalogSearchSelector
import app.openstory.plugin.api.selector.catalog.CatalogSelectorEndpoints
import app.openstory.plugin.api.selector.catalog.CatalogTextFilterBinding
import app.openstory.plugin.api.selector.content.ChapterBlockListBinding
import app.openstory.plugin.api.selector.content.ChapterDocumentBinding
import app.openstory.plugin.api.selector.content.ChapterTextBinding
import app.openstory.plugin.api.selector.content.ContentChapterSelector
import app.openstory.plugin.api.selector.content.ContentReleasesSelector
import app.openstory.plugin.api.selector.content.ContentSearchSelector
import app.openstory.plugin.api.selector.content.ContentSelectorEndpoints
import app.openstory.plugin.api.selector.content.ContentStorySelector
import app.openstory.plugin.api.selector.content.ContentSyncSelector
import app.openstory.plugin.api.selector.content.ParagraphBlockBinding
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SelectorEndpointCoverageTest {
    @Test
    fun allCatalogAndContentEndpointsReturnWireDtos() = runTest {
        val validation = SelectorValidation.validate(definition(), manifest())
        assertTrue(validation.isSuccess, validation.exceptionOrNull()?.message)
        val plugins = assertIs<AppResult.Success<SelectorPlugins>>(
            SelectorPluginFactory().create(manifest(), definition(), RoutingGateway()),
        ).value
        val catalog = assertNotNull(plugins.catalog)
        val content = assertNotNull(plugins.content)

        assertIs<AppResult.Success<*>>(catalog.home(CatalogHomeRequest()))
        assertIs<AppResult.Success<*>>(catalog.search(CatalogSearchRequest("novel")))
        val details = catalog.details("catalog-1")
        assertTrue(details is AppResult.Success, (details as? AppResult.Failure)?.error.toString())
        assertIs<AppResult.Success<*>>(catalog.filters())
        assertIs<AppResult.Success<*>>(content.search(ContentSearchRequest("novel")))
        assertIs<AppResult.Success<*>>(content.story("story-1"))
        assertIs<AppResult.Success<*>>(content.latest("story-1", 10))
        assertIs<AppResult.Success<*>>(content.allChapters("story-1"))
        assertIs<AppResult.Success<*>>(content.sync("story-1", null))
        assertIs<AppResult.Success<*>>(content.chapter("release-1"))
    }

    private fun definition() = SelectorDefinition(
        catalog = CatalogSelectorEndpoints(
            home = CatalogHomeSelector(request("/home"), sectionList()),
            search = CatalogSearchSelector(request("/search?q={query}"), cardList()),
            details = CatalogDetailsSelector(request("/story/{sourceId}"), catalogDetails()),
            filters = CatalogFiltersSelector(
                listOf(CatalogTextFilterBinding("author", "Author", null)),
            ),
        ),
        content = ContentSelectorEndpoints(
            search = ContentSearchSelector(request("/content/search?q={query}"), candidateList()),
            story = ContentStorySelector(request("/content/story/{sourceStoryId}"), storyDetails()),
            latest = ContentReleasesSelector(
                request("/content/story/{sourceStoryId}/latest?limit={limit}"),
                releaseList(),
            ),
            allChapters = ContentReleasesSelector(
                request("/content/story/{sourceStoryId}/chapters"),
                releaseList(),
            ),
            sync = ContentSyncSelector(
                request("/content/story/{sourceStoryId}/sync?cursor={cursor}"),
                ObjectBinding(linkedMapOf("upserts" to releaseList())),
            ),
            chapter = ContentChapterSelector(
                request("/content/chapter/{sourceReleaseId}"),
                ChapterDocumentBinding(
                    blocks = ChapterBlockListBinding(
                        css = ".chapter > p",
                        variants = listOf(
                            ParagraphBlockBinding("p", ChapterTextBinding(TextBinding())),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun sectionList() = ListBinding(
        css = "section.catalog",
        item = ObjectBinding(
            linkedMapOf(
                "sourceId" to AttributeBinding(attribute = "data-id"),
                "title" to TextBinding("h2"),
                "items" to cardList(),
            ),
        ),
    )

    private fun cardList() = ListBinding(
        css = "article.story",
        item = ObjectBinding(
            linkedMapOf(
                "sourceId" to AttributeBinding(attribute = "data-id"),
                "title" to TextBinding(".title"),
                "contentType" to EnumBinding(TextBinding(".content-type")),
            ),
        ),
    )

    private fun catalogDetails() = ObjectBinding(
        linkedMapOf(
            "sourceId" to AttributeBinding("main", "data-id"),
            "title" to TextBinding("main h1"),
            "contentType" to EnumBinding(TextBinding("main .content-type")),
            "languageTags" to TextSetBinding("main .language"),
        ),
    )

    private fun candidateList() = ListBinding(
        css = "article.story",
        item = ObjectBinding(
            linkedMapOf(
                "sourceStoryId" to AttributeBinding(attribute = "data-id"),
                "title" to TextBinding(".title"),
                "contentType" to EnumBinding(TextBinding(".content-type")),
                "languageTags" to TextSetBinding(".language"),
            ),
        ),
    )

    private fun storyDetails() = ObjectBinding(
        linkedMapOf(
            "sourceStoryId" to AttributeBinding("main", "data-id"),
            "sourceUrl" to UrlBinding(AttributeBinding("main", "data-url")),
            "title" to TextBinding("main h1"),
            "contentType" to EnumBinding(TextBinding("main .content-type")),
            "languageTags" to TextSetBinding("main .language"),
        ),
    )

    private fun releaseList() = ListBinding(
        css = "li.chapter",
        item = ObjectBinding(
            linkedMapOf(
                "sourceReleaseId" to AttributeBinding(attribute = "data-id"),
                "sourceUrl" to UrlBinding(AttributeBinding("a", "href")),
                "languageTag" to TextBinding(".language"),
                "rawTitle" to TextBinding(".title"),
            ),
        ),
    )

    private fun request(url: String) = SelectorRequestPlan(listOf(HttpGet(url)))

    private fun manifest() = PluginManifest(
        id = "community.complete",
        name = "Complete",
        version = "1.0.0",
        packageChecksumSha256 = "b".repeat(64),
        minimumHostVersion = "1.0.0",
        updateUrl = "https://allowed.example/update.json",
        api = PluginApiVersion(1, 0),
        kinds = setOf(PluginKind.CATALOG, PluginKind.CONTENT),
        languages = setOf("en"),
        allowedHosts = setOf("allowed.example"),
        capabilities = setOf(PluginCapability.NETWORK),
        runtime = PluginRuntime.DECLARATIVE,
        entry = "selector.json",
        declarativeOrigin = "https://allowed.example/",
    )

    private class RoutingGateway : PluginHttpGateway {
        override suspend fun execute(
            request: PluginHttpRequest,
            budget: RequestBudget,
        ): AppResult<PluginHttpResponse> = AppResult.Success(
            PluginHttpResponse(
                status = 200,
                headers = emptyMap(),
                body = body(request.url).encodeToByteArray(),
                decodedText = body(request.url),
            ),
        )

        private fun body(url: String): String = when {
            "/home" in url -> """
                <section class='catalog' data-id='section-1'>
                  <h2>Featured</h2>
                  <article class='story' data-id='catalog-1'><span class='title'>Novel</span><span class='content-type'>LIGHT_NOVEL</span></article>
                </section>
            """.trimIndent()
            "/search" in url && "/content/" !in url ->
                "<article class='story' data-id='catalog-1'><span class='title'>Novel</span><span class='content-type'>LIGHT_NOVEL</span></article>"
            "/story/catalog-1" in url -> """
                <main data-id='catalog-1'><h1>Novel</h1><span class='content-type'>LIGHT_NOVEL</span><span class='language'>en</span></main>
            """.trimIndent()
            "/content/search" in url -> """
                <article class='story' data-id='story-1'><span class='title'>Novel</span><span class='content-type'>LIGHT_NOVEL</span><span class='language'>en</span></article>
            """.trimIndent()
            "/content/story/story-1/sync" in url -> releaseHtml()
            "/content/story/story-1/latest" in url -> releaseHtml()
            "/content/story/story-1/chapters" in url -> releaseHtml()
            "/content/story/story-1" in url -> """
                <main data-id='story-1' data-url='/content/story/story-1'><h1>Novel</h1><span class='content-type'>LIGHT_NOVEL</span><span class='language'>en</span></main>
            """.trimIndent()
            "/content/chapter/release-1" in url ->
                "<main class='chapter'><p>Chapter body</p></main>"
            else -> error("Unexpected selector URL")
        }

        private fun releaseHtml() = """
            <ul><li class='chapter' data-id='release-1'><a href='/content/chapter/release-1'></a><span class='language'>en</span><span class='title'>Chapter 1</span></li></ul>
        """.trimIndent()
    }
}
