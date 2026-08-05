package app.openstory.plugin.api.packageformat

data class PackageArchiveEntry(
    val path: String,
    val compressedSizeBytes: Long,
    val uncompressedSizeBytes: Long,
    val isSymbolicLink: Boolean,
    val isExecutable: Boolean,
)

data class PackageArchiveLimits(
    val maximumEntryCount: Int = DEFAULT_MAXIMUM_ENTRY_COUNT,
    val maximumCompressedBytes: Long = DEFAULT_MAXIMUM_COMPRESSED_BYTES,
    val maximumUncompressedBytes: Long = DEFAULT_MAXIMUM_UNCOMPRESSED_BYTES,
    val maximumCompressionRatio: Double =
        DEFAULT_MAXIMUM_COMPRESSION_RATIO,
) {
    init {
        require(maximumEntryCount > 0) {
            "Maximum archive entry count must be positive."
        }

        require(maximumCompressedBytes > 0) {
            "Maximum compressed byte count must be positive."
        }

        require(maximumUncompressedBytes > 0) {
            "Maximum uncompressed byte count must be positive."
        }

        require(
            maximumCompressionRatio.isFinite() &&
                maximumCompressionRatio > 0.0,
        ) {
            "Maximum compression ratio must be finite and positive."
        }
    }

    private companion object {
        const val DEFAULT_MAXIMUM_ENTRY_COUNT = 128
        const val DEFAULT_MAXIMUM_COMPRESSED_BYTES =
            16L * 1024L * 1024L
        const val DEFAULT_MAXIMUM_UNCOMPRESSED_BYTES =
            64L * 1024L * 1024L
        const val DEFAULT_MAXIMUM_COMPRESSION_RATIO = 100.0
    }
}

enum class PackageLayoutError {
    PATH_TRAVERSAL,
    DUPLICATE_ENTRY,
    UNDECLARED_ENTRY,
    MISSING_MANIFEST,
    SYMBOLIC_LINK,
    ENTRY_COUNT_LIMIT,
    COMPRESSED_SIZE_LIMIT,
    UNCOMPRESSED_SIZE_LIMIT,
    SUSPICIOUS_COMPRESSION_RATIO,
    UNDECLARED_EXECUTABLE,
}

object PackageLayoutValidator {

    fun validateEntries(
        entries: List<String>,
    ): List<PackageLayoutError> = buildList {
        val unsafeEntries = entries
            .filter(::hasUnsafePath)
            .toSet()

        if (entries.size != entries.toSet().size) {
            add(PackageLayoutError.DUPLICATE_ENTRY)
        }

        if (unsafeEntries.isNotEmpty()) {
            add(PackageLayoutError.PATH_TRAVERSAL)
        }

        if (
            entries.any { entry ->
                entry !in unsafeEntries &&
                    entry !in ALLOWED_ENTRIES
            }
        ) {
            add(PackageLayoutError.UNDECLARED_ENTRY)
        }

        if (MANIFEST_ENTRY !in entries) {
            add(PackageLayoutError.MISSING_MANIFEST)
        }
    }

    fun validateArchive(
        entries: List<PackageArchiveEntry>,
        declaredExecutableEntries: Set<String>,
        limits: PackageArchiveLimits = PackageArchiveLimits(),
    ): List<PackageLayoutError> = buildList {
        addAll(
            validateEntries(
                entries = entries.map { it.path },
            ),
        )

        if (entries.any { it.isSymbolicLink }) {
            add(PackageLayoutError.SYMBOLIC_LINK)
        }

        if (entries.size > limits.maximumEntryCount) {
            add(PackageLayoutError.ENTRY_COUNT_LIMIT)
        }

        val compressedBytes = entries.sumOf {
            it.compressedSizeBytes
        }

        if (compressedBytes > limits.maximumCompressedBytes) {
            add(PackageLayoutError.COMPRESSED_SIZE_LIMIT)
        }

        val uncompressedBytes = entries.sumOf {
            it.uncompressedSizeBytes
        }

        if (uncompressedBytes > limits.maximumUncompressedBytes) {
            add(PackageLayoutError.UNCOMPRESSED_SIZE_LIMIT)
        }

        if (
            entries.any { entry ->
                exceedsCompressionRatio(
                    entry = entry,
                    maximumRatio = limits.maximumCompressionRatio,
                )
            }
        ) {
            add(PackageLayoutError.SUSPICIOUS_COMPRESSION_RATIO)
        }

        if (
            entries.any { entry ->
                entry.isExecutable &&
                    entry.path !in declaredExecutableEntries
            }
        ) {
            add(PackageLayoutError.UNDECLARED_EXECUTABLE)
        }
    }.distinct()

    private fun exceedsCompressionRatio(
        entry: PackageArchiveEntry,
        maximumRatio: Double,
    ): Boolean = when {
        entry.uncompressedSizeBytes <= 0L ->
            false

        entry.compressedSizeBytes <= 0L ->
            true

        else ->
            entry.uncompressedSizeBytes.toDouble() /
                entry.compressedSizeBytes.toDouble() >
                maximumRatio
    }

    private fun hasUnsafePath(entry: String): Boolean =
        entry.startsWith('/') ||
            entry.split('/').any { segment ->
                segment == ".."
            }

    private const val MANIFEST_ENTRY = "manifest.json"

    private val ALLOWED_ENTRIES = setOf(
        MANIFEST_ENTRY,
        "selector.json",
        "main.js",
        "CHANGELOG.md",
        "LICENSE",
    )
}
