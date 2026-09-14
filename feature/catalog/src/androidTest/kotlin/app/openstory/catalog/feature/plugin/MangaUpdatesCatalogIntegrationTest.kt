package app.openstory.catalog.feature.plugin

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.javascriptengine.JavaScriptSandbox
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.asset.SourceAssetPolicy
import app.openstory.catalog.domain.asset.SourceAssetPolicyProvider
import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.catalog.domain.read.DiscoverPersistenceState
import app.openstory.catalog.domain.validation.CatalogAcquisitionValidator
import app.openstory.catalog.runtime.CatalogCapabilityActivation
import app.openstory.catalog.runtime.CatalogRuntimeFactory
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionResult
import app.openstory.catalog.runtime.source.CatalogSourceBinding
import app.openstory.artwork.ArtworkAuthorityKey
import app.openstory.artwork.ArtworkPolicy
import app.openstory.artwork.ArtworkPolicyResolver
import app.openstory.artwork.ArtworkPreflight
import app.openstory.artwork.ArtworkRuntime
import app.openstory.artwork.ArtworkRuntimeCallbacks
import app.openstory.catalog.feature.assets.CoverImagePreflight
import app.openstory.catalog.feature.assets.LocalArtworkLoader
import app.openstory.catalog.feature.assets.LocalCoverAssetResolver
import app.openstory.common.execution.BoundedProcessWorkAdmission
import app.openstory.catalog.feature.discover.DiscoverCardUi
import app.openstory.catalog.feature.discover.DiscoverContentState
import app.openstory.catalog.feature.discover.DiscoverScreen
import app.openstory.catalog.feature.discover.DiscoverTestTags
import app.openstory.catalog.feature.discover.DiscoverSectionUi
import app.openstory.catalog.feature.discover.previewDescriptor
import app.openstory.catalog.feature.discover.DiscoverUiState
import app.openstory.designsystem.theme.HikariTheme
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MangaUpdatesCatalogIntegrationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var appContext: Context
    private lateinit var testContext: Context

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        appContext = instrumentation.targetContext.applicationContext
        testContext = instrumentation.context
        appContext.deleteDatabase(CATALOG_DATABASE_NAME)
        File(appContext.cacheDir, CATALOG_COVER_CACHE).deleteRecursively()
    }

    @After
    fun tearDown() {
        appContext.deleteDatabase(CATALOG_DATABASE_NAME)
        File(appContext.cacheDir, CATALOG_COVER_CACHE).deleteRecursively()
    }

    @Test
    fun javascriptSandboxSupportIsRequiredForAcceptance() {
        assertTrue(JavaScriptSandbox.isSupported())
    }

    @Test
    fun realPluginPublishesExplicitMediaScopedSectionsWithHostOwnedProvenance() = runBlocking {
        val transport = ControlledPluginTransport(::standardResponse)
        val source = ReferencePluginBridge.fromAssets(testContext, transport)
        val binding = binding(source)
        val session = CatalogRuntimeFactory(
            context = appContext,
            binding = binding,
            wallClockEpochMs = { ACQUIRED_AT },
        ).createSession()
        try {
            val activation = session.activate() as CatalogCapabilityActivation.Available

            assertEquals(CatalogAcquisitionResult.Success, activation.acquireDiscover(CatalogMediaType.MANGA))
            assertEquals(
                CatalogAcquisitionResult.Success,
                activation.acquireDiscover(CatalogMediaType.LIGHT_NOVEL),
            )

            val manga = activation.publishedDiscover(CatalogMediaType.MANGA)
            val lightNovel = activation.publishedDiscover(CatalogMediaType.LIGHT_NOVEL)
            assertEquals(CatalogSectionKind.entries, manga.cards.map { it.sectionKind })
            assertEquals(CatalogSectionKind.entries, lightNovel.cards.map { it.sectionKind })
            assertTrue(manga.cards.all { it.contentType == CatalogMediaType.MANGA })
            assertTrue(lightNovel.cards.all { it.contentType == CatalogMediaType.LIGHT_NOVEL })
            assertFalse(
                (manga.cards + lightNovel.cards).any {
                    it.ref.sourceStoryId in setOf(
                        WEB_NOVEL_ID,
                        ANIME_ID,
                        UNKNOWN_MANGA_FALLBACK_ID,
                        UNSUPPORTED_NOVEL_ID,
                    )
                },
            )
            assertEquals(SOURCE_KEY, manga.provenance.catalogSourceKey)
            assertEquals(SOURCE_VERSION, manga.provenance.sourceVersion)
            assertEquals(ACQUIRED_AT, manga.provenance.acquiredAtEpochMs)
            val cover = manga.cards.first().coverLocator as CoverLocator.RemoteHttps
            assertEquals(SOURCE_KEY, cover.catalogSourceKey)
            assertEquals("cdn.mangaupdates.com", java.net.URI(cover.normalizedUri.value).host)

            composeRule.setContent {
                HikariTheme(darkTheme = false) {
                    DiscoverScreen(
                        mediaType = CatalogMediaType.MANGA,
                        state = manga.toDiscoverUiState(),
                        listState = rememberLazyListState(),
                        onStorySelected = { _ -> },
                        onRefresh = {},
                        onRetry = {},
                    )
                }
            }
            listOf(
                CatalogSectionKind.POPULAR to "Trending Now",
                CatalogSectionKind.LATEST_UPDATES to "Latest Updates",
                CatalogSectionKind.TOP_RATED to "Top Rated",
            ).forEach { (kind, title) ->
                composeRule.onNodeWithTag(DiscoverTestTags.ROOT)
                    .performScrollToNode(hasTestTag(DiscoverTestTags.section(kind)))
                composeRule.onNodeWithText(title).assertIsDisplayed()
            }
        } finally {
            session.close()
            source.close()
        }
    }

    @Test
    fun realPluginDetailsUseExactRouteIdentityAndPersistUnchangedMetadata() = runBlocking {
        val transport = ControlledPluginTransport(::standardResponse)
        val source = ReferencePluginBridge.fromAssets(testContext, transport)
        val session = CatalogRuntimeFactory(
            context = appContext,
            binding = binding(source),
            wallClockEpochMs = { ACQUIRED_AT },
        ).createSession()
        try {
            val activation = session.activate() as CatalogCapabilityActivation.Available
            val ref = storyRef(MANGA_ID)

            val storySession = activation.storyDetailSession(ref)
            val states = storySession.activate()
            assertEquals(CatalogAcquisitionResult.Success, storySession.retry())
            val projection = states.first { it.projection?.detail != null }.projection!!
            assertEquals(MANGA_ID, projection.ref.sourceStoryId)
            assertEquals("Manga Alpha", projection.summary.title)
            assertEquals(listOf("Author One"), projection.detail!!.authors)
            assertEquals(listOf("Action", "Adventure"), projection.detail!!.genres)
            assertEquals("A stable description", projection.detail!!.description)
            assertTrue(transport.pluginRequests.any { it.url.endsWith("/v1/series/$MANGA_ID") })

            storySession.release()
        } finally {
            session.close()
            source.close()
        }
    }

    @Test
    fun pluginAndBoundaryFailuresRetainPreviouslyPublishedContent() = runBlocking {
        var response: (ControlledPluginRequest) -> ControlledPluginResponse = ::standardResponse
        val transport = ControlledPluginTransport(handler = { request -> response(request) })
        val source = ReferencePluginBridge.fromAssets(testContext, transport)
        val session = CatalogRuntimeFactory(
            context = appContext,
            binding = binding(source),
            wallClockEpochMs = { ACQUIRED_AT },
        ).createSession()
        try {
            val activation = session.activate() as CatalogCapabilityActivation.Available
            assertEquals(CatalogAcquisitionResult.Success, activation.acquireDiscover(CatalogMediaType.MANGA))
            val initial = activation.publishedDiscover(CatalogMediaType.MANGA)

            response = { ControlledPluginResponse(status = 503, body = "{}") }
            assertAcquisitionFailure(activation.acquireDiscover(CatalogMediaType.MANGA))
            assertEquals(initial, activation.publishedDiscover(CatalogMediaType.MANGA))

            val overLimitBodies = listOf(
                homeBody(sourceId = "9".repeat(513)),
                homeBody(title = "T".repeat(1_025)),
                homeBody(coverUrl = "https://cdn.mangaupdates.com/${"c".repeat(4_100)}"),
            )
            overLimitBodies.forEach { body ->
                response = { request -> homeResponse(request, body) }
                val result = activation.acquireDiscover(CatalogMediaType.MANGA)
                assertValidationFailure(result)
                assertEquals(initial, activation.publishedDiscover(CatalogMediaType.MANGA))
            }

            val ref = storyRef(MANGA_ID)
            val previouslyPublishedCard = initial.cards.first {
                it.sectionKind == CatalogSectionKind.POPULAR && it.ref == ref
            }
            response = { ControlledPluginResponse(200, detailsBody(authors = List(33) { "Author $it" })) }
            val storySession = activation.storyDetailSession(ref)
            val storyStates = storySession.activate()
            assertValidationFailure(storySession.retry())
            val retainedSummary = storyStates.first { it.projection != null }.projection!!
            assertEquals(previouslyPublishedCard.title, retainedSummary.summary.title)
            assertEquals(previouslyPublishedCard.coverAssetKey, retainedSummary.summary.coverAssetKey)
            assertEquals(previouslyPublishedCard.coverLocator, retainedSummary.summary.coverLocator)
            assertEquals(null, retainedSummary.detail)
            storySession.release()
        } finally {
            session.close()
            source.close()
        }
    }

    @Test
    fun realPluginDetailOutputIsRejectedByStricterStepTwoBounds() = runBlocking {
        var detailBody = detailsBody()
        val transport = ControlledPluginTransport(
            handler = { ControlledPluginResponse(status = 200, body = detailBody) },
        )
        val source = ReferencePluginBridge.fromAssets(testContext, transport)
        val ref = storyRef(MANGA_ID)
        try {
            listOf(
                detailsBody(authors = List(33) { "Author $it" }),
                detailsBody(genres = List(65) { "Genre $it" }),
                detailsBody(description = "D".repeat(65 * 1_024)),
            ).forEach { body ->
                detailBody = body
                val acquisition = source.acquireStoryDetail(ref)
                try {
                    CatalogAcquisitionValidator.requireValidStoryDetail(ref, acquisition)
                    fail("Expected a stricter Step 2 validation failure")
                } catch (failure: CatalogFailureException) {
                    assertTrue(failure.failure is CatalogFailure.Validation)
                }
            }
        } finally {
            source.close()
        }
    }

    @Test
    fun explicitUnsupportedWireContentTypesAreNeverCoerced() = runBlocking {
        val executor = ReferencePluginExecutor(
            evaluator = ReferenceJavaScriptEvaluator { _, _, _, _, _ -> unsupportedContentOutput() },
        )
        val source = ReferencePluginBridge.fromAssets(
            context = testContext,
            transport = ControlledPluginTransport(::standardResponse),
            executor = executor,
        )
        try {
            CatalogMediaType.entries.forEach { mediaType ->
                val acquisition = source.acquireDiscover(mediaType)
                assertTrue(acquisition.sections.flatMap { it.items }.isEmpty())
            }
        } finally {
            source.close()
        }
    }

    @Test
    fun realPluginCoverRendersInDiscoverAndStoryProjectionKeepsArtworkIdentity() = runBlocking {
        val encodedCover = png(width = 48, height = 72)
        val transport = ControlledPluginTransport(
            handler = ::standardResponse,
            coverHandler = {
                ControlledCoverResponse(
                    statusCode = 200,
                    contentType = "image/png",
                    bytes = encodedCover,
                )
            },
        )
        val source = ReferencePluginBridge.fromAssets(testContext, transport)
        val policy = SourceAssetPolicy(SOURCE_KEY, ALLOWED_HOSTS)
        val session = CatalogRuntimeFactory(
            context = appContext,
            binding = CatalogSourceBinding(
                catalogSourceKey = SOURCE_KEY,
                sourceVersion = SOURCE_VERSION,
                discoverCapability = source,
                storyCapability = source,
                assetPolicy = policy,
            ),
            wallClockEpochMs = { ACQUIRED_AT },
        ).createSession()
        val decodeCount = AtomicInteger()
        val loader = ArtworkRuntime(
            context = appContext,
            localResolver = LocalCoverAssetResolver { _, _ -> null },
            remoteTransport = transport,
            policyResolver = ArtworkPolicyResolver { authority ->
                ArtworkPolicy(authority, policy.allowedHttpsHosts)
                    .takeIf { authority.value == policy.catalogSourceKey.value }
            },
            admission = BoundedProcessWorkAdmission(),
            preflight = ArtworkPreflight { file, mediaType, size ->
                CoverImagePreflight().inspect(file, mediaType, size)
            },
            callbacks = ArtworkRuntimeCallbacks(
                onSuccessfulDecode = { decodeCount.incrementAndGet() },
            ),
        )
        try {
            val activation = session.activate() as CatalogCapabilityActivation.Available
            assertEquals(CatalogAcquisitionResult.Success, activation.acquireDiscover(CatalogMediaType.MANGA))
            val discover = activation.publishedDiscover(CatalogMediaType.MANGA)
            val card = discover.cards.first { it.ref.sourceStoryId == MANGA_ID }

            val storySession = activation.storyDetailSession(card.ref)
            val storyStates = storySession.activate()
            assertEquals(CatalogAcquisitionResult.Success, storySession.retry())
            val story = storyStates.first { it.projection?.detail != null }.projection!!
            assertEquals(card.coverAssetKey, story.summary.coverAssetKey)
            assertEquals(card.coverLocator, story.summary.coverLocator)

            val coverReady = AtomicInteger()
            composeRule.setContent {
                HikariTheme(darkTheme = false) {
                    CompositionLocalProvider(LocalArtworkLoader provides loader) {
                        DiscoverScreen(
                            mediaType = CatalogMediaType.MANGA,
                            state = DiscoverUiState(
                                content = DiscoverContentState.Content(
                                    sections = listOf(
                                        DiscoverSectionUi(
                                            CatalogSectionKind.POPULAR.previewDescriptor(),
                                            listOf(
                                                DiscoverCardUi(
                                                    ref = card.ref,
                                                    title = card.title,
                                                    coverAssetKey = card.coverAssetKey,
                                                    ratingLabel = null,
                                                    supportingLabel = null,
                                                    coverLocator = card.coverLocator,
                                                ),
                                            ),
                                        ),
                                    ),
                                    refreshing = false,
                                    issue = null,
                                ),
                            ),
                            listState = rememberLazyListState(),
                            onStorySelected = { _ -> },
                            onRefresh = {},
                            onRetry = {},
                            onCoverReady = { coverReady.incrementAndGet() },
                        )
                    }
                }
            }
            composeRule.waitUntil(10_000) { coverReady.get() == 1 && decodeCount.get() == 1 }
            assertEquals(1, transport.coverRequestCount.get())
            storySession.release()
        } finally {
            loader.close()
            session.close()
            source.close()
        }
    }

    private fun binding(source: ReferencePluginBridge) = CatalogSourceBinding(
        catalogSourceKey = SOURCE_KEY,
        sourceVersion = SOURCE_VERSION,
        discoverCapability = source,
        storyCapability = source,
        assetPolicy = SourceAssetPolicy(SOURCE_KEY, ALLOWED_HOSTS),
    )

    private suspend fun CatalogCapabilityActivation.Available.publishedDiscover(
        mediaType: CatalogMediaType,
    ): DiscoverPersistenceState.Published = discoverSession(mediaType).states
        .first { it.persistence is DiscoverPersistenceState.Published }
        .persistence as DiscoverPersistenceState.Published

    private fun DiscoverPersistenceState.Published.toDiscoverUiState() = DiscoverUiState(
        content = DiscoverContentState.Content(
            sections = CatalogSectionKind.entries.map { kind ->
                DiscoverSectionUi(
                    descriptor = kind.previewDescriptor(),
                    cards = cards.filter { it.sectionKind == kind }.map { card ->
                        DiscoverCardUi(
                            ref = card.ref,
                            title = card.title,
                            coverAssetKey = card.coverAssetKey,
                            ratingLabel = null,
                            supportingLabel = null,
                            coverLocator = card.coverLocator,
                        )
                    },
                )
            },
            refreshing = false,
            issue = null,
        ),
    )

    private fun assertAcquisitionFailure(result: CatalogAcquisitionResult) {
        assertTrue(result is CatalogAcquisitionResult.Failed)
    }

    private fun assertValidationFailure(result: CatalogAcquisitionResult) {
        val failure = (result as? CatalogAcquisitionResult.Failed)?.failure
        assertTrue("Expected validation failure, got $failure", failure is CatalogFailure.Validation)
    }

    private fun png(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try {
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    companion object {
        const val CATALOG_DATABASE_NAME = "hikari-v2-catalog.db"
        const val CATALOG_COVER_CACHE = "catalog-cover-cache"
        const val SOURCE_VERSION = "host-v17"
        const val ACQUIRED_AT = 1_789_200_000_000L
        const val MANGA_ID = "101"
        const val LIGHT_NOVEL_ID = "202"
        const val WEB_NOVEL_ID = "303"
        const val ANIME_ID = "404"
        const val UNKNOWN_MANGA_FALLBACK_ID = "505"
        const val UNSUPPORTED_NOVEL_ID = "606"
        val SOURCE_KEY = CatalogSourceKey("org.openstory.catalog.mangaupdates")
        val ALLOWED_HOSTS = setOf(
            "api.mangaupdates.com",
            "cdn.mangaupdates.com",
            "mangaupdates.com",
            "www.mangaupdates.com",
        )
    }
}

internal fun storyRef(sourceStoryId: String) = StorySourceRef(
    storyId = SourceStoryIdV1.derive(SourceStoryKey(MangaUpdatesCatalogIntegrationTest.SOURCE_KEY, sourceStoryId)),
    catalogSourceKey = MangaUpdatesCatalogIntegrationTest.SOURCE_KEY,
    sourceStoryId = sourceStoryId,
)

internal fun standardResponse(request: ControlledPluginRequest): ControlledPluginResponse = when {
    request.url.endsWith("/v1/series/${MangaUpdatesCatalogIntegrationTest.MANGA_ID}") ->
        ControlledPluginResponse(200, detailsBody())
    request.url.contains("/v1/releases/days") -> ControlledPluginResponse(200, releaseHomeBody())
    request.body.orEmpty().contains("\"orderby\":\"week_pos\"") ->
        ControlledPluginResponse(200, searchHomeBody("Popular"))
    request.body.orEmpty().contains("\"orderby\":\"rating\"") ->
        ControlledPluginResponse(200, searchHomeBody("Rated"))
    else -> ControlledPluginResponse(404, "{}")
}

private fun homeResponse(
    request: ControlledPluginRequest,
    body: String,
): ControlledPluginResponse = if (request.url.contains("/v1/releases/days")) {
    ControlledPluginResponse(200, releaseHomeBody())
} else {
    ControlledPluginResponse(200, body)
}

private fun searchHomeBody(label: String) = """
    {"results":[
      {"record":${seriesJson(MangaUpdatesCatalogIntegrationTest.MANGA_ID, "Manga $label", "Manga")}},
      {"record":${seriesJson(MangaUpdatesCatalogIntegrationTest.LIGHT_NOVEL_ID, "Novel $label", "Novel")}},
      {"record":${seriesJson(MangaUpdatesCatalogIntegrationTest.WEB_NOVEL_ID, "Web $label", "Web Novel")}},
      {"record":${seriesJson(MangaUpdatesCatalogIntegrationTest.ANIME_ID, "Anime $label", "Anime")}},
      {"record":${seriesJson(MangaUpdatesCatalogIntegrationTest.UNKNOWN_MANGA_FALLBACK_ID, "Audio $label", "Audio Drama")}},
      {"record":${seriesJson(MangaUpdatesCatalogIntegrationTest.UNSUPPORTED_NOVEL_ID, "Visual $label", "Visual Novel")}}
    ]}
""".trimIndent()

private fun releaseHomeBody() = """
    {"results":[
      ${releaseJson(MangaUpdatesCatalogIntegrationTest.MANGA_ID, "Manga Latest", "Manga")},
      ${releaseJson(MangaUpdatesCatalogIntegrationTest.LIGHT_NOVEL_ID, "Novel Latest", "Novel")},
      ${releaseJson(MangaUpdatesCatalogIntegrationTest.WEB_NOVEL_ID, "Web Latest", "Web Novel")},
      ${releaseJson(MangaUpdatesCatalogIntegrationTest.ANIME_ID, "Anime Latest", "Anime")},
      ${releaseJson(MangaUpdatesCatalogIntegrationTest.UNKNOWN_MANGA_FALLBACK_ID, "Audio Latest", "Audio Drama")},
      ${releaseJson(MangaUpdatesCatalogIntegrationTest.UNSUPPORTED_NOVEL_ID, "Visual Latest", "Visual Novel")}
    ]}
""".trimIndent()

private fun homeBody(
    sourceId: String = MangaUpdatesCatalogIntegrationTest.MANGA_ID,
    title: String = "Manga Alpha",
    coverUrl: String = "https://cdn.mangaupdates.com/cover-alpha.png",
) = """{"results":[{"record":${seriesJson(sourceId, title, "Manga", coverUrl)}}]}"""

private fun detailsBody(
    authors: List<String> = listOf("Author One"),
    genres: List<String> = listOf("Action", "Adventure"),
    description: String = "A stable description",
) = """
    {
      "series_id":${MangaUpdatesCatalogIntegrationTest.MANGA_ID},
      "title":"Manga Alpha",
      "type":"Manga",
      "url":"https://www.mangaupdates.com/series/manga-alpha",
      "image":{"url":{"thumb":"https://cdn.mangaupdates.com/cover-${MangaUpdatesCatalogIntegrationTest.MANGA_ID}.png"}},
      "authors":${stringObjects(authors)},
      "genres":${stringObjects(genres, "genre")},
      "description":${jsonString(description)},
      "status":"Ongoing"
    }
""".trimIndent()

private fun seriesJson(
    sourceId: String,
    title: String,
    type: String,
    coverUrl: String = "https://cdn.mangaupdates.com/cover-$sourceId.png",
) = """
    {
      "series_id":"$sourceId",
      "title":${jsonString(title)},
      "type":"$type",
      "image":{"url":{"thumb":${jsonString(coverUrl)}}},
      "authors":[{"name":"Author One"}],
      "genres":[{"genre":"Action"}],
      "bayesian_rating":8.5,
      "status":"Ongoing",
      "catalogSourceKey":"payload-must-not-win",
      "sourceVersion":"payload-must-not-win",
      "acquiredAt":-1
    }
""".trimIndent()

private fun releaseJson(sourceId: String, title: String, type: String) = """
    {
      "series_id":"$sourceId",
      "series":${seriesJson(sourceId, title, type)},
      "time_added":{"as_rfc3339":"2026-09-13T00:00:00Z"},
      "volume":"1",
      "chapter":"2"
    }
""".trimIndent()

private fun stringObjects(values: List<String>, key: String = "name"): String =
    values.joinToString(prefix = "[", postfix = "]") { value -> "{\"$key\":${jsonString(value)}}" }

private fun jsonString(value: String): String = kotlinx.serialization.json.Json.encodeToString(value)

private fun unsupportedContentOutput(): String = """
    {
      "sections":[
        {
          "sourceId":"unsupported",
          "title":"Unsupported",
          "kind":"POPULAR",
          "items":[
            {"sourceId":"web-1","title":"Web","contentType":"WEB_NOVEL"},
            {"sourceId":"anime-1","title":"Anime","contentType":"ANIME"}
          ]
        }
      ]
    }
""".trimIndent()
