package app.openstory.reader.engine

@JvmInline
value class BasisPoints(
    val value: Int,
) {
    init {
        require(value in MIN_VALUE..MAX_VALUE) {
            "BasisPoints must be in $MIN_VALUE..$MAX_VALUE: $value"
        }
    }

    companion object {
        const val MIN_VALUE: Int = 0
        const val MAX_VALUE: Int = 10_000
    }
}

@JvmInline
value class ReaderPlanRevision(
    val value: Long,
) {
    init {
        require(value >= 0L) { "ReaderPlanRevision must be non-negative: $value" }
    }
}

@JvmInline
value class ReaderChapterGraphRevision(
    val value: Long,
) {
    init {
        require(value >= 0L) { "ReaderChapterGraphRevision must be non-negative: $value" }
    }
}

@JvmInline
value class SourceGroupKey(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "SourceGroupKey must not be blank." }
    }
}

enum class HesContractVersion {
    HES_V1,
}

enum class ReaderRoutingAlgorithmVersion {
    READER_ROUTING_V1,
}

enum class ReaderPolicyVersion {
    READER_POLICY_V1,
}

enum class HealthPolicyVersion {
    HEALTH_POLICY_V1,
}

enum class RoutingIntent {
    FOREGROUND,
    PREFETCH,
}

enum class ReaderNetworkClass {
    OFFLINE,
    METERED,
    UNMETERED,
    UNKNOWN,
}

enum class AccessMode {
    LOCAL,
    REMOTE,
}

enum class AttemptRole {
    PRIMARY,
    HEDGE,
    FALLBACK,
}

internal fun normalizeLanguageTag(value: String): String =
    value.trim().replace('_', '-').lowercase()
