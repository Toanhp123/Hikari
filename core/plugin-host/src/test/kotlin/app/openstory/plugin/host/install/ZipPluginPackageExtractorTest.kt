package app.openstory.plugin.host.install

import app.openstory.common.AppResult
import java.io.ByteArrayOutputStream
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.concurrent.CancellationException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

class ZipPluginPackageExtractorTest {

    @Test
    fun cancellationWhileResolvingDestinationIsPropagated() =
        runTest {
            val destination =
                cancellingDestinationPath()

            assertFailsWith<CancellationException> {
                ZipPluginPackageExtractor()
                    .extract(
                        packageBytes =
                            byteArrayOf(),
                        destination =
                            destination,
                    )
            }
        }

    @Test
    fun traversalEntryIsRejectedWithoutWritingOutsideDestination() =
        runTest {
            val fixture =
                extractorFixture()

            try {
                val failure =
                    assertIs<AppResult.Failure>(
                        fixture.extractor.extract(
                            packageBytes =
                                zipPackage(
                                    entryPath =
                                        "../escape.js",
                                ),
                            destination =
                                fixture.destination,
                        ),
                    )

                assertEquals(
                    expected =
                        "plugin.package_path_invalid",
                    actual =
                        failure.error.code,
                )

                assertFalse(
                    fixture.outsideFileExists(),
                    "Archive traversal must not write outside the staging directory.",
                )
            } finally {
                fixture.close()
            }
        }
}

private fun cancellingDestinationPath():
    Path =
    Proxy.newProxyInstance(
        Path::class.java.classLoader,
        arrayOf(
            Path::class.java,
        ),
    ) { _, method, _ ->
        if (
            method.name ==
            "toAbsolutePath"
        ) {
            throw CancellationException(
                "Fixture destination cancellation.",
            )
        }

        throw UnsupportedOperationException(
            method.name,
        )
    } as Path

private fun extractorFixture():
    ZipExtractorFixture {
    val rootDirectory =
        Files.createTempDirectory(
            "openstory-zip-extractor-",
        )

    return ZipExtractorFixture(
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

private class ZipExtractorFixture(
    private val rootDirectory: Path,
    val destination: Path,
    val extractor:
        ZipPluginPackageExtractor,
) : AutoCloseable {

    fun outsideFileExists(): Boolean =
        Files.exists(
            rootDirectory.resolve(
                "escape.js",
            ),
        )

    override fun close() {
        rootDirectory.deleteRecursively()
    }
}

private fun zipPackage(
    entryPath: String,
): ByteArray {
    val output =
        ByteArrayOutputStream()

    ZipOutputStream(output).use { archive ->
        archive.putNextEntry(
            ZipEntry(
                entryPath,
            ),
        )

        archive.write(
            "escape"
                .encodeToByteArray(),
        )

        archive.closeEntry()
    }

    return output.toByteArray()
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
