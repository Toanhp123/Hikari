package app.openstory.matching

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

    fun tokens(value: String): Set<String> = normalize(value)
        .split(' ')
        .filter(String::isNotBlank)
        .toSet()

    fun similarity(
        left: String,
        right: String,
    ): Double {
        val normalizedLeft = normalize(left)
        val normalizedRight = normalize(right)
        return if (normalizedLeft == normalizedRight && normalizedLeft.isNotEmpty()) {
            FULL_MATCH
        } else {
            setSimilarity(tokens(left), tokens(right)) ?: NO_MATCH
        }
    }

    fun setSimilarity(
        left: Set<String>,
        right: Set<String>,
    ): Double? {
        if (left.isEmpty() || right.isEmpty()) return null
        val intersection = left.intersect(right).size.toDouble()
        val union = left.union(right).size.toDouble()
        return intersection / union
    }

    private const val FULL_MATCH = 1.0
    private const val NO_MATCH = 0.0
}
