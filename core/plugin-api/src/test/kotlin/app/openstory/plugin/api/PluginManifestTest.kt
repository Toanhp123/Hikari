package app.openstory.plugin.api

import kotlin.test.Test
import kotlin.test.assertFailsWith

class PluginManifestTest {

    @Test
    fun manifestRejectsUndeclaredWildcardDomain() {
        assertFailsWith<IllegalArgumentException> {
            validManifest(
                allowedHosts = setOf("*.com"),
            )
        }
    }

    @Test
    fun manifestRejectsInvalidPluginId() {
        assertFailsWith<IllegalArgumentException> {
            validManifest(
                id = "Invalid Plugin ID",
            )
        }
    }

    @Test
    fun manifestRejectsInvalidSemanticVersion() {
        assertFailsWith<IllegalArgumentException> {
            validManifest(
                version = "version-one",
            )
        }
    }

    @Test
    fun manifestRejectsNonNormalizedHost() {
        assertFailsWith<IllegalArgumentException> {
            validManifest(
                allowedHosts = setOf("Example.COM"),
            )
        }
    }

    @Test
    fun manifestRejectsHostWithoutDomainSuffix() {
        assertFailsWith<IllegalArgumentException> {
            validManifest(
                allowedHosts = setOf("localhost"),
            )
        }
    }
    @Test
    fun manifestRejectsBlankName() {
        assertFailsWith<IllegalArgumentException> {
            validManifest(
                name = "   ",
            )
        }
    }
    @Test
    fun manifestRejectsParentTraversalEntry() {
        assertFailsWith<IllegalArgumentException> {
            validManifest(
                entry = "../main.js",
            )
        }
    }
    @Test
    fun manifestRejectsAbsoluteEntry() {
        assertFailsWith<IllegalArgumentException> {
            validManifest(
                entry = "/main.js",
            )
        }
    }

    @Test
    fun manifestRejectsBlankEntry() {
        assertFailsWith<IllegalArgumentException> {
            validManifest(
                entry = "   ",
            )
        }
    }

    @Test
    fun manifestRejectsHostWithScheme() {
        assertFailsWith<IllegalArgumentException> {
            validManifest(
                allowedHosts = setOf("http://example.com"),
            )
        }
    }

    @Test
    fun manifestRejectsHostWithPath() {
        assertFailsWith<IllegalArgumentException> {
            validManifest(
                allowedHosts = setOf("example.com/search"),
            )
        }
    }
    @Test
    fun manifestCapabilitiesDefaultToDeny() {
        kotlin.test.assertTrue(
            validManifest().capabilities.isEmpty(),
        )
    }

    @Test
    fun apiVersionRejectsUnsupportedMajor() {
        assertFailsWith<IllegalArgumentException> {
            PluginApiVersion(
                major = 2,
                minor = 0,
            ).requireSupportedBy(
                hostApi = PluginApiVersion(
                    major = 1,
                    minor = 5,
                ),
            )
        }
    }

    @Test
    fun apiVersionRejectsNewerMinor() {
        assertFailsWith<IllegalArgumentException> {
            PluginApiVersion(
                major = 1,
                minor = 6,
            ).requireSupportedBy(
                hostApi = PluginApiVersion(
                    major = 1,
                    minor = 5,
                ),
            )
        }
    }

    @Test
    fun apiVersionAcceptsSupportedMinor() {
        PluginApiVersion(
            major = 1,
            minor = 3,
        ).requireSupportedBy(
            hostApi = PluginApiVersion(
                major = 1,
                minor = 5,
            ),
        )
    }
    @Test
    fun manifestRejectsMalformedPackageChecksum() {
        assertFailsWith<IllegalArgumentException> {
            validManifest(
                packageChecksumSha256 = "abc",
            )
        }

        assertFailsWith<IllegalArgumentException> {
            validManifest(
                packageChecksumSha256 = "A".repeat(64),
            )
        }
    }

    @Test
    fun manifestRejectsInvalidMinimumHostVersion() {
        assertFailsWith<IllegalArgumentException> {
            validManifest(
                minimumHostVersion = "latest",
            )
        }
    }

    @Test
    fun manifestRejectsNonHttpsMetadataUrls() {
        assertFailsWith<IllegalArgumentException> {
            validManifest(
                homepageUrl = "http://example.com",
            )
        }

        assertFailsWith<IllegalArgumentException> {
            validManifest(
                sourceUrl = "http://example.com/source",
            )
        }

        assertFailsWith<IllegalArgumentException> {
            validManifest(
                updateUrl = "http://example.com/plugin.json",
            )
        }
    }

    @Test
    fun manifestRequiresUpdateUrlOrRepositoryProvenance() {
        assertFailsWith<IllegalArgumentException> {
            validManifest(
                updateUrl = null,
                repositoryProvenance = null,
            )
        }
    }

    @Test
    fun manifestAcceptsRepositoryProvenanceAsUpdateSource() {
        validManifest(
            updateUrl = null,
            repositoryProvenance = PluginRepositoryProvenance(
                repositoryId = "community.main",
                repositoryUrl = "https://repo.example/index.json",
            ),
        )
    }
    private fun validManifest(
        id: String = "community.example",
        name: String = "Example",
        version: String = "1.0.0",
        packageChecksumSha256: String = "a".repeat(64),
        homepageUrl: String? = "https://example.com",
        sourceUrl: String? = "https://example.com/source",
        minimumHostVersion: String = "1.0.0",
        updateUrl: String? = "https://example.com/plugin.json",
        repositoryProvenance: PluginRepositoryProvenance? = null,
        allowedHosts: Set<String> = setOf("example.com"),
        entry: String = "main.js",
    ): PluginManifest = PluginManifest(
        id = id,
        name = name,
        version = version,
        packageChecksumSha256 = packageChecksumSha256,
        homepageUrl = homepageUrl,
        sourceUrl = sourceUrl,
        minimumHostVersion = minimumHostVersion,
        updateUrl = updateUrl,
        repositoryProvenance = repositoryProvenance,
        api = PluginApiVersion(
            major = 1,
            minor = 0,
        ),
        kinds = setOf(PluginKind.CONTENT),
        languages = setOf("vi"),
        allowedHosts = allowedHosts,
        runtime = PluginRuntime.JAVASCRIPT,
        entry = entry,
    )
}
