package app.openstory.downloads.assets

import app.openstory.reader.assets.ReaderAssetKeyHash
import java.security.MessageDigest
import java.util.UUID

class ReaderAssetBlobIdFactory(
    private val nextUuid: () -> UUID = UUID::randomUUID,
) {
    fun create(logicalKeyHash: ReaderAssetKeyHash): ReaderAssetBlobId = ReaderAssetBlobId(
        MessageDigest.getInstance("SHA-256")
            .digest("${logicalKeyHash.value}\u0000${nextUuid()}".encodeToByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte) },
    )
}
