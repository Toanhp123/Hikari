package app.openstory.plugin.host.install

import app.openstory.common.AppResult
import app.openstory.model.ContentType
import app.openstory.network.PluginHttpGateway
import app.openstory.network.PluginHttpRequest
import app.openstory.network.PluginHttpResponse
import app.openstory.network.RequestBudget
import app.openstory.plugin.api.PluginCapability
import app.openstory.plugin.api.PluginManifest
import app.openstory.plugin.api.Page
import app.openstory.plugin.api.catalog.CatalogCard
import app.openstory.plugin.api.catalog.CatalogDetails
import app.openstory.plugin.api.catalog.CatalogFilterDefinition
import app.openstory.plugin.api.catalog.CatalogHomeRequest
import app.openstory.plugin.api.catalog.CatalogSection
import app.openstory.plugin.api.catalog.CatalogSearchRequest
import app.openstory.plugin.api.packageformat.PackageInstallProvenance
import app.openstory.plugin.api.packageformat.PackageInstallSource
import app.openstory.plugin.api.packageformat.PackageSignatureState
import app.openstory.plugin.api.packageformat.PluginPackageMetadata
import app.openstory.plugin.api.selector.SelectorDefinitionDecoder
import app.openstory.plugin.host.selector.runtime.SelectorPluginFactory
import app.openstory.plugin.host.selector.runtime.SelectorPlugins
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class BundledDefaultCatalogPackageTest {
    @Test
    fun bundledAssetPassesPackageAndSelectorValidation() {
        val packageBytes = Files.readAllBytes(repositoryFile(ASSET_RELATIVE_PATH))
        val actualSha256 = packageBytes.sha256()

        assertEquals(DefaultCatalogBundledPlugin.PACKAGE_SHA_256, actualSha256)

        val result = PackageVerifier(
            archiveInspector = ZipPackageArchiveInspector(),
        ).verify(
            InstallRequest(
                packageBytes = packageBytes,
                metadata = PluginPackageMetadata(
                    pluginId = DefaultCatalogBundledPlugin.PLUGIN_ID,
                    version = DefaultCatalogBundledPlugin.VERSION,
                    exactPackageSha256 = actualSha256,
                    signature = null,
                ),
                provenance = PackageInstallProvenance(
                    source = PackageInstallSource.LOCAL_FILE,
                    sourceReference = "asset://${DefaultCatalogBundledPlugin.ASSET_PATH}",
                    signatureState = PackageSignatureState.UNSIGNED,
                    unsignedWarningAcknowledged = true,
                ),
                acceptedCapabilities = setOf(PluginCapability.NETWORK),
            ),
        )

        assertIs<AppResult.Success<VerifiedPluginPackage>>(result)
    }

    @Test
    fun bundledCatalogExecutesHomeAndSearchAgainstDeterministicFixtures() = runTest {
        val manifestSource = Files.readString(repositoryFile(MANIFEST_RELATIVE_PATH))
        val selectorSource = Files.readString(repositoryFile(SELECTOR_RELATIVE_PATH))
        val manifest = Json { ignoreUnknownKeys = true }.decodeFromString(
            PluginManifest.serializer(),
            manifestSource,
        )
        val definition = SelectorDefinitionDecoder().decode(selectorSource).getOrThrow()
        val plugins = assertIs<AppResult.Success<SelectorPlugins>>(
            SelectorPluginFactory().create(
                manifest = manifest,
                definition = definition,
                http = FixtureCatalogGateway(),
            ),
        ).value
        val catalog = assertNotNull(plugins.catalog)

        val sections = assertIs<AppResult.Success<List<CatalogSection>>>(
            catalog.home(CatalogHomeRequest()),
        ).value
        assertEquals(listOf("featured", "popular"), sections.map { it.sourceId })
        assertEquals(ContentType.WEB_NOVEL, sections.first().items.first().contentType)

        val search = assertIs<AppResult.Success<Page<CatalogCard>>>(
            catalog.search(CatalogSearchRequest(query = "hikari")),
        ).value
        assertEquals(
            listOf("hikari-chronicles", "moonlit-archive"),
            search.items.map { it.sourceId },
        )
        assertEquals(null, search.nextToken)

        val details = assertIs<AppResult.Success<CatalogDetails>>(
            catalog.details("hikari-chronicles"),
        ).value
        assertEquals("hikari-chronicles", details.sourceId)
        assertEquals(ContentType.WEB_NOVEL, details.contentType)
        assertEquals(setOf("en"), details.languageTags)

        val filters = assertIs<AppResult.Success<List<CatalogFilterDefinition>>>(
            catalog.filters(),
        ).value
        assertEquals(2, filters.size)
    }
}

private class FixtureCatalogGateway : PluginHttpGateway {
    override suspend fun execute(
        request: PluginHttpRequest,
        budget: RequestBudget,
    ): AppResult<PluginHttpResponse> {
        val fixture = when {
            "/home" in request.url -> HOME_RELATIVE_PATH
            "/search" in request.url -> SEARCH_RELATIVE_PATH
            "/story/" in request.url -> DETAILS_RELATIVE_PATH
            else -> error("Unexpected bundled catalog URL: ${request.url}")
        }
        val html = Files.readString(repositoryFile(fixture))
        return AppResult.Success(
            PluginHttpResponse(
                status = 200,
                headers = emptyMap(),
                body = html.encodeToByteArray(),
                decodedText = html,
            ),
        )
    }
}

private fun repositoryFile(relativePath: String): Path {
    val relative = Path.of(relativePath)
    val userDir = Path.of(System.getProperty("user.dir"))
    val candidates = listOf(
        userDir.resolve(relative),
        userDir.resolve("../..").resolve(relative).normalize(),
    )
    return checkNotNull(candidates.firstOrNull(Files::isRegularFile)) {
        "Missing repository fixture: $relativePath"
    }
}

private fun ByteArray.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }

private const val ASSET_RELATIVE_PATH = "app/src/main/assets/plugins/default-catalog.osp"
private const val MANIFEST_RELATIVE_PATH = "bundled-plugins/default-catalog/manifest.json"
private const val SELECTOR_RELATIVE_PATH = "bundled-plugins/default-catalog/selector.json"
private const val HOME_RELATIVE_PATH = "bundled-plugins/default-catalog/fixtures/home.html"
private const val SEARCH_RELATIVE_PATH = "bundled-plugins/default-catalog/fixtures/search.html"
private const val DETAILS_RELATIVE_PATH = "bundled-plugins/default-catalog/fixtures/details.html"
