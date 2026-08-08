package app.openstory.plugin.host.selector.binding

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield

class SelectorEvaluationBudget(
    private val maxBoundFields: Int = 100_000,
    private val maxOutputItems: Int = 10_000,
    private val maxTextCharacters: Int = 5_000_000,
    private val maxChapterBlocks: Int = 20_000,
    private val maxChapterTextCharacters: Int = 5_000_000,
    private val maxTotalSpans: Int = 100_000,
    private val maxWallClockMillis: Long = 10_000,
) {
    private val startedAt = TimeSource.Monotonic.markNow()
    private var boundFields = 0
    private var outputItems = 0
    private var textCharacters = 0
    private var chapterBlocks = 0
    private var chapterTextCharacters = 0
    private var totalSpans = 0

    init {
        require(maxBoundFields > 0)
        require(maxOutputItems > 0)
        require(maxTextCharacters > 0)
        require(maxChapterBlocks > 0)
        require(maxChapterTextCharacters > 0)
        require(maxTotalSpans > 0)
        require(maxWallClockMillis > 0)
    }

    suspend fun consumeField() {
        checkpoint()
        boundFields += 1
        enforce(boundFields <= maxBoundFields)
    }

    suspend fun consumeOutputItem() {
        checkpoint()
        outputItems += 1
        enforce(outputItems <= maxOutputItems)
    }

    suspend fun consumeTextCharacters(count: Int) {
        require(count >= 0)
        checkpoint()
        textCharacters += count
        enforce(textCharacters <= maxTextCharacters)
    }

    suspend fun consumeChapterBlock() {
        checkpoint()
        chapterBlocks += 1
        enforce(chapterBlocks <= maxChapterBlocks)
    }

    suspend fun consumeChapterCharacters(count: Int) {
        require(count >= 0)
        checkpoint()
        chapterTextCharacters += count
        enforce(chapterTextCharacters <= maxChapterTextCharacters)
    }

    suspend fun consumeSpans(count: Int) {
        require(count >= 0)
        checkpoint()
        totalSpans += count
        enforce(totalSpans <= maxTotalSpans)
    }

    suspend fun checkpoint() {
        currentCoroutineContext().ensureActive()
        yield()
        if (startedAt.elapsedNow() > maxWallClockMillis.milliseconds) {
            throw SelectorEvaluationLimitExceeded("plugin.selector_timeout")
        }
    }

    private fun enforce(withinLimit: Boolean) {
        if (!withinLimit) {
            throw SelectorEvaluationLimitExceeded("plugin.selector_output_limit")
        }
    }
}

internal class SelectorEvaluationLimitExceeded(
    val code: String,
) : RuntimeException(null, null, false, false)
