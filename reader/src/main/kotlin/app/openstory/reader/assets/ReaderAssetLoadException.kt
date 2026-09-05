package app.openstory.reader.assets

/**
 * Throwable bridge for adapters that can only surface Reader asset failures as exceptions.
 *
 * The typed failure remains owned by :reader so presentation code can inspect it without a
 * forbidden dependency on the app-level transport or image-pipeline adapters.
 */
class ReaderAssetLoadException(
    val failure: ReaderAssetFailure,
) : Exception("Reader asset load failed: $failure")
