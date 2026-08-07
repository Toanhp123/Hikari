package app.openstory.plugin.api

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PluginManifestInvariantTest {
    @Test
    fun apiVersionRequiresPositiveMajorAndNonNegativeMinor() {
        assertFailsWith<IllegalArgumentException> {
            PluginApiVersion(major = 0, minor = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            PluginApiVersion(major = 1, minor = -1)
        }
    }

    @Test
    fun manifestRequiresAtLeastOneKindAndLanguage() {
        assertFailsWith<IllegalArgumentException> {
            validManifest(kinds = emptySet())
        }
        assertFailsWith<IllegalArgumentException> {
            validManifest(languages = emptySet())
        }
    }

    @Test
    fun networkCapabilityAndAllowedHostsMustAgree() {
        assertFailsWith<IllegalArgumentException> {
            validManifest(
                capabilities = setOf(PluginCapability.NETWORK),
                allowedHosts = emptySet(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            validManifest(
                capabilities = emptySet(),
                allowedHosts = setOf("allowed.example"),
            )
        }
    }

    @Test
    fun declarativeOriginMustUseHttpsAndAllowedHost() {
        assertFailsWith<IllegalArgumentException> {
            validManifest(
                runtime = PluginRuntime.DECLARATIVE,
                entry = "selector.json",
                declarativeOrigin = "http://allowed.example/root/",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            validManifest(
                runtime = PluginRuntime.DECLARATIVE,
                entry = "selector.json",
                declarativeOrigin = "https://other.example/root/",
            )
        }
    }

    @Test
    fun javascriptRuntimeRejectsDeclarativeOrigin() {
        assertFailsWith<IllegalArgumentException> {
            validManifest(
                runtime = PluginRuntime.JAVASCRIPT,
                entry = "main.js",
                declarativeOrigin = "https://allowed.example/",
            )
        }
    }

    @Test
    fun manifestWithoutDeclarativeOriginRemainsCompatible() {
        assertNull(validManifest().declarativeOrigin)
    }

    @Test
    fun additiveOriginPreservesOriginalPositionalConstructorShape() {
        val manifest = PluginManifest(
            "community.positional",
            "Positional",
            "1.0.0",
            "a".repeat(64),
            null,
            null,
            "1.0.0",
            "https://allowed.example/plugin.json",
            null,
            PluginApiVersion(1, 0),
            setOf(PluginKind.CONTENT),
            setOf("vi"),
            setOf("allowed.example"),
            setOf(PluginCapability.NETWORK),
            PluginRuntime.JAVASCRIPT,
            "main.js",
        )

        assertNull(manifest.declarativeOrigin)
    }

    @Test
    fun runtimeRequiresCanonicalEntryName() {
        assertFailsWith<IllegalArgumentException> {
            validManifest(
                runtime = PluginRuntime.DECLARATIVE,
                entry = "main.js",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            validManifest(
                runtime = PluginRuntime.JAVASCRIPT,
                entry = "selector.json",
            )
        }
    }

    @Test
    fun repositoryProvenanceRequiresValidatedIdAndHttpsUrl() {
        assertFailsWith<IllegalArgumentException> {
            PluginRepositoryProvenance(
                repositoryId = "Invalid ID",
                repositoryUrl = "https://repo.example/index.json",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            PluginRepositoryProvenance(
                repositoryId = "community.main",
                repositoryUrl = "http://repo.example/index.json",
            )
        }
    }

    private fun validManifest(
        kinds: Set<PluginKind> = setOf(PluginKind.CONTENT),
        languages: Set<String> = setOf("vi"),
        allowedHosts: Set<String> = setOf("allowed.example"),
        capabilities: Set<PluginCapability> = setOf(PluginCapability.NETWORK),
        runtime: PluginRuntime = PluginRuntime.JAVASCRIPT,
        entry: String = "main.js",
        declarativeOrigin: String? = null,
    ): PluginManifest = PluginManifest(
        id = "community.example",
        name = "Example",
        version = "1.0.0",
        packageChecksumSha256 = "a".repeat(64),
        homepageUrl = "https://allowed.example",
        sourceUrl = "https://allowed.example/source",
        minimumHostVersion = "1.0.0",
        updateUrl = "https://allowed.example/plugin.json",
        api = PluginApiVersion(major = 1, minor = 0),
        kinds = kinds,
        languages = languages,
        allowedHosts = allowedHosts,
        capabilities = capabilities,
        runtime = runtime,
        entry = entry,
        declarativeOrigin = declarativeOrigin,
    )
}
