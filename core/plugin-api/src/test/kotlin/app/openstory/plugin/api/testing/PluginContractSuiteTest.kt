package app.openstory.plugin.api.testing

import app.openstory.fixtures.plugin.FakeCatalogMode
import app.openstory.fixtures.plugin.FakeCatalogPlugin
import app.openstory.fixtures.plugin.FakeContentMode
import app.openstory.fixtures.plugin.FakeContentPlugin
import app.openstory.fixtures.plugin.UnstableIdContentPlugin
import app.openstory.plugin.api.PluginKind
import app.openstory.plugin.api.PluginManifest
import app.openstory.plugin.api.content.ChapterKindHint
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PluginContractSuiteTest {

    @Test
    fun suiteFlagsUnstableSearchIds() = runTest {
        val report = PluginContractSuite
            .content(UnstableIdContentPlugin())
            .run()

        report.assertViolation(
            code = "content.search.unstable_id",
            method = "content.search",
        )
    }

    @Test
    fun violationNamesExactMethodAndSourceId() = runTest {
        val report = PluginContractSuite
            .content(UnstableIdContentPlugin())
            .run()

        val violation = report.violations.single { item ->
            item.code == "content.search.unstable_id"
        }

        assertEquals("content.search", violation.method)
        assertTrue(
            violation.sourceId
                ?.startsWith("unstable-story-") == true,
        )
    }

    @Test
    fun suiteFlagsDuplicateSearchIds() = runTest {
        val report = suiteFor(
            FakeContentMode.DUPLICATE_SEARCH_IDS,
        ).run()

        report.assertViolation(
            code = "content.search.duplicate_id",
            method = "content.search",
        )
    }

    @Test
    fun suiteFlagsOversizedSearchPages() = runTest {
        val report = suiteFor(
            FakeContentMode.OVERSIZED_SEARCH_PAGE,
        ).run()

        report.assertViolation(
            code = "content.search.oversized_page",
            method = "content.search",
        )
    }

    @Test
    fun suiteFlagsInvalidLanguageTags() = runTest {
        val report = suiteFor(
            FakeContentMode.INVALID_LANGUAGE_TAG,
        ).run()

        report.assertViolation(
            code = "content.search.invalid_language_tag",
            method = "content.search",
        )
    }

    @Test
    fun suiteFlagsUndeclaredStoryHosts() = runTest {
        val report = suiteFor(
            FakeContentMode.UNDECLARED_STORY_HOST,
        ).run()

        report.assertViolation(
            code = "content.story.undeclared_host",
            method = "content.story",
        )
    }

    @Test
    fun suiteFlagsBlankChapterContent() = runTest {
        val report = suiteFor(
            FakeContentMode.BLANK_CHAPTER_CONTENT,
        ).run()

        report.assertViolation(
            code = "content.chapter.blank_content",
            method = "content.chapter",
        )
    }

    @Test
    fun suiteFlagsMalformedSyncCursors() = runTest {
        val report = suiteFor(
            FakeContentMode.MALFORMED_SYNC_CURSOR,
        ).run()

        report.assertViolation(
            code = "content.sync.malformed_cursor",
            method = "content.sync",
        )
    }

    @Test
    fun fixtureModeReturnsRetryableTimeout() = runTest {
        val result = FakeContentPlugin(
            FakeContentMode.TIMEOUT,
        ).search(
            app.openstory.plugin.api.content.ContentSearchRequest(
                query = "fixture",
            ),
        )

        val failure =
            result as app.openstory.common.AppResult.Failure
        val error =
            failure.error as app.openstory.common.AppError.Network

        assertEquals("network.timeout", error.code)
        assertTrue(error.retryable)
    }

    @Test
    fun fixtureModeReturnsRetryableRateLimit() = runTest {
        val result = FakeContentPlugin(
            FakeContentMode.RATE_LIMIT,
        ).search(
            app.openstory.plugin.api.content.ContentSearchRequest(
                query = "fixture",
            ),
        )

        val failure =
            result as app.openstory.common.AppResult.Failure
        val error =
            failure.error as app.openstory.common.AppError.Network

        assertEquals("network.rate_limited", error.code)
        assertTrue(error.retryable)
    }

    @Test
    fun fixtureModeExposesMissingTimestampRelease() = runTest {
        val releases = FakeContentPlugin(
            FakeContentMode.MISSING_TIMESTAMP,
        )
            .latest(
                sourceStoryId = "fixture-story-1",
                limit = 10,
            )
            .getOrNull()

        assertNotNull(releases)

        val release = releases.single()

        assertEquals(null, release.publishedAtEpochMillis)
        assertEquals(null, release.updatedAtEpochMillis)
    }

    @Test
    fun fixtureModeExposesDeletedReleaseTombstone() = runTest {
        val delta = FakeContentPlugin(
            FakeContentMode.DELETED_RELEASE,
        )
            .sync(
                sourceStoryId = "fixture-story-1",
                cursor = null,
            )
            .getOrNull()

        assertNotNull(delta)

        assertEquals(
            setOf("fixture-release-deleted"),
            delta.tombstoneSourceReleaseIds,
        )
    }

    @Test
    fun fixtureModeExposesDuplicateChapterCandidates() = runTest {
        val releases = FakeContentPlugin(
            FakeContentMode.DUPLICATE_CHAPTER,
        )
            .allChapters("fixture-story-1")
            .getOrNull()

        assertNotNull(releases)
        assertEquals(2, releases.size)
        assertEquals(
            2,
            releases.map { it.sourceReleaseId }
                .distinct()
                .size,
        )
        assertEquals(
            1,
            releases.map {
                listOf(
                    it.kindHint.name,
                    it.normalizedVolumeHint,
                    it.normalizedChapterHint,
                    it.normalizedPartHint,
                )
            }
                .distinct()
                .size,
        )
    }

    @Test
    fun fixtureModeExposesSpecialChapter() = runTest {
        val releases = FakeContentPlugin(
            FakeContentMode.SPECIAL_CHAPTER,
        )
            .allChapters("fixture-story-1")
            .getOrNull()

        assertNotNull(releases)

        val release = releases.single()

        assertEquals(
            ChapterKindHint.PROLOGUE,
            release.kindHint,
        )
        assertEquals(null, release.normalizedChapterHint)
        assertEquals("prologue", release.normalizedTitleHint)
    }
    @Test
    fun reportRendersMarkdownAndMachineReadableJson() = runTest {
        val report = PluginContractSuite
            .content(UnstableIdContentPlugin())
            .run()

        val violation = report.violations.single { item ->
            item.code == "content.search.unstable_id"
        }

        val markdown = report.toMarkdown()

        assertTrue("# Plugin Contract Report" in markdown)
        assertTrue(violation.code in markdown)
        assertTrue(violation.method in markdown)
        assertTrue(violation.sourceId.orEmpty() in markdown)

        val json = Json
            .parseToJsonElement(report.toJson())
            .jsonObject

        val encodedViolation = json
            .getValue("violations")
            .jsonArray
            .single()
            .jsonObject

        assertEquals(
            violation.code,
            encodedViolation
                .getValue("code")
                .jsonPrimitive
                .content,
        )

        assertEquals(
            violation.method,
            encodedViolation
                .getValue("method")
                .jsonPrimitive
                .content,
        )

        assertEquals(
            violation.sourceId,
            encodedViolation
                .getValue("sourceId")
                .jsonPrimitive
                .content,
        )
    }

    @Test
    fun sampleCatalogManifestDecodesAndFixturePasses() = runTest {
        val manifest = sampleManifest(
            "sample-plugins/catalog-fixture/manifest.json",
        )

        assertEquals("fixture.catalog", manifest.id)
        assertEquals(setOf(PluginKind.CATALOG), manifest.kinds)

        val report = PluginContractSuite
            .catalog(
                plugin = FakeCatalogPlugin(),
                pluginSourceId = manifest.id,
                allowedHosts = manifest.allowedHosts,
            )
            .run()

        assertTrue(
            report.violations.isEmpty(),
            "Catalog sample violations: ${report.violations}",
        )
    }

    @Test
    fun sampleContentManifestDecodesAndFixturePasses() = runTest {
        val manifest = sampleManifest(
            "sample-plugins/content-fixture/manifest.json",
        )

        assertEquals("fixture.content", manifest.id)
        assertEquals(setOf(PluginKind.CONTENT), manifest.kinds)

        val report = PluginContractSuite
            .content(
                plugin = FakeContentPlugin(),
                pluginSourceId = manifest.id,
                allowedHosts = manifest.allowedHosts,
            )
            .run()

        assertTrue(
            report.violations.isEmpty(),
            "Content sample violations: ${report.violations}",
        )
    }
    @Test
    fun catalogSuiteAcceptsDeterministicFixture() = runTest {
        val report = catalogSuiteFor(
            FakeCatalogMode.NORMAL,
        ).run()

        assertTrue(
            report.violations.isEmpty(),
            "Expected valid catalog fixture but received ${report.violations}",
        )
    }

    @Test
    fun catalogSuiteFlagsUnstableSearchIds() = runTest {
        val report = catalogSuiteFor(
            FakeCatalogMode.UNSTABLE_SEARCH_IDS,
        ).run()

        report.assertViolation(
            code = "catalog.search.unstable_id",
            method = "catalog.search",
        )
    }

    @Test
    fun catalogSuiteFlagsUndeclaredImageHosts() = runTest {
        val report = catalogSuiteFor(
            FakeCatalogMode.UNDECLARED_IMAGE_HOST,
        ).run()

        report.assertViolation(
            code = "catalog.search.undeclared_host",
            method = "catalog.search",
        )
    }

    private fun sampleManifest(
        relativePath: String,
    ): PluginManifest {
        val root = generateSequence(
            File(".").canonicalFile,
        ) { directory ->
            directory.parentFile
        }.first { directory ->
            File(
                directory,
                "settings.gradle.kts",
            ).isFile
        }

        val manifestFile = File(
            root,
            relativePath,
        )

        assertTrue(
            manifestFile.isFile,
            "Missing sample manifest: ${manifestFile.path}",
        )

        return Json.decodeFromString(
            manifestFile.readText(),
        )
    }
    private fun catalogSuiteFor(
        mode: FakeCatalogMode,
    ): PluginContractSuite =
        PluginContractSuite.catalog(
            plugin = FakeCatalogPlugin(mode),
            pluginSourceId = "fixture.catalog",
            allowedHosts = setOf("fixture.example"),
        )
    private fun suiteFor(
        mode: FakeContentMode,
    ): PluginContractSuite =
        PluginContractSuite.content(
            plugin = FakeContentPlugin(mode),
            pluginSourceId = "fixture.content",
            allowedHosts = setOf("fixture.example"),
        )

    private fun ContractReport.assertViolation(
        code: String,
        method: String,
    ) {
        val violation = violations.singleOrNull { item ->
            item.code == code
        }

        assertNotNull(
            violation,
            "Expected violation $code but received $violations",
        )
        assertEquals(method, violation.method)
        assertTrue(
            !violation.sourceId.isNullOrBlank(),
            "Violation $code must identify its offending source.",
        )
    }
}
