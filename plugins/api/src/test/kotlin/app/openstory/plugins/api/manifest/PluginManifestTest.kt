package app.openstory.plugins.api.manifest

import app.openstory.plugins.api.protocol.PluginOperation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PluginManifestTest {
    @Test
    fun manifestRejectsWildcardNetworkHost() {
        assertFailsWith<IllegalArgumentException> { manifest(networkHosts = setOf("*.example.com")) }
    }

    @Test
    fun manifestUsesSingleJavaScriptEntry() {
        assertFailsWith<IllegalArgumentException> { manifest(entry = "selector.json") }
    }

    @Test
    fun serializedManifestHasNoSelfChecksumOrRuntimeField() {
        val json = Json.encodeToString(PluginManifest.serializer(), manifest())
        assertFalse("packageChecksumSha256" in json)
        assertFalse("\"runtime\"" in json)
    }

    @Test
    fun manifestDeclaresOperationSupportWithoutBreakingLegacyServiceFallback() {
        val declared = manifest(operations = setOf(PluginOperation.CATALOG_SEARCH))

        assertTrue(declared.supports(PluginOperation.CATALOG_SEARCH))
        assertFalse(declared.supports(PluginOperation.CATALOG_HOME))
        assertTrue(manifest().supports(PluginOperation.CATALOG_HOME))
        assertTrue("\"operations\":[\"catalog.search\"]" in Json.encodeToString(declared))
    }

    @Test
    fun manifestRejectsOperationsOutsideDeclaredServices() {
        assertFailsWith<IllegalArgumentException> {
            manifest(operations = setOf(PluginOperation.CONTENT_CHAPTER))
        }
    }

    @Test
    fun manifestRetainsDeclaredServices() {
        assertEquals(setOf(PluginService.CATALOG), manifest().provides)
    }

    @Test
    fun remoteImageCapabilityCannotAdvertiseUnsupportedOfflineDownload() {
        assertFailsWith<IllegalArgumentException> {
            ReaderCapability(offlineDownload = true, remoteImages = true)
        }
    }

    @Test
    fun remoteImageCacheTrustIsExplicitAndDefaultsFailClosed() {
        val capability = ReaderCapability(remoteImages = true, offlineDownload = false)

        assertEquals(ReaderImageIdentityContract.DELIVERY_STABLE_ONLY, capability.imageIdentity)
        assertEquals(ReaderImageLocatorContract.MUTABLE_OR_UNKNOWN, capability.imageLocator)
        assertEquals(ReaderImagePersistenceContract.NON_PERSISTENT, capability.imagePersistence)
    }

    @Test
    fun textReaderCapabilityRejectsImageCacheTrustContracts() {
        assertFailsWith<IllegalArgumentException> {
            ReaderCapability(imageIdentity = ReaderImageIdentityContract.STABLE_ID_CHANGES_WITH_CONTENT)
        }
        assertFailsWith<IllegalArgumentException> {
            ReaderCapability(imageLocator = ReaderImageLocatorContract.LOCATOR_CHANGES_WITH_CONTENT)
        }
        assertFailsWith<IllegalArgumentException> {
            ReaderCapability(imagePersistence = ReaderImagePersistenceContract.PUBLIC)
        }
    }

    @Test
    fun readerCapabilityRequiresChapterOperationAndCanDisableOfflineDownload() {
        assertFailsWith<IllegalArgumentException> {
            contentManifest(
                operations = setOf(PluginOperation.CONTENT_CHAPTERS),
                reader = ReaderCapability(offlineDownload = false, remoteImages = true),
            )
        }

        val manifest = contentManifest(
            operations = setOf(PluginOperation.CONTENT_CHAPTER),
            reader = ReaderCapability(offlineDownload = false, remoteImages = true),
        )

        assertFalse(manifest.capabilities.reader!!.offlineDownload)
        assertTrue(manifest.capabilities.reader!!.remoteImages)
    }

    @Test
    fun authenticationIsOptionalForExistingManifests() {
        val encoded = Json.encodeToString(PluginManifest.serializer(), manifest())

        assertEquals(null, manifest().capabilities.authentication)
        assertFalse("authentication" in encoded)
    }

    @Test
    fun authenticationRejectsUnsafeNavigationAndCompletionTargets() {
        assertFailsWith<IllegalArgumentException> {
            authentication(loginStartUrl = "http://accounts.example.com/login")
        }
        assertFailsWith<IllegalArgumentException> {
            authentication(navigationHosts = setOf("*.example.com"))
        }
        assertFailsWith<IllegalArgumentException> {
            authentication(completionHost = "user@accounts.example.com")
        }
        assertFailsWith<IllegalArgumentException> {
            authentication(completionHost = "accounts.example.com:8443")
        }
    }

    @Test
    fun authenticationRejectsUnsafeCredentialScopeAndTtl() {
        assertFailsWith<IllegalArgumentException> {
            manifest(authentication = authentication(credentialHost = "outside.example.com"))
        }
        assertFailsWith<IllegalArgumentException> {
            PluginAuthenticationCredentialTarget("api.example.com", "relative", setOf("session"))
        }
        assertFailsWith<IllegalArgumentException> {
            PluginAuthenticationCredentialTarget("api.example.com", "/v1", emptySet())
        }
        assertFailsWith<IllegalArgumentException> {
            authentication(sessionTtlSeconds = PluginAuthenticationCapability.MAX_SESSION_TTL_SECONDS + 1)
        }
    }

    @Test
    fun authenticationFingerprintInputIsOrderIndependentAndSecretFree() {
        val first = authentication(
            navigationHosts = setOf("accounts.example.com", "login.example.com"),
            cookieNames = setOf("refresh", "session"),
        )
        val second = authentication(
            navigationHosts = setOf("login.example.com", "accounts.example.com"),
            cookieNames = setOf("session", "refresh"),
        )

        assertEquals(first.policyFingerprint(), second.policyFingerprint())
        assertEquals(64, first.policyFingerprint().length)
    }

    @Test
    fun authenticationFixtureDecodesAndReencodesDeterministically() {
        val raw = checkNotNull(javaClass.getResource("/authentication/manifest.json")).readText()
        val manifest = Json.decodeFromString<PluginManifest>(raw)
        val authentication = checkNotNull(manifest.capabilities.authentication)

        assertEquals("accounts.example.org", authentication.navigationHosts.first())
        assertEquals(setOf("refresh", "session"), authentication.credentialTargets.single().cookieNames)
        assertEquals(
            Json.encodeToString(manifest),
            Json.encodeToString(Json.decodeFromString<PluginManifest>(Json.encodeToString(manifest))),
        )
    }

    private fun manifest(
        entry: String = "main.js",
        networkHosts: Set<String> = setOf("api.example.com"),
        operations: Set<PluginOperation>? = null,
        authentication: PluginAuthenticationCapability? = null,
    ) = PluginManifest(
        id = "org.example.plugin",
        name = "Example plugin",
        version = "1.0.0",
        protocol = PluginProtocolVersion(1),
        entry = entry,
        provides = setOf(PluginService.CATALOG),
        operations = operations,
        capabilities = PluginCapabilities(
            network = NetworkCapability(networkHosts),
            authentication = authentication,
        ),
    )

    private fun authentication(
        loginStartUrl: String = "https://accounts.example.com/login",
        navigationHosts: Set<String> = setOf("accounts.example.com"),
        completionHost: String = "accounts.example.com",
        credentialHost: String = "api.example.com",
        cookieNames: Set<String> = setOf("session"),
        sessionTtlSeconds: Long = 24 * 60 * 60,
    ) = PluginAuthenticationCapability(
        loginStartUrl = loginStartUrl,
        navigationHosts = navigationHosts,
        completion = PluginAuthenticationCompletionTarget(completionHost, "/complete"),
        credentialTargets = listOf(
            PluginAuthenticationCredentialTarget(
                host = credentialHost,
                pathPrefix = "/v1",
                cookieNames = cookieNames,
            ),
        ),
        sessionTtlSeconds = sessionTtlSeconds,
    )
    private fun contentManifest(
        operations: Set<PluginOperation>,
        reader: ReaderCapability?,
    ) = PluginManifest(
        id = "org.example.content",
        name = "Example content",
        version = "1.0.0",
        protocol = PluginProtocolVersion(1),
        provides = setOf(PluginService.CONTENT),
        operations = operations,
        capabilities = PluginCapabilities(reader = reader),
    )
}
