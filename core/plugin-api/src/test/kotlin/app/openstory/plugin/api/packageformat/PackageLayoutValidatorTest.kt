package app.openstory.plugin.api.packageformat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class PackageLayoutValidatorTest {

    @Test
    fun archiveRejectsTraversalEntry() {
        val result = PackageLayoutValidator.validateEntries(
            listOf(
                "manifest.json",
                "../escape.js",
            ),
        )

        assertEquals(
            PackageLayoutError.PATH_TRAVERSAL,
            result.single(),
        )
    }

    @Test
    fun archiveRejectsDuplicateEntryNames() {
        val result = PackageLayoutValidator.validateEntries(
            listOf(
                "manifest.json",
                "main.js",
                "main.js",
            ),
        )

        assertTrue(
            PackageLayoutError.DUPLICATE_ENTRY in result,
        )
    }

    @Test
    fun archiveRejectsSymbolicLinks() {
        val result = PackageLayoutValidator.validateArchive(
            entries = listOf(
                archiveEntry(path = "manifest.json"),
                archiveEntry(
                    path = "main.js",
                    isSymbolicLink = true,
                ),
            ),
            declaredExecutableEntries = setOf("main.js"),
        )

        assertTrue(
            PackageLayoutError.SYMBOLIC_LINK in result,
        )
    }

    @Test
    fun archiveRejectsTooManyEntries() {
        val result = PackageLayoutValidator.validateArchive(
            entries = listOf(
                archiveEntry(path = "manifest.json"),
                archiveEntry(path = "main.js"),
            ),
            declaredExecutableEntries = setOf("main.js"),
            limits = PackageArchiveLimits(
                maximumEntryCount = 1,
                maximumCompressedBytes = 1_000,
                maximumUncompressedBytes = 1_000,
                maximumCompressionRatio = 100.0,
            ),
        )

        assertTrue(
            PackageLayoutError.ENTRY_COUNT_LIMIT in result,
        )
    }

    @Test
    fun archiveRejectsCompressedSizeLimit() {
        val result = PackageLayoutValidator.validateArchive(
            entries = listOf(
                archiveEntry(
                    path = "manifest.json",
                    compressedSizeBytes = 80,
                    uncompressedSizeBytes = 80,
                ),
                archiveEntry(
                    path = "main.js",
                    compressedSizeBytes = 80,
                    uncompressedSizeBytes = 80,
                ),
            ),
            declaredExecutableEntries = setOf("main.js"),
            limits = PackageArchiveLimits(
                maximumEntryCount = 10,
                maximumCompressedBytes = 100,
                maximumUncompressedBytes = 1_000,
                maximumCompressionRatio = 100.0,
            ),
        )

        assertTrue(
            PackageLayoutError.COMPRESSED_SIZE_LIMIT in result,
        )
    }

    @Test
    fun archiveRejectsUncompressedSizeLimit() {
        val result = PackageLayoutValidator.validateArchive(
            entries = listOf(
                archiveEntry(
                    path = "manifest.json",
                    compressedSizeBytes = 10,
                    uncompressedSizeBytes = 600,
                ),
                archiveEntry(
                    path = "main.js",
                    compressedSizeBytes = 10,
                    uncompressedSizeBytes = 600,
                ),
            ),
            declaredExecutableEntries = setOf("main.js"),
            limits = PackageArchiveLimits(
                maximumEntryCount = 10,
                maximumCompressedBytes = 1_000,
                maximumUncompressedBytes = 1_000,
                maximumCompressionRatio = 100.0,
            ),
        )

        assertTrue(
            PackageLayoutError.UNCOMPRESSED_SIZE_LIMIT in result,
        )
    }

    @Test
    fun archiveRejectsSuspiciousCompressionRatio() {
        val result = PackageLayoutValidator.validateArchive(
            entries = listOf(
                archiveEntry(path = "manifest.json"),
                archiveEntry(
                    path = "main.js",
                    compressedSizeBytes = 1,
                    uncompressedSizeBytes = 1_000,
                ),
            ),
            declaredExecutableEntries = setOf("main.js"),
            limits = PackageArchiveLimits(
                maximumEntryCount = 10,
                maximumCompressedBytes = 10_000,
                maximumUncompressedBytes = 10_000,
                maximumCompressionRatio = 100.0,
            ),
        )

        assertTrue(
            PackageLayoutError.SUSPICIOUS_COMPRESSION_RATIO in result,
        )
    }

    @Test
    fun archiveRejectsUndeclaredExecutable() {
        val result = PackageLayoutValidator.validateArchive(
            entries = listOf(
                archiveEntry(path = "manifest.json"),
                archiveEntry(
                    path = "main.js",
                    isExecutable = true,
                ),
            ),
            declaredExecutableEntries = emptySet(),
        )

        assertTrue(
            PackageLayoutError.UNDECLARED_EXECUTABLE in result,
        )
    }


    @Test
    fun signedPackagePayloadBindsChecksumPluginIdAndVersion() {
        val checksum = "a".repeat(64)
        val metadata = PluginPackageMetadata(
            pluginId = "community.example",
            version = "1.2.3",
            exactPackageSha256 = checksum,
            signature = PluginPackageSignature(
                algorithm = PluginSignatureAlgorithm.ED25519,
                signerKeyId = "author-main",
                signatureBase64 = java.util.Base64.getEncoder()
                    .encodeToString(ByteArray(64)),
            ),
        )

        assertEquals(
            "$checksum\ncommunity.example\n1.2.3",
            metadata.signaturePayload(),
        )
    }

    @Test
    fun unsignedInstallRequiresRecordedWarningAcknowledgement() {
        assertFailsWith<IllegalArgumentException> {
            PackageInstallProvenance(
                source = PackageInstallSource.LOCAL_FILE,
                sourceReference = "example.osp",
                signatureState = PackageSignatureState.UNSIGNED,
                unsignedWarningAcknowledged = false,
            )
        }

        val provenance = PackageInstallProvenance(
            source = PackageInstallSource.LOCAL_FILE,
            sourceReference = "example.osp",
            signatureState = PackageSignatureState.UNSIGNED,
            unsignedWarningAcknowledged = true,
        )

        assertTrue(provenance.unsignedWarningAcknowledged)
    }

    @Test
    fun repositoryRejectsMutatedArtifactForSameVersion() {
        val original = RepositoryVersionArtifact(
            pluginId = "community.example",
            version = "1.0.0",
            packageUrl =
                "https://repo.example/community.example/1.0.0.osp",
            exactPackageSha256 = "a".repeat(64),
            signature = null,
            changelogUrl = null,
            declaredCapabilities = emptySet(),
            rollback = null,
        )

        val mutated = original.copy(
            packageUrl =
                "https://repo.example/community.example/replaced.osp",
            exactPackageSha256 = "b".repeat(64),
        )

        val result = RepositoryIndexValidation.validate(
            RepositoryIndex(
                schemaVersion = RepositoryIndex.CURRENT_SCHEMA_VERSION,
                repositoryId = "community.main",
                artifacts = listOf(original, mutated),
            ),
        )

        assertEquals(
            RepositoryIndexError.IMMUTABLE_VERSION_CONFLICT,
            result.single(),
        )
    }

    @Test
    fun repositoryJsonRoundTripPreservesUnknownOptionalFields() {
        val source = """
            {
              "schemaVersion": 1,
              "repositoryId": "community.main",
              "futureTopLevel": {
                "enabled": true
              },
              "artifacts": [
                {
                  "pluginId": "community.example",
                  "version": "1.0.0",
                  "packageUrl": "https://repo.example/example-1.0.0.osp",
                  "exactPackageSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "signature": null,
                  "changelogUrl": null,
                  "declaredCapabilities": [],
                  "rollback": null,
                  "futureArtifactField": "keep-me"
                }
              ]
            }
        """.trimIndent()

        val decoded = RepositoryIndexJson.decode(source)
        val encoded = RepositoryIndexJson.encode(decoded)
        val roundTrip = Json.parseToJsonElement(encoded).jsonObject

        assertTrue(
            roundTrip
                .getValue("futureTopLevel")
                .jsonObject
                .getValue("enabled")
                .jsonPrimitive
                .boolean,
        )

        assertEquals(
            "keep-me",
            roundTrip
                .getValue("artifacts")
                .jsonArray
                .single()
                .jsonObject
                .getValue("futureArtifactField")
                .jsonPrimitive
                .content,
        )
    }
    private fun archiveEntry(
        path: String,
        compressedSizeBytes: Long = 10,
        uncompressedSizeBytes: Long = 10,
        isSymbolicLink: Boolean = false,
        isExecutable: Boolean = false,
    ): PackageArchiveEntry = PackageArchiveEntry(
        path = path,
        compressedSizeBytes = compressedSizeBytes,
        uncompressedSizeBytes = uncompressedSizeBytes,
        isSymbolicLink = isSymbolicLink,
        isExecutable = isExecutable,
    )
}
