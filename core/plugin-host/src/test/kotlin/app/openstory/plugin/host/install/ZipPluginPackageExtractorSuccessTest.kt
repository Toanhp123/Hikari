package app.openstory.plugin.host.install

import app.openstory.common.AppResult
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ZipPluginPackageExtractorSuccessTest {

    @Test
    fun validPackageEntriesAreExtractedIntoDestination() =
        runTest {
            val fixture =
                validExtractionFixture()

            try {
                val result =
                    fixture.extractor.extract(
                        packageBytes =
                            validZipPackage(),
                        destination =
                            fixture.destination,
                    )

                assertIs<AppResult.Success<*>>(
                    result,
                )

                fixture.assertExtractedFiles()
            } finally {
                fixture.close()
            }
        }
}

private fun validExtractionFixture():
    ValidZipExtractionFixture {
    val rootDirectory =
        Files.createTempDirectory(
            "openstory-valid-zip-",
        )

    return ValidZipExtractionFixture(
        rootDirectory =
            rootDirectory,
        destination =
            rootDirectory.resolve(
                "staging",
            ),
        extractor =
            ZipPluginPackageExtractor(),
    )
}

private class ValidZipExtractionFixture(
    private val rootDirectory: Path,
    val destination: Path,
    val extractor:
        ZipPluginPackageExtractor,
) : AutoCloseable {

    fun assertExtractedFiles() {
        val manifest =
            destination.resolve(
                "manifest.json",
            )

        val entryScript =
            destination.resolve(
                "main.js",
            )

        assertTrue(
            Files.isRegularFile(
                manifest,
            ),
        )

        assertTrue(
            Files.isRegularFile(
                entryScript,
            ),
        )

        assertEquals(
            expected =
                MANIFEST_CONTENT,
            actual =
                Files.readString(
                    manifest,
                ),
        )

        assertEquals(
            expected =
                SCRIPT_CONTENT,
            actual =
                Files.readString(
                    entryScript,
                ),
        )
    }

    override fun close() {
        rootDirectory.deleteRecursively()
    }
}

private fun validZipPackage():
    ByteArray {
    val output =
        ByteArrayOutputStream()

    ZipOutputStream(output).use { archive ->
        archive.writeEntry(
            path =
                "manifest.json",
            content =
                MANIFEST_CONTENT,
        )

        archive.writeEntry(
            path =
                "main.js",
            content =
                SCRIPT_CONTENT,
        )
    }

    return output.toByteArray()
}

private fun ZipOutputStream.writeEntry(
    path: String,
    content: String,
) {
    putNextEntry(
        ZipEntry(
            path,
        ),
    )

    write(
        content.encodeToByteArray(),
    )

    closeEntry()
}

private fun Path.deleteRecursively() {
    if (!Files.exists(this)) {
        return
    }

    Files.walk(this).use { paths ->
        paths
            .sorted(
                Comparator.reverseOrder(),
            )
            .forEach { path ->
                path.toFile()
                    .setWritable(
                        true,
                        false,
                    )

                Files.deleteIfExists(
                    path,
                )
            }
    }
}

private const val MANIFEST_CONTENT =
    """{"id":"community.fixture","version":"1.0.0"}"""

private const val SCRIPT_CONTENT =
    "export default {}"
