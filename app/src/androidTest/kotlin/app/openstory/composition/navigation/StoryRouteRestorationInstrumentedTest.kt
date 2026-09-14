package app.openstory.composition.navigation

import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.read.StoryRouteArgs
import app.openstory.catalog.domain.read.StoryRoutePreview
import app.openstory.common.navigation.RouteEntryId
import app.openstory.navigation.StoryRouteWire
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StoryRouteRestorationInstrumentedTest {
    private val json = Json { ignoreUnknownKeys = false }

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun primitiveStoryWireRestoresToTheSameValidatedRouteArguments() {
        val restorationTester = StateRestorationTester(composeRule)
        val args = StoryRouteArgs(
            ref = REF,
            originMediaContext = CatalogMediaType.MANGA,
            preview = StoryRoutePreview(title = "Restored story"),
        )
        val entryId = RouteEntryId.from("story-restoration-entry")
        var restoredArgs: StoryRouteArgs? = null
        var restoredEntryId: RouteEntryId? = null

        restorationTester.setContent {
            val initialWire = remember(args, entryId) { StoryRouteCodec.encode(args, entryId) }
            val savedWireJson = rememberSaveable { json.encodeToString(initialWire) }
            val restoredWire = json.decodeFromString<StoryRouteWire>(savedWireJson)
            restoredArgs = StoryRouteCodec.decodeOrNull(restoredWire)
            restoredEntryId = runCatching { RouteEntryId.from(restoredWire.entryId) }.getOrNull()
        }

        restorationTester.emulateSavedInstanceStateRestore()

        assertEquals(args, restoredArgs)
        assertEquals(entryId, restoredEntryId)
    }

    private companion object {
        val SOURCE_KEY = CatalogSourceKey("story-restoration-test")
        val REF = StorySourceRef(
            storyId = SourceStoryIdV1.derive(SourceStoryKey(SOURCE_KEY, "story-17")),
            catalogSourceKey = SOURCE_KEY,
            sourceStoryId = "story-17",
        )
    }
}
