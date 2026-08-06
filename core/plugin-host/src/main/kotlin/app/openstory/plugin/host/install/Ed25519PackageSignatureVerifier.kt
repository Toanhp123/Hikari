package app.openstory.plugin.host.install

import app.openstory.plugin.api.packageformat.PluginPackageMetadata
import app.openstory.plugin.api.packageformat.PluginPackageSignature
import app.openstory.plugin.api.packageformat.PluginSignatureAlgorithm
import java.security.MessageDigest
import java.util.Base64
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

fun interface TrustedSignerKeyResolver {

    fun resolve(
        signerKeyId: String,
    ): ByteArray?
}

class Ed25519PackageSignatureVerifier(
    private val trustedSignerKeyResolver:
        TrustedSignerKeyResolver,
) : PackageSignatureVerifier {

    override fun verify(
        metadata: PluginPackageMetadata,
    ): PackageSignatureDecision {
        val signature =
            metadata.signature

        return if (
            signature == null ||
            signature.algorithm !=
            PluginSignatureAlgorithm.ED25519
        ) {
            PackageSignatureDecision.invalid()
        } else {
            runCatching {
                verifyEd25519(
                    metadata =
                        metadata,
                    signature =
                        signature,
                )
            }.getOrDefault(
                PackageSignatureDecision.invalid(),
            )
        }
    }

    private fun verifyEd25519(
        metadata: PluginPackageMetadata,
        signature: PluginPackageSignature,
    ): PackageSignatureDecision {
        val publicKeyBytes =
            trustedSignerKeyResolver.resolve(
                signature.signerKeyId,
            )

        val signatureBytes =
            Base64.getDecoder()
                .decode(
                    signature.signatureBase64,
                )

        val isVerified =
            publicKeyBytes != null &&
                publicKeyBytes.size ==
                ED25519_PUBLIC_KEY_BYTES &&
                signatureBytes.size ==
                ED25519_SIGNATURE_BYTES &&
                verifyPayload(
                    payload =
                        metadata.signaturePayload()
                            .encodeToByteArray(),
                    publicKeyBytes =
                        publicKeyBytes,
                    signatureBytes =
                        signatureBytes,
                )

        return if (isVerified) {
            PackageSignatureDecision.verified(
                signerKeyId =
                    signature.signerKeyId,
                signerFingerprintSha256 =
                    sha256Fingerprint(
                        publicKeyBytes,
                    ),
            )
        } else {
            PackageSignatureDecision.invalid()
        }
    }

    private fun verifyPayload(
        payload: ByteArray,
        publicKeyBytes: ByteArray,
        signatureBytes: ByteArray,
    ): Boolean {
        val verifier =
            Ed25519Signer()

        verifier.init(
            false,
            Ed25519PublicKeyParameters(
                publicKeyBytes,
                0,
            ),
        )

        verifier.update(
            payload,
            0,
            payload.size,
        )

        return verifier.verifySignature(
            signatureBytes,
        )
    }
}

private fun sha256Fingerprint(
    bytes: ByteArray,
): String =
    MessageDigest
        .getInstance(
            "SHA-256",
        )
        .digest(
            bytes,
        )
        .joinToString(
            separator =
                "",
        ) { byte ->
            "%02x".format(
                byte.toInt() and
                    UNSIGNED_BYTE_MASK,
            )
        }

private const val ED25519_PUBLIC_KEY_BYTES =
    32

private const val ED25519_SIGNATURE_BYTES =
    64

private const val UNSIGNED_BYTE_MASK =
    0xff
