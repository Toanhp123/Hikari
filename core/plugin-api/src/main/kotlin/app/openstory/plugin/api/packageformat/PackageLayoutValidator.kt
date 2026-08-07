package app.openstory.plugin.api.packageformat

import java.util.Locale

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
    val maximumCompressionRatio: Double = DEFAULT_MAXIMUM_COMPRESSION_RATIO,
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
        require(maximumCompressionRatio.isFinite() && maximumCompressionRatio > 0.0) {
            "Maximum compression ratio must be finite and positive."
        }
    }

    private companion object {
        const val DEFAULT_MAXIMUM_ENTRY_COUNT = 128
        const val DEFAULT_MAXIMUM_COMPRESSED_BYTES = 16L * 1024L * 1024L
        const val DEFAULT_MAXIMUM_UNCOMPRESSED_BYTES = 64L * 1024L * 1024L
        const val DEFAULT_MAXIMUM_COMPRESSION_RATIO = 100.0
    }
}

enum class PackageLayoutError {
    PATH_TRAVERSAL,
    INVALID_ENTRY_PATH,
    DUPLICATE_ENTRY,
    UNDECLARED_ENTRY,
    MISSING_MANIFEST,
    SYMBOLIC_LINK,
    INVALID_ENTRY_SIZE,
    SIZE_OVERFLOW,
    ENTRY_COUNT_LIMIT,
    COMPRESSED_SIZE_LIMIT,
    UNCOMPRESSED_SIZE_LIMIT,
    SUSPICIOUS_COMPRESSION_RATIO,
    UNDECLARED_EXECUTABLE,
    MISSING_DECLARED_EXECUTABLE,
    RUNTIME_ENTRY_MISMATCH,
}

object PackageLayoutValidator {
    fun validateEntries(entries: List<String>): List<PackageLayoutError> = buildList {
        val invalidEntries = entries.filter(::hasInvalidPath).toSet()
        val traversalEntries = entries.filter(::hasTraversalPath).toSet()

        if (entries.size != entries.toSet().size ||
            entries.map { it.lowercase(Locale.ROOT) }.distinct().size != entries.size
        ) {
            add(PackageLayoutError.DUPLICATE_ENTRY)
        }
        if (traversalEntries.isNotEmpty()) {
            add(PackageLayoutError.PATH_TRAVERSAL)
        }
        if (invalidEntries.isNotEmpty()) {
            add(PackageLayoutError.INVALID_ENTRY_PATH)
        }
        if (entries.any { entry ->
                entry !in invalidEntries &&
                    entry !in traversalEntries &&
                    entry !in ALLOWED_ENTRIES
            }
        ) {
            add(PackageLayoutError.UNDECLARED_ENTRY)
        }
        if (MANIFEST_ENTRY !in entries) {
            add(PackageLayoutError.MISSING_MANIFEST)
        }
    }.distinct()

    fun validateArchive(
        entries: List<PackageArchiveEntry>,
        declaredExecutableEntries: Set<String>,
        limits: PackageArchiveLimits = PackageArchiveLimits(),
        requiredRuntimeEntry: String? = null,
    ): List<PackageLayoutError> {
        val paths = entries.map(PackageArchiveEntry::path)

        return buildList {
            addAll(validateEntries(paths))
            addAll(validateArchiveEntries(entries, declaredExecutableEntries, paths))
            addAll(validateArchiveSizes(entries, limits))
            addAll(validateCompressionRatios(entries, limits.maximumCompressionRatio))
            addAll(
                validateRuntimeEntry(
                    requiredRuntimeEntry = requiredRuntimeEntry,
                    paths = paths,
                    declaredExecutableEntries = declaredExecutableEntries,
                ),
            )
        }.distinct()
    }

    private fun validateArchiveEntries(
        entries: List<PackageArchiveEntry>,
        declaredExecutableEntries: Set<String>,
        paths: List<String>,
    ): List<PackageLayoutError> = buildList {
        if (entries.any(PackageArchiveEntry::isSymbolicLink)) {
            add(PackageLayoutError.SYMBOLIC_LINK)
        }
        if (entries.any { it.compressedSizeBytes < 0L || it.uncompressedSizeBytes < 0L }) {
            add(PackageLayoutError.INVALID_ENTRY_SIZE)
        }
        if (entries.any { it.isExecutable && it.path !in declaredExecutableEntries }) {
            add(PackageLayoutError.UNDECLARED_EXECUTABLE)
        }
        if (declaredExecutableEntries.any { it !in paths }) {
            add(PackageLayoutError.MISSING_DECLARED_EXECUTABLE)
        }
    }

    private fun validateArchiveSizes(
        entries: List<PackageArchiveEntry>,
        limits: PackageArchiveLimits,
    ): List<PackageLayoutError> = buildList {
        if (entries.size > limits.maximumEntryCount) {
            add(PackageLayoutError.ENTRY_COUNT_LIMIT)
        }

        val compressedBytes = safeSum(entries.map(PackageArchiveEntry::compressedSizeBytes))
        val uncompressedBytes = safeSum(entries.map(PackageArchiveEntry::uncompressedSizeBytes))
        if (compressedBytes == null || uncompressedBytes == null) {
            add(PackageLayoutError.SIZE_OVERFLOW)
            return@buildList
        }
        if (compressedBytes > limits.maximumCompressedBytes) {
            add(PackageLayoutError.COMPRESSED_SIZE_LIMIT)
        }
        if (uncompressedBytes > limits.maximumUncompressedBytes) {
            add(PackageLayoutError.UNCOMPRESSED_SIZE_LIMIT)
        }
    }

    private fun validateCompressionRatios(
        entries: List<PackageArchiveEntry>,
        maximumRatio: Double,
    ): List<PackageLayoutError> {
        val suspicious = entries.any { entry ->
            entry.compressedSizeBytes >= 0L &&
                entry.uncompressedSizeBytes >= 0L &&
                exceedsCompressionRatio(entry, maximumRatio)
        }
        return if (suspicious) {
            listOf(PackageLayoutError.SUSPICIOUS_COMPRESSION_RATIO)
        } else {
            emptyList()
        }
    }

    private fun validateRuntimeEntry(
        requiredRuntimeEntry: String?,
        paths: List<String>,
        declaredExecutableEntries: Set<String>,
    ): List<PackageLayoutError> {
        val mismatch = requiredRuntimeEntry != null &&
            !runtimeEntryMatches(
                requiredRuntimeEntry = requiredRuntimeEntry,
                paths = paths,
                declaredExecutableEntries = declaredExecutableEntries,
            )
        return if (mismatch) {
            listOf(PackageLayoutError.RUNTIME_ENTRY_MISMATCH)
        } else {
            emptyList()
        }
    }

    private fun runtimeEntryMatches(
        requiredRuntimeEntry: String,
        paths: List<String>,
        declaredExecutableEntries: Set<String>,
    ): Boolean {
        val otherRuntimeEntries = RUNTIME_ENTRIES - requiredRuntimeEntry
        return requiredRuntimeEntry in RUNTIME_ENTRIES &&
            requiredRuntimeEntry in paths &&
            requiredRuntimeEntry in declaredExecutableEntries &&
            paths.none(otherRuntimeEntries::contains)
    }

    private fun safeSum(values: List<Long>): Long? = runCatching {
        values.fold(0L, Math::addExact)
    }.getOrNull()

    private fun exceedsCompressionRatio(
        entry: PackageArchiveEntry,
        maximumRatio: Double,
    ): Boolean = when {
        entry.uncompressedSizeBytes <= 0L -> false
        entry.compressedSizeBytes <= 0L -> true
        else -> entry.uncompressedSizeBytes.toDouble() /
            entry.compressedSizeBytes.toDouble() > maximumRatio
    }

    private fun hasTraversalPath(entry: String): Boolean =
        entry.startsWith('/') ||
            entry.startsWith('\\') ||
            DRIVE_PATH.matches(entry) ||
            entry.contains('\\') ||
            entry.split('/').any { it == ".." }

    private fun hasInvalidPath(entry: String): Boolean =
        entry.isBlank() ||
            entry.any(Char::isISOControl) ||
            entry.split('/').any { it.isBlank() || it == "." }

    private const val MANIFEST_ENTRY = "manifest.json"
    private val DRIVE_PATH = Regex("""[A-Za-z]:.*""")
    private val RUNTIME_ENTRIES = setOf("selector.json", "main.js")
    private val ALLOWED_ENTRIES = setOf(
        MANIFEST_ENTRY,
        "selector.json",
        "main.js",
        "CHANGELOG.md",
        "LICENSE",
    )
}
