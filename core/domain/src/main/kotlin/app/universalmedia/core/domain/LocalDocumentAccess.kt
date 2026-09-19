package app.universalmedia.core.domain

sealed interface LocalDocumentAccessResult {
    data class Readable(val mimeType: String?) : LocalDocumentAccessResult
    data class Failed(val failure: LocalAccessFailure) : LocalDocumentAccessResult
}

fun interface LocalDocumentAccess {
    /** Checks current metadata and read-only openability, releasing resources before returning. */
    suspend fun validate(
        root: StorageRoot,
        locator: LocalDocumentLocator,
    ): LocalDocumentAccessResult
}
