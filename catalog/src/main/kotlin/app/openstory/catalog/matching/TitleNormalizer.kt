package app.openstory.catalog.matching

import java.text.Normalizer
import java.util.Locale

object TitleNormalizer {
    private val nonWord = Regex("[^\\p{L}\\p{N}]+")
    private val whitespace = Regex("\\s+")

    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .replace(nonWord, " ")
        .trim()
        .replace(whitespace, " ")

    fun tokens(value: String): Set<String> = tokensOfNormalized(normalize(value))

    fun similarity(left: String, right: String): Double {
        val normalizedLeft = normalize(left)
        val normalizedRight = normalize(right)
        return similarityNormalized(
            normalizedLeft,
            tokensOfNormalized(normalizedLeft),
            normalizedRight,
            tokensOfNormalized(normalizedRight),
        )
    }

    fun setSimilarity(left: Set<String>, right: Set<String>): Double? {
        if (left.isEmpty() || right.isEmpty()) return null
        val (smaller, larger) = if (left.size <= right.size) left to right else right to left
        var intersectionSize = 0
        smaller.forEach { value ->
            if (value in larger) intersectionSize += 1
        }
        val unionSize = left.size + right.size - intersectionSize
        return intersectionSize.toDouble() / unionSize.toDouble()
    }

    internal fun tokensOfNormalized(value: String): Set<String> = value
        .split(' ')
        .filter(String::isNotBlank)
        .toSet()

    internal fun similarityNormalized(
        normalizedLeft: String,
        leftTokens: Set<String>,
        normalizedRight: String,
        rightTokens: Set<String>,
    ): Double = if (normalizedLeft == normalizedRight && normalizedLeft.isNotEmpty()) {
        1.0
    } else {
        setSimilarity(leftTokens, rightTokens) ?: 0.0
    }
}
