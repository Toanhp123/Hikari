package app.openstory.plugin.api.packageformat

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PackageSecurityValidationTest {
    @Test
    fun archiveRejectsWindowsTraversalControlCharactersAndInvalidSizes() {
        val traversal = PackageLayoutValidator.validateEntries(
            listOf("manifest.json", "..\\main.js"),
        )
        val drive = PackageLayoutValidator.validateEntries(
            listOf("manifest.json", "C:\\main.js"),
        )
        val control = PackageLayoutValidator.validateEntries(
            listOf("manifest.json", "main\u0000.js"),
        )
        val invalidSize = PackageLayoutValidator.validateArchive(
            entries = listOf(
                PackageArchiveEntry("manifest.json", -1, 1, false, false),
            ),
            declaredExecutableEntries = emptySet(),
        )

        assertTrue(PackageLayoutError.PATH_TRAVERSAL in traversal)
        assertTrue(PackageLayoutError.PATH_TRAVERSAL in drive)
        assertTrue(PackageLayoutError.INVALID_ENTRY_PATH in control)
        assertTrue(PackageLayoutError.INVALID_ENTRY_SIZE in invalidSize)
    }

    @Test
    fun executableDeclarationMustReferenceExistingRuntimeEntry() {
        val result = PackageLayoutValidator.validateArchive(
            entries = listOf(
                PackageArchiveEntry("manifest.json", 1, 1, false, false),
                PackageArchiveEntry("selector.json", 1, 1, false, false),
            ),
            declaredExecutableEntries = setOf("main.js"),
        )

        assertTrue(PackageLayoutError.MISSING_DECLARED_EXECUTABLE in result)
    }

    @Test
    fun archiveRejectsSizeOverflowAndMissingRuntimeEntry() {
        val overflow = PackageLayoutValidator.validateArchive(
            entries = listOf(
                PackageArchiveEntry("manifest.json", Long.MAX_VALUE, 1, false, false),
                PackageArchiveEntry("selector.json", 1, 1, false, false),
            ),
            declaredExecutableEntries = emptySet(),
        )
        val missingRuntime = PackageLayoutValidator.validateArchive(
            entries = listOf(
                PackageArchiveEntry("manifest.json", 1, 1, false, false),
                PackageArchiveEntry("main.js", 1, 1, false, true),
            ),
            declaredExecutableEntries = setOf("main.js"),
            requiredRuntimeEntry = "selector.json",
        )
        val mixedRuntime = PackageLayoutValidator.validateArchive(
            entries = listOf(
                PackageArchiveEntry("manifest.json", 1, 1, false, false),
                PackageArchiveEntry("selector.json", 1, 1, false, false),
                PackageArchiveEntry("main.js", 1, 1, false, false),
            ),
            declaredExecutableEntries = setOf("selector.json"),
            requiredRuntimeEntry = "selector.json",
        )

        assertTrue(PackageLayoutError.SIZE_OVERFLOW in overflow)
        assertTrue(PackageLayoutError.RUNTIME_ENTRY_MISMATCH in missingRuntime)
        assertTrue(PackageLayoutError.RUNTIME_ENTRY_MISMATCH in mixedRuntime)
    }

    @Test
    fun ed25519SignatureRequiresCanonicalKeyIdAndSixtyFourBytes() {
        assertFailsWith<IllegalArgumentException> {
            PluginPackageSignature(
                algorithm = PluginSignatureAlgorithm.ED25519,
                signerKeyId = "Invalid Key",
                signatureBase64 = java.util.Base64.getEncoder().encodeToString(ByteArray(64)),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            PluginPackageSignature(
                algorithm = PluginSignatureAlgorithm.ED25519,
                signerKeyId = "author.main",
                signatureBase64 = java.util.Base64.getEncoder().encodeToString(ByteArray(63)),
            )
        }
    }

    @Test
    fun manifestUrlProvenanceRequiresHttps() {
        assertFailsWith<IllegalArgumentException> {
            PackageInstallProvenance(
                source = PackageInstallSource.MANIFEST_URL,
                sourceReference = "http://repo.example/manifest.json",
                signatureState = PackageSignatureState.INVALID,
                unsignedWarningAcknowledged = false,
            )
        }
    }

    @Test
    fun repositoryRejectsInvalidSecurityFieldsAndRollbackDirection() {
        val artifact = RepositoryVersionArtifact(
            pluginId = "community.example",
            version = "1.0.0",
            packageUrl = "http://repo.example/example.osp",
            exactPackageSha256 = "not-a-checksum",
            signature = null,
            changelogUrl = null,
            declaredCapabilities = emptySet(),
            rollback = RepositoryRollbackMetadata(
                version = "2.0.0",
                packageUrl = "https://repo.example/example-2.0.0.osp",
                exactPackageSha256 = "b".repeat(64),
            ),
        )
        val errors = RepositoryIndexValidation.validate(
            RepositoryIndex(
                schemaVersion = 1,
                repositoryId = "community.main",
                artifacts = listOf(artifact),
            ),
        )

        assertTrue(RepositoryIndexError.INVALID_ARTIFACT in errors)
        assertTrue(RepositoryIndexError.INVALID_ROLLBACK in errors)
    }
}
