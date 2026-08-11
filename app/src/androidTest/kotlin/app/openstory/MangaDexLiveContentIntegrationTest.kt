package app.openstory

import androidx.javascriptengine.JavaScriptSandbox
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.repository.CatalogDetailsMutation
import app.openstory.common.Outcome
import app.openstory.common.SystemClock
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.di.PluginRuntimeEntryPoint
import app.openstory.library.content.PluginContentSourceRegistry
import app.openstory.library.mapping.ContentMappingOrigin
import app.openstory.library.mapping.ContentMappingSearchPolicy
import app.openstory.library.mapping.ContentMappingSearchReport
import app.openstory.library.mapping.ContentMappingSearchService
import app.openstory.library.mapping.ContentMappingService
import app.openstory.library.matching.ContentMatchDecision
import app.openstory.library.matching.ContentStoryMatcher
import app.openstory.plugins.api.manifest.PluginService
import app.openstory.plugins.runtime.PluginCallResult
import app.openstory.plugins.runtime.PluginRuntime
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.catalog.RoomCatalogRepository
import app.openstory.storage.room.catalog.RoomCatalogStoryProjectionRepository
import app.openstory.storage.room.library.RoomContentMappingRepository
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MangaDexLiveContentIntegrationTest {
    @Test
    fun liveMangaDexPluginMapsCanonicalStoryAndProtectsUserUrl() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString(MANGADEX_LIVE_ARGUMENT) == "true")
        assumeTrue(JavaScriptSandbox.isSupported())
        val testContext = instrumentation.context
        val appContext = instrumentation.targetContext.applicationContext
        val database = OpenStoryDatabase.open(appContext)
        try {
            val installation = ensureMangaDexBundledPluginInstalled(
                context = appContext,
                database = database,
                packageBytes = mangaDexPackageBytes(testContext),
            )
            assertTrue("MangaDex reference plugin install failed", installation is PluginCallResult.Success)

            val runtime = EntryPointAccessors.fromApplication(appContext, PluginRuntimeEntryPoint::class.java).runtime()
            val enabledContent = runtime.enabled(PluginService.CONTENT)
            assertTrue(
                "MangaDex CONTENT plugin not enabled: ${enabledContent.map { it.pluginId.value to it.version }}",
                enabledContent.any { it.pluginId.value == MANGADEX_PLUGIN_ID },
            )
            verifyMappingFlow(database, runtime)
        } finally {
            database.close()
        }
    }
}

private suspend fun verifyMappingFlow(
    database: OpenStoryDatabase,
    runtime: PluginRuntime,
) {
    val nonce = System.currentTimeMillis()
    val storyId = StoryId("$E2E_STORY_ID:$nonce")
    val catalog = RoomCatalogRepository(database)
    val committed = catalog.commitDetails(
        CatalogDetailsMutation(
            storyId = storyId,
            entry = CatalogEntry(
                storyId = storyId,
                pluginId = PluginId(E2E_CATALOG_PLUGIN_ID),
                sourceId = "one-piece-$nonce",
                title = "One Piece",
                contentType = ContentType.MANGA,
            ),
            pluginVersion = E2E_CATALOG_PLUGIN_VERSION,
            fetchedAtEpochMillis = nonce,
        ),
    )
    assertTrue("Canonical story seed failed", committed is Outcome.Success<*>)

    val mappingService = mappingService(database, runtime)
    val automatedReport = mappingService.automate(storyId)
    assertTrue(
        automatedReport.liveDiagnostic("Live MangaDex mapping search failed"),
        automatedReport.failures.isEmpty(),
    )
    val automated = mappingService.observe(storyId).first()
        .single { mapping -> mapping.pluginId.value == MANGADEX_PLUGIN_ID }
    assertEquals(ContentMappingOrigin.AUTOMATED, automated.origin)
    val candidate = automatedReport.candidates.first { result ->
        result.pluginId.value == MANGADEX_PLUGIN_ID && result.sourceStoryId == automated.sourceStoryId
    }
    assertEquals(ContentMatchDecision.AUTO_LINK, candidate.match.decision)
    assertTrue(candidate.title.equals("One Piece", ignoreCase = true))
    val sourceUrl = checkNotNull(candidate.sourceUrl)

    val resolvedReport = mappingService.resolveUrl(storyId, sourceUrl)
    assertTrue(
        resolvedReport.liveDiagnostic("Live MangaDex URL resolution failed"),
        resolvedReport.failures.isEmpty(),
    )
    val resolvedCandidate = resolvedReport.candidates.single { it.pluginId.value == MANGADEX_PLUGIN_ID }
    assertEquals(automated.sourceStoryId, resolvedCandidate.sourceStoryId)
    mappingService.acceptUrl(storyId, resolvedCandidate)
    assertProtectedMapping(mappingService, storyId, automated.sourceStoryId)

    mappingService.automate(storyId)
    assertProtectedMapping(mappingService, storyId, automated.sourceStoryId)
}

private fun mappingService(
    database: OpenStoryDatabase,
    runtime: PluginRuntime,
) = ContentMappingService(
    repository = RoomContentMappingRepository(database),
    search = ContentMappingSearchService(
        projections = RoomCatalogStoryProjectionRepository(database),
        sources = PluginContentSourceRegistry(runtime, Json),
        matcher = ContentStoryMatcher(),
        policy = ContentMappingSearchPolicy(
            quickSourceTimeoutMillis = LIVE_SOURCE_TIMEOUT_MILLIS,
            deferredSourceTimeoutMillis = LIVE_SOURCE_TIMEOUT_MILLIS,
        ),
    ),
    clock = SystemClock,
)

private fun ContentMappingSearchReport.liveDiagnostic(label: String): String {
    val searched = searchedPluginIds.joinToString(prefix = "[", postfix = "]") { it.value }
    val failureSummary = failures.joinToString(prefix = "[", postfix = "]") { failure ->
        "${failure.pluginId?.value ?: "global"}:${failure.code}:retryable=${failure.retryable}"
    }
    val candidateSummary = candidates.joinToString(prefix = "[", postfix = "]") { candidate ->
        "${candidate.pluginId.value}:${candidate.title}:${candidate.match.decision}:score=${candidate.match.score}"
    }
    return buildString {
        append(label)
        append(" searched=").append(searched)
        append(" failures=").append(failureSummary)
        append(" candidates=").append(candidateSummary)
        append(" queries=").append(queryVariants)
    }
}

private suspend fun assertProtectedMapping(
    service: ContentMappingService,
    storyId: StoryId,
    expectedSourceStoryId: String,
) {
    val mapping = service.observe(storyId).first()
        .single { value -> value.pluginId.value == MANGADEX_PLUGIN_ID }
    assertEquals(ContentMappingOrigin.USER_URL, mapping.origin)
    assertEquals(expectedSourceStoryId, mapping.sourceStoryId)
}

private const val MANGADEX_LIVE_ARGUMENT = "openstoryLiveMangaDex"
private const val E2E_STORY_ID = "e2e:mangadex:one-piece"
private const val E2E_CATALOG_PLUGIN_ID = "org.openstory.catalog.e2e"
private const val E2E_CATALOG_PLUGIN_VERSION = "1.0.0"
private const val LIVE_SOURCE_TIMEOUT_MILLIS = 15_000L
