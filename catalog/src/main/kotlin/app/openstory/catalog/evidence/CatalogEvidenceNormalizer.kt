package app.openstory.catalog.evidence

import java.text.Normalizer
import java.util.Locale

object CatalogEvidenceNormalizer {
    private val repeatedWhitespace = Regex("\\s+")

    val stableDisplayComparator: Comparator<String> = compareBy<String>(::comparisonKey)
        .thenBy { it }

    fun comparisonKey(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .trim()
        .replace(repeatedWhitespace, " ")
        .lowercase(Locale.ROOT)
}
