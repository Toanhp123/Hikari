package app.openstory.catalog.domain.asset

import app.openstory.catalog.domain.limits.strictUtf8
import java.text.Normalizer
import java.util.Locale

internal object DnsHostCanonicalizer {
    fun canonicalize(rawHost: String): String {
        strictUtf8(rawHost)
        require(rawHost.isNotBlank() && ':' !in rawHost && '[' !in rawHost && ']' !in rawHost)
        val mapped = Normalizer.normalize(mapDots(rawHost), Normalizer.Form.NFKC)
            .uppercase(Locale.ROOT)
            .lowercase(Locale.ROOT)
            .let { Normalizer.normalize(it, Normalizer.Form.NFKC) }
            .removeSuffix(".")
        val canonical = mapped.split('.').joinToString(separator = ".", transform = ::toAsciiLabel)
        require(canonical.isNotBlank() && canonical.length <= MAX_DNS_HOST_CHARS)
        require(!IPV4_PATTERN.matches(canonical))
        return canonical
    }

    private fun mapDots(value: String): String = buildString(value.length) {
        value.forEach { character ->
            append(if (character in IDNA_DOT_EQUIVALENTS) '.' else character)
        }
    }

    private fun toAsciiLabel(label: String): String {
        require(label.isNotEmpty())
        require(label.first() != '-' && label.last() != '-')
        val ascii = if (label.all { it.code < ASCII_LIMIT }) {
            label
        } else {
            validateUnicodeLabel(label)
            ACE_PREFIX + Punycode.encode(label)
        }
        require(ascii.length <= MAX_DNS_LABEL_CHARS)
        require(ascii.first() != '-' && ascii.last() != '-')
        require(ascii.all { it.isAsciiLetterOrDigit() || it == '-' })
        return ascii
    }

    private fun validateUnicodeLabel(label: String) {
        val codePoints = label.codePoints().toArray()
        require(Character.getType(codePoints.first()) !in COMBINING_MARK_TYPES)
        codePoints.forEach { codePoint ->
            require(
                codePoint >= ASCII_LIMIT ||
                    Character.isLetterOrDigit(codePoint) ||
                    codePoint == HYPHEN_CODE_POINT ||
                    Character.isIdentifierIgnorable(codePoint),
            )
            if (codePoint >= ASCII_LIMIT) {
                require(Character.getType(codePoint) in ALLOWED_NON_ASCII_TYPES)
            }
        }
    }
}

private object Punycode {
    fun encode(value: String): String {
        val codePoints = value.codePoints().toArray()
        val output = StringBuilder()
        codePoints.filter { it < INITIAL_CODE_POINT }.forEach { output.append(it.toChar()) }
        val basicCount = output.length
        if (basicCount > 0 && basicCount < codePoints.size) output.append(DELIMITER)

        var state = EncodingState(handled = basicCount)
        while (state.handled < codePoints.size) {
            val nextCodePoint = codePoints.filter { it >= state.next }.minOrNull()
                ?: error("Punycode input has no remaining code point")
            state = state.advanceTo(nextCodePoint)
            codePoints.forEach { codePoint ->
                state = encodeCodePoint(codePoint, state, output, basicCount)
            }
            state = state.nextRound()
        }
        return output.toString()
    }

    private fun encodeCodePoint(
        codePoint: Int,
        state: EncodingState,
        output: StringBuilder,
        basicCount: Int,
    ): EncodingState = when {
        codePoint < state.next -> state.copy(delta = state.delta + 1)
        codePoint != state.next -> state
        else -> {
            var quotient = state.delta
            var weight = BASE
            while (true) {
                val threshold = threshold(weight, state.bias)
                if (quotient < threshold) break
                output.append(encodeDigit(threshold + (quotient - threshold) % (BASE - threshold)))
                quotient = (quotient - threshold) / (BASE - threshold)
                weight += BASE
            }
            output.append(encodeDigit(quotient))
            state.copy(
                delta = 0,
                bias = adapt(state.delta, state.handled + 1, state.handled == basicCount),
                handled = state.handled + 1,
            )
        }
    }

    private fun threshold(weight: Long, bias: Long): Long = when {
        weight <= bias + T_MIN -> T_MIN
        weight >= bias + T_MAX -> T_MAX
        else -> weight - bias
    }

    private fun adapt(deltaValue: Long, pointCount: Int, firstTime: Boolean): Long {
        var delta = if (firstTime) deltaValue / DAMP else deltaValue / TWO
        delta += delta / pointCount
        var adjustment = 0L
        while (delta > ADAPT_LIMIT) {
            delta /= BASE - T_MIN
            adjustment += BASE
        }
        return adjustment + (BASE - T_MIN + 1) * delta / (delta + SKEW)
    }

    private fun encodeDigit(value: Long): Char = when (value) {
        in LETTER_DIGIT_RANGE -> ('a'.code + value.toInt()).toChar()
        in NUMBER_DIGIT_RANGE -> ('0'.code + value.toInt() - LETTER_DIGIT_COUNT).toChar()
        else -> error("Invalid Punycode digit")
    }

    private data class EncodingState(
        val next: Int = INITIAL_CODE_POINT,
        val delta: Long = 0,
        val bias: Long = INITIAL_BIAS,
        val handled: Int,
    ) {
        fun advanceTo(codePoint: Int): EncodingState = copy(
            delta = delta + (codePoint - next).toLong() * (handled + 1),
            next = codePoint,
        )

        fun nextRound(): EncodingState = copy(next = next + 1, delta = delta + 1)
    }
}

private val IPV4_PATTERN = Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")
private val IDNA_DOT_EQUIVALENTS = setOf('\u3002', '\uFF0E', '\uFF61')
private val ALLOWED_NON_ASCII_TYPES = setOf(
    Character.UPPERCASE_LETTER.toInt(),
    Character.LOWERCASE_LETTER.toInt(),
    Character.TITLECASE_LETTER.toInt(),
    Character.MODIFIER_LETTER.toInt(),
    Character.OTHER_LETTER.toInt(),
    Character.NON_SPACING_MARK.toInt(),
    Character.COMBINING_SPACING_MARK.toInt(),
    Character.DECIMAL_DIGIT_NUMBER.toInt(),
)
private val COMBINING_MARK_TYPES = setOf(
    Character.NON_SPACING_MARK.toInt(),
    Character.COMBINING_SPACING_MARK.toInt(),
    Character.ENCLOSING_MARK.toInt(),
)

private const val MAX_DNS_HOST_CHARS = 253
private const val MAX_DNS_LABEL_CHARS = 63
private const val ASCII_LIMIT = 128
private const val INITIAL_CODE_POINT = 128
private const val INITIAL_BIAS = 72L
private const val BASE = 36L
private const val T_MIN = 1L
private const val T_MAX = 26L
private const val SKEW = 38L
private const val DAMP = 700L
private const val TWO = 2L
private const val ADAPT_LIMIT = 455L
private const val DELIMITER = '-'
private const val ACE_PREFIX = "xn--"
private const val LETTER_DIGIT_COUNT = 26
private const val HYPHEN_CODE_POINT = 45
private val LETTER_DIGIT_RANGE = 0L until LETTER_DIGIT_COUNT.toLong()
private val NUMBER_DIGIT_RANGE = LETTER_DIGIT_COUNT.toLong() until BASE
