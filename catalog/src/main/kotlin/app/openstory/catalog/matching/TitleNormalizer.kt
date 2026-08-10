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

    fun tokens(value: String): Set<String> = normalize(value)
        .split(' ')
        .filter(String::isNotBlank)
        .toSet()

    fun similarity(left: String, right: String): Double {
        val normalizedLeft = normalize(left)
        val normalizedRight = normalize(right)
        return if (normalizedLeft == normalizedRight && normalizedLeft.isNotEmpty()) 1.0
        else setSimilarity(tokens(left), tokens(right)) ?: 0.0
    }

    fun setSimilarity(left: Set<String>, right: Set<String>): Double? {
        if (left.isEmpty() || right.isEmpty()) return null
        return left.intersect(right).size.toDouble() / left.union(right).size.toDouble()
    }
}
