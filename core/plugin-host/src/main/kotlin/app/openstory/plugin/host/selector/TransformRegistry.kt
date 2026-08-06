package app.openstory.plugin.host.selector

class TransformRegistry {

    fun normalizeWhitespace(
        values: List<String>,
        enabled: Boolean,
    ): List<String> {
        if (!enabled) {
            return values
        }

        return values.map { value ->
            WHITESPACE
                .replace(
                    input = value.trim(),
                    replacement = " ",
                )
        }
    }

    private companion object {
        val WHITESPACE =
            Regex("""\s+""")
    }
}