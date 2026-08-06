package app.openstory.network

import app.openstory.common.Clock

data class RequestBudget(
    val maxRequests: Int = DEFAULT_MAX_REQUESTS,
    val maxDurationMillis: Long =
        DEFAULT_MAX_DURATION_MILLIS,
    val maxCompressedBytes: Long =
        DEFAULT_MAX_COMPRESSED_BYTES,
    val maxDecompressedBytes: Long =
        DEFAULT_MAX_DECOMPRESSED_BYTES,
    val maxDecodedCharacters: Int =
        DEFAULT_MAX_DECODED_CHARACTERS,
) {
    init {
        require(maxRequests > 0) {
            "Request budget must allow at least one request."
        }
        require(maxDurationMillis > 0) {
            "Operation duration limit must be positive."
        }
        require(maxCompressedBytes > 0) {
            "Compressed response limit must be positive."
        }
        require(
            maxDecompressedBytes in
                1..Int.MAX_VALUE.toLong(),
        ) {
            "Decompressed response limit must fit in a ByteArray."
        }
        require(maxDecodedCharacters > 0) {
            "Decoded character limit must be positive."
        }
    }

    private companion object {
        const val DEFAULT_MAX_REQUESTS = 8
        const val DEFAULT_MAX_DURATION_MILLIS =
            30_000L
        const val DEFAULT_MAX_COMPRESSED_BYTES =
            2L * 1024L * 1024L
        const val DEFAULT_MAX_DECOMPRESSED_BYTES =
            8L * 1024L * 1024L
        const val DEFAULT_MAX_DECODED_CHARACTERS =
            2_000_000
    }
}

fun interface PluginRequestRateLimiter {
    fun tryAcquire(
        pluginId: String,
    ): Boolean
}

object UnlimitedPluginRequestRateLimiter :
    PluginRequestRateLimiter {
    override fun tryAcquire(
        pluginId: String,
    ): Boolean =
        true
}

class PerPluginTokenBucket(
    private val capacity: Int,
    private val refillTokens: Int,
    private val refillPeriodMillis: Long,
    private val clock: Clock,
) : PluginRequestRateLimiter {
    private val buckets =
        mutableMapOf<String, Bucket>()

    init {
        require(capacity > 0) {
            "Token bucket capacity must be positive."
        }
        require(refillTokens > 0) {
            "Refill token count must be positive."
        }
        require(refillPeriodMillis > 0) {
            "Refill period must be positive."
        }
    }

    override fun tryAcquire(
        pluginId: String,
    ): Boolean =
        synchronized(buckets) {
            require(pluginId.isNotBlank()) {
                "Plugin ID must not be blank."
            }

            val now = clock.nowEpochMillis()

            val bucket =
                buckets.getOrPut(pluginId) {
                    Bucket(
                        tokens = capacity,
                        lastRefillAt = now,
                    )
                }

            bucket.refill(now)

            if (bucket.tokens > 0) {
                bucket.tokens -= 1
                true
            } else {
                false
            }
        }

    private fun Bucket.refill(
        now: Long,
    ) {
        val elapsed =
            (now - lastRefillAt)
                .coerceAtLeast(0L)

        if (elapsed < refillPeriodMillis) {
            return
        }

        val elapsedPeriods =
            elapsed / refillPeriodMillis

        val replenishedTokens =
            elapsedPeriods *
                refillTokens.toLong()

        tokens =
            (
                tokens.toLong() +
                    replenishedTokens
            )
                .coerceAtMost(capacity.toLong())
                .toInt()

        lastRefillAt +=
            elapsedPeriods *
                refillPeriodMillis
    }

    private data class Bucket(
        var tokens: Int,
        var lastRefillAt: Long,
    )
}
