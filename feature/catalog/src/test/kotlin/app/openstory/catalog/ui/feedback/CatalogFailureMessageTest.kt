package app.openstory.catalog.ui.feedback

import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogFailureMessageTest {
    @Test
    fun machineFailureCodeUsesUserFacingFallback() {
        assertEquals(
            "Couldn't refresh chapters.",
            catalogFailureMessage("plugin.mangadex_http_status", "Couldn't refresh chapters."),
        )
        assertEquals(
            "Couldn't update reading sources.",
            catalogFailureMessage("library.mapping.search_failed", "Couldn't update reading sources."),
        )
    }

    @Test
    fun humanReadableFailureMessageIsPreserved() {
        assertEquals(
            "Storage is full. Free some space and try again.",
            catalogFailureMessage(
                "Storage is full. Free some space and try again.",
                "This download failed.",
            ),
        )
    }
}
