package app.openstory.catalog.evidence

import app.openstory.catalog.identity.ExternalIdentifier
import app.openstory.catalog.metadata.CatalogMetadataSnapshot
import app.openstory.catalog.metadata.CatalogMetadataStamp
import app.openstory.catalog.model.CatalogEntry
import java.security.MessageDigest

object CatalogEvidenceFingerprints {
    private const val UNSIGNED_BYTE_MASK = 0xFF
    private const val HEX_RADIX = 16

    fun identity(entry: CatalogEntry): String = sha256(
        CanonicalEncoding().apply {
            text(CatalogEvidenceNormalizer.comparisonKey(entry.title))
            normalizedTexts(entry.aliases)
            normalizedTexts(entry.authors)
            text(entry.contentType.name)
            identifiers(entry.externalIdentifiers)
        }.value(),
    )

    fun fusion(snapshot: CatalogMetadataSnapshot): String = sha256(
        CanonicalEncoding().apply {
            val entry = snapshot.entry
            text(entry.title)
            nullableText(entry.description)
            nullableText(entry.coverUrl)
            nullableText(entry.sourceUrl)
            displayTexts(entry.aliases)
            displayTexts(entry.authors)
            displayTexts(entry.genres)
            displayTexts(entry.languageTags)
            nullableText(entry.score?.value?.toString())
            nullableText(entry.score?.scale?.toString())
            nullableText(entry.popularityRank?.toString())
            nullableText(entry.publicationStatus?.name)
            nullableText(entry.latestUpdate?.atEpochMillis?.toString())
            nullableText(entry.latestUpdate?.releaseLabel)
            stamp(snapshot.summary)
            nullableStamp(snapshot.full)
        }.value(),
    )

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte ->
            (byte.toInt() and UNSIGNED_BYTE_MASK).toString(HEX_RADIX).padStart(2, '0')
        }

    private class CanonicalEncoding {
        private val builder = StringBuilder()

        fun text(value: String) {
            builder.append(value.length).append(':').append(value).append(';')
        }

        fun nullableText(value: String?) {
            if (value == null) {
                builder.append("-1:;")
            } else {
                text(value)
            }
        }

        fun normalizedTexts(values: Collection<String>) {
            val normalized = values.asSequence()
                .map(CatalogEvidenceNormalizer::comparisonKey)
                .filter(String::isNotBlank)
                .distinct()
                .sorted()
                .toList()
            collection(normalized)
        }

        fun displayTexts(values: Collection<String>) {
            val stable = values.sortedWith(CatalogEvidenceNormalizer.stableDisplayComparator)
            collection(stable)
        }

        fun identifiers(values: Collection<ExternalIdentifier>) {
            val stable = values.sortedWith(
                compareBy<ExternalIdentifier> { it.namespace }
                    .thenBy { it.scope.name }
                    .thenBy { it.value },
            )
            builder.append(stable.size).append('[')
            stable.forEach { identifier ->
                text(identifier.namespace)
                text(identifier.scope.name)
                text(identifier.value)
            }
            builder.append(']')
        }

        fun stamp(value: CatalogMetadataStamp) {
            text(value.pluginVersion)
        }

        fun nullableStamp(value: CatalogMetadataStamp?) {
            if (value == null) {
                builder.append("0{}")
            } else {
                builder.append("1{")
                stamp(value)
                builder.append('}')
            }
        }

        private fun collection(values: List<String>) {
            builder.append(values.size).append('[')
            values.forEach(::text)
            builder.append(']')
        }

        fun value(): String = builder.toString()
    }
}
