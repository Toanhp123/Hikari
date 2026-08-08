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
import app.openstory.plugin.api.selector.SelectorDefinitionDecoder
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class SelectorCanonicalFixtureTest {
    @Test
    fun canonicalFixtureExecutesAllEndpointsAgainstDeterministicHtml() = runTest {
        val definition = SelectorDefinitionDecoder().decode(fixtureSource()).getOrThrow()
        val plugins = assertIs<AppResult.Success<SelectorPlugins>>(
            SelectorPluginFactory().create(manifest(), definition, FixtureGateway()),
        ).value
        val catalog = assertNotNull(plugins.catalog)
        val content = assertNotNull(plugins.content)

        assertIs<AppResult.Success<*>>(catalog.home(CatalogHomeRequest()))
        assertIs<AppResult.Success<*>>(catalog.search(CatalogSearchRequest("novel")))
        assertIs<AppResult.Success<*>>(catalog.details("catalog-1"))
        assertIs<AppResult.Success<*>>(catalog.filters())
        assertIs<AppResult.Success<*>>(content.search(ContentSearchRequest("novel")))
        assertIs<AppResult.Success<*>>(content.story("story-1"))
        assertIs<AppResult.Success<*>>(content.latest("story-1", 10))
        assertIs<AppResult.Success<*>>(content.allChapters("story-1"))
        assertIs<AppResult.Success<*>>(content.sync("story-1", "cursor-1"))
        assertIs<AppResult.Success<*>>(content.chapter("release-1"))
    }

    private fun fixtureSource(): String {
        val relative = Path.of("sample-plugins", "selector-fixture", "selector.json")
        val candidates = listOf(
            Path.of(System.getProperty("user.dir")).resolve(relative),
            Path.of(System.getProperty("user.dir")).resolve("../..").resolve(relative).normalize(),
        )
        return Files.readString(checkNotNull(candidates.firstOrNull(Files::isRegularFile)))
    }

    private fun manifest() = PluginManifest(
        id = "fixture.selector",
        name = "Selector Contract Fixture",
        version = "1.0.0",
        packageChecksumSha256 = "a".repeat(64),
        minimumHostVersion = "1.0.0",
        updateUrl = "https://fixture.example/manifest.json",
        api = PluginApiVersion(1, 0),
        kinds = setOf(PluginKind.CATALOG, PluginKind.CONTENT),
        languages = setOf("en"),
        allowedHosts = setOf("fixture.example"),
        capabilities = setOf(PluginCapability.NETWORK),
        runtime = PluginRuntime.DECLARATIVE,
        entry = "selector.json",
        declarativeOrigin = "https://fixture.example/",
    )

    private class FixtureGateway : PluginHttpGateway {
        override suspend fun execute(
            request: PluginHttpRequest,
            budget: RequestBudget,
        ): AppResult<PluginHttpResponse> {
            val html = body(request.url)
            return AppResult.Success(
                PluginHttpResponse(
                    status = 200,
                    headers = emptyMap(),
                    body = html.encodeToByteArray(),
                    decodedText = html,
                ),
            )
        }

        private fun body(url: String): String = when {
            "/home" in url -> """
                <section class="catalog" data-section-id="section-1">
                  <h2>Featured</h2>
                  ${catalogCard()}
                </section>
            """.trimIndent()
            "/search" in url && "/content/" !in url ->
                catalogCard() + "<a class='next'>catalog-cursor</a>"
            "/story/catalog-1" in url -> catalogDetails()
            "/content/search" in url -> contentCandidate() +
                "<a class='next'>content-cursor</a>"
            "/content/story/story-1/sync" in url -> release() +
                "<span class='tombstone'>release-old</span><span class='next-cursor'>cursor-2</span>"
            "/content/story/story-1/latest" in url -> release()
            "/content/story/story-1/chapters" in url -> release()
            "/content/story/story-1" in url -> contentStory()
            "/content/chapter/release-1" in url -> chapter()
            else -> error("Unexpected selector URL")
        }

        private fun catalogCard() = """
            <article class="story" data-id="catalog-1">
              <span class="title">Novel</span><span class="author">Author</span>
              <img class="cover" src="/images/cover.jpg">
              <span class="score-value">8.5</span><span class="score-scale">10</span>
            </article>
        """.trimIndent()

        private fun catalogDetails() = """
            <main data-id="catalog-1" data-url="/story/catalog-1">
              <h1>Novel</h1><span class="alias">Alias</span><span class="author">Author</span>
              <p class="description">Description</p><span class="genre">Fantasy</span>
              <span class="content-type">LIGHT_NOVEL</span><span class="language">en</span>
              <img class="cover" src="/images/cover.jpg">
              <span class="score-value">8.5</span><span class="score-scale">10</span>
              <span class="rank">1</span>
            </main>
        """.trimIndent()

        private fun contentCandidate() = """
            <article class="story" data-id="story-1" data-url="/content/story/story-1">
              <span class="title">Novel</span><span class="author">Author</span>
              <span class="content-type">LIGHT_NOVEL</span><span class="language">en</span>
            </article>
        """.trimIndent()

        private fun contentStory() = """
            <main data-id="story-1" data-url="/content/story/story-1">
              <h1>Novel</h1><span class="alias">Alias</span><span class="author">Author</span>
              <p class="description">Description</p><span class="content-type">LIGHT_NOVEL</span>
              <span class="language">en</span>
              <span class="catalog-mapping" data-plugin-id="fixture.catalog" data-source-id="catalog-1"></span>
            </main>
        """.trimIndent()

        private fun release() = """
            <ul><li class="chapter" data-id="release-1">
              <a href="/content/chapter/release-1"></a><span class="language">en</span>
              <span class="title">Chapter 1</span><span class="volume">1</span>
              <span class="chapter">1</span><span class="part">1</span>
              <span class="kind">NUMBERED</span><span class="normalized-volume">1</span>
              <span class="normalized-chapter">1</span><span class="normalized-part">1</span>
              <span class="normalized-title">Chapter 1</span><span class="translator">Team</span>
              <time class="published">2026-08-07T00:00:00Z</time>
              <time class="updated">2026-08-08T00:00:00Z</time>
              <span class="fingerprint">fingerprint-1</span>
            </li></ul>
        """.trimIndent()

        private fun chapter() = """
            <h1>Chapter 1</h1><main class="chapter">
              <p>Paragraph <em>emphasis</em></p><h2>Heading</h2><hr>
              <img src="/images/chapter.jpg" alt="Illustration"><aside class="note">Note</aside>
            </main>
        """.trimIndent()
    }
}
