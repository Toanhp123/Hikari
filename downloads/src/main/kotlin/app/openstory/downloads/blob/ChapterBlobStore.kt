package app.openstory.downloads.blob

/** Opaque, integrity-checked chapter bytes owned by the downloads capability. */
interface ChapterBlobStore {
    suspend fun read(key: ChapterBlobKey): ChapterBlob?

    suspend fun write(key: ChapterBlobKey, blob: ChapterBlob)

    suspend fun delete(key: ChapterBlobKey)
}
