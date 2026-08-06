package app.openstory.plugin.host.install

import app.openstory.plugin.api.packageformat.PackageSignatureState
import app.openstory.plugin.api.packageformat.PluginPackageMetadata
import app.openstory.plugin.api.packageformat.PluginPackageSignature
import app.openstory.plugin.api.packageformat.PluginSignatureAlgorithm
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

class Ed25519PackageSignatureVerifierTest {

    @Test
    fun validSignatureFromTrustedSignerIsAccepted() {
        val fixture =
            signedFixture()

        val decision =
            trustedVerifier(
                publicKeyBytes =
                    fixture.publicKeyBytes,
            ).verify(
                fixture.metadata,
            )

        assertEquals(
            PackageSignatureState.VERIFIED,
            decision.signatureState,
        )
    }

    @Test
    fun validSignatureReportsTrustedSignerFingerprint() {
        val fixture =
            signedFixture()

        val decision =
            trustedVerifier(
                publicKeyBytes =
                    fixture.publicKeyBytes,
            ).verify(
                fixture.metadata,
            )

        assertEquals(
            PackageSignatureState.VERIFIED,
            decision.signatureState,
        )

        assertEquals(
            FIXTURE_SIGNER_KEY_ID,
            decision.signerKeyId,
        )

        assertEquals(
            sha256(
                fixture.publicKeyBytes,
            ),
            decision.signerFingerprintSha256,
        )
    }
}

private data class SignedPackageFixture(
    val metadata: PluginPackageMetadata,
    val publicKeyBytes: ByteArray,
)

private fun signedFixture():
    SignedPackageFixture {
    val privateKey =
        Ed25519PrivateKeyParameters(
            SecureRandom(),
        )

    val publicKeyBytes =
        privateKey.generatePublicKey()
            .encoded

    val unsignedMetadata =
        fixtureMetadata(
            signature =
                null,
        )

    val signatureBytes =
        sign(
            payload =
                unsignedMetadata.signaturePayload(),
            privateKey =
                privateKey,
        )

    return SignedPackageFixture(
        metadata =
            unsignedMetadata.copy(
                signature =
                    PluginPackageSignature(
                        algorithm =
                            PluginSignatureAlgorithm.ED25519,
                        signerKeyId =
                            FIXTURE_SIGNER_KEY_ID,
                        signatureBase64 =
                            Base64.getEncoder()
                                .encodeToString(
                                    signatureBytes,
                                ),
                    ),
            ),
        publicKeyBytes =
            publicKeyBytes,
    )
}

private fun trustedVerifier(
    publicKeyBytes: ByteArray,
): Ed25519PackageSignatureVerifier =
    Ed25519PackageSignatureVerifier(
        trustedSignerKeyResolver =
            TrustedSignerKeyResolver {
                    signerKeyId,
                ->
                if (
                    signerKeyId ==
                    FIXTURE_SIGNER_KEY_ID
                ) {
                    publicKeyBytes
                } else {
                    null
                }
            },
    )

private fun fixtureMetadata(
    signature: PluginPackageSignature?,
): PluginPackageMetadata =
    PluginPackageMetadata(
        pluginId =
            "community.fixture",
        version =
            "1.0.0",
        exactPackageSha256 =
            "0".repeat(64),
        signature =
            signature,
    )

private fun sign(
    payload: String,
    privateKey:
        Ed25519PrivateKeyParameters,
): ByteArray {
    val payloadBytes =
        payload.encodeToByteArray()

    val signer =
        Ed25519Signer().apply {
            init(
                true,
                privateKey,
            )

            update(
                payloadBytes,
                0,
                payloadBytes.size,
            )
        }

    return signer.generateSignature()
}

private fun sha256(
    bytes: ByteArray,
): String =
    MessageDigest.getInstance(
        "SHA-256",
    ).digest(
        bytes,
    ).joinToString(
        separator =
            "",
    ) { byte ->
        "%02x".format(
            byte.toInt() and
                UNSIGNED_BYTE_MASK,
        )
    }

private const val FIXTURE_SIGNER_KEY_ID =
    "fixture-author-main"

private const val UNSIGNED_BYTE_MASK =
    0xff
