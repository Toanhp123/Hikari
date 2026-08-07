package app.openstory.plugin.api.selector

import kotlin.test.Test
import kotlin.test.assertEquals

class SelectorBindingSerializationTest {
    @Test
    fun bindingRoundTripPreservesNestedObjectAndListTypes() {
        val binding: SelectorBinding = ListBinding(
            css = "article.story",
            item = ObjectBinding(
                fields = linkedMapOf(
                    "sourceId" to AttributeBinding(
                        css = "a",
                        attribute = "href",
                    ),
                    "title" to TextBinding(
                        css = ".title",
                        normalizeWhitespace = true,
                    ),
                    "authors" to TextListBinding(
                        css = ".author",
                        value = ElementTextBinding,
                        distinct = true,
                    ),
                ),
            ),
        )

        val encoded = SELECTOR_JSON.encodeToString(
            SelectorBinding.serializer(),
            binding,
        )
        val decoded = SELECTOR_JSON.decodeFromString(
            SelectorBinding.serializer(),
            encoded,
        )

        assertEquals(binding, decoded)
    }

    @Test
    fun timestampRoundTripPreservesClosedConfiguration() {
        val binding: SelectorBinding = TimestampBinding(
            source = TextBinding(css = "time"),
            format = SelectorTimestampFormat.HOST_PATTERN_ID,
            hostPatternId = "month_day_year_short",
            timezoneId = "UTC",
        )

        val encoded = SELECTOR_JSON.encodeToString(
            SelectorBinding.serializer(),
            binding,
        )
        assertEquals(
            binding,
            SELECTOR_JSON.decodeFromString(SelectorBinding.serializer(), encoded),
        )
    }
}
