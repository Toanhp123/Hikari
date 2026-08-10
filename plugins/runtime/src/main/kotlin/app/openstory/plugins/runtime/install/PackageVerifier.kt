package app.openstory.plugins.runtime.install

import app.openstory.common.id.PluginId
import app.openstory.plugins.api.manifest.PluginManifest
import app.openstory.plugins.api.packageformat.PluginArtifact
import app.openstory.plugins.runtime.PluginCallResult
import java.io.IOException
import java.security.MessageDigest

data class VerifiedPluginPackage(
    val pluginId: PluginId,
    val version: String,
    val sha256: String,
    val signerFingerprint: String?,
    val manifest: PluginManifest,
    val entries: Map<String, ByteArray>,
)

class PackageVerifier(
    private val inspector: PackageArchiveInspector = PackageArchiveInspector(),
) {
    fun verify(
        bytes: ByteArray,
        artifactProvenance: PluginArtifact,
    ): PluginCallResult<VerifiedPluginPackage> {
        val actualSha256 = sha256(bytes)
        if (actualSha256 != artifactProvenance.sha256) {
            return PluginCallResult.Failure("plugin.package_checksum_mismatch", retryable = false)
        }
        return try {
            val inspected = inspector.inspect(bytes.copyOf())
            if (inspected.manifest.id != artifactProvenance.pluginId ||
                inspected.manifest.version != artifactProvenance.version
            ) {
                PluginCallResult.Failure("plugin.package_provenance_mismatch", retryable = false)
            } else {
                PluginCallResult.Success(
                    VerifiedPluginPackage(
                        pluginId = PluginId(inspected.manifest.id),
                        version = inspected.manifest.version,
                        sha256 = actualSha256,
                        signerFingerprint = artifactProvenance.signatureEd25519?.let(::sha256Text),
                        manifest = inspected.manifest,
                        entries = inspected.entries,
                    ),
                )
            }
        } catch (_: IllegalArgumentException) {
            PluginCallResult.Failure("plugin.package_layout_invalid", retryable = false)
        } catch (_: IOException) {
            PluginCallResult.Failure("plugin.package_invalid", retryable = false)
        } catch (_: RuntimeException) {
            PluginCallResult.Failure("plugin.package_invalid", retryable = false)
        }
    }
}

internal fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

private fun sha256Text(value: String): String = sha256(value.encodeToByteArray())
