package app.openstory.catalog.feature

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverRevision
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef

internal sealed interface CatalogRoute {
    data object Discover : CatalogRoute

    data class Story(
        val ref: StorySourceRef,
        val coverAssetKey: CoverAssetKey?,
    ) : CatalogRoute {
        init {
            require(coverAssetKey == null || coverAssetKey.storyId == ref.storyId)
        }
    }
}

internal object CatalogRouteCodec {
    fun save(route: CatalogRoute): String = when (route) {
        CatalogRoute.Discover -> DISCOVER
        is CatalogRoute.Story -> buildString {
            append(STORY)
            appendField(route.ref.storyId.value)
            appendField(route.ref.catalogSourceKey.value)
            appendField(route.ref.sourceStoryId)
            appendField(route.coverAssetKey?.coverRevision?.value.orEmpty())
        }
    }

    fun restore(saved: String): CatalogRoute = try {
        when {
            saved == DISCOVER -> CatalogRoute.Discover
            saved.startsWith(STORY) -> restoreStory(saved)
            else -> CatalogRoute.Discover
        }
    } catch (_: IllegalArgumentException) {
        CatalogRoute.Discover
    }

    private fun restoreStory(saved: String): CatalogRoute.Story {
        val fields = readFields(saved, startIndex = STORY.length)
        require(fields.size == STORY_FIELD_COUNT)
        val sourceKey = CatalogSourceKey(fields[SOURCE_KEY_INDEX])
        val sourceStoryId = fields[SOURCE_STORY_ID_INDEX]
        val derivedStoryId = SourceStoryIdV1.derive(SourceStoryKey(sourceKey, sourceStoryId))
        require(derivedStoryId.value == fields[STORY_ID_INDEX])
        val ref = StorySourceRef(
            storyId = derivedStoryId,
            catalogSourceKey = sourceKey,
            sourceStoryId = sourceStoryId,
        )
        val coverAssetKey = fields[COVER_REVISION_INDEX].takeIf(String::isNotEmpty)?.let { revision ->
            CoverAssetKey(ref.storyId, CoverRevision(revision))
        }
        return CatalogRoute.Story(ref, coverAssetKey)
    }

    private fun StringBuilder.appendField(value: String) {
        append(FIELD_SEPARATOR)
        append(value.length)
        append(FIELD_SEPARATOR)
        append(value)
    }

    private fun readFields(saved: String, startIndex: Int): List<String> {
        var cursor = startIndex
        return buildList {
            while (cursor < saved.length) {
                require(saved[cursor] == FIELD_SEPARATOR)
                val lengthEnd = saved.indexOf(FIELD_SEPARATOR, startIndex = cursor + 1)
                require(lengthEnd > cursor + 1)
                val fieldLength = saved.substring(cursor + 1, lengthEnd).toIntOrNull()
                require(fieldLength != null && fieldLength >= 0)
                val fieldStart = lengthEnd + 1
                require(fieldLength <= saved.length - fieldStart)
                val fieldEnd = fieldStart + fieldLength
                add(saved.substring(fieldStart, fieldEnd))
                cursor = fieldEnd
            }
        }
    }

    private const val DISCOVER = "discover"
    private const val STORY = "story"
    private const val STORY_FIELD_COUNT = 4
    private const val STORY_ID_INDEX = 0
    private const val SOURCE_KEY_INDEX = 1
    private const val SOURCE_STORY_ID_INDEX = 2
    private const val COVER_REVISION_INDEX = 3
    private const val FIELD_SEPARATOR = ':'
}

internal val CatalogRouteSaver: Saver<CatalogRoute, String> = Saver(
    save = { route -> CatalogRouteCodec.save(route) },
    restore = CatalogRouteCodec::restore,
)

internal class CatalogNavigationState(
    val discoverListState: LazyListState,
    private val routeState: MutableState<CatalogRoute> = mutableStateOf(CatalogRoute.Discover),
) {
    var route: CatalogRoute by routeState
        private set

    fun showStory(ref: StorySourceRef, coverAssetKey: CoverAssetKey?) {
        route = CatalogRoute.Story(ref, coverAssetKey)
    }

    fun showDiscover() {
        route = CatalogRoute.Discover
    }
}
