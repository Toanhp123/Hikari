package app.openstory.database.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openstory.database.OpenStoryDatabase
import app.openstory.model.CanonicalStory
import app.openstory.model.CatalogEntry
import app.openstory.model.CatalogEntryId
import app.openstory.model.ContentType
import app.openstory.model.LibraryStatus
import app.openstory.model.LanguageTag
import app.openstory.model.PluginId
import app.openstory.model.StoryId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogMetadataRepositoryTest {

    @Test
    fun catalogMetadataRoundTripsLosslessly() = runTest {
        val database = createDatabase()

        try {
            val repository = RoomStoryRepository(database)
            val story = CanonicalStory(
                id = StoryId("catalog-story"),
                contentType = ContentType.WEB_NOVEL,
                preferredTitle = "Catalog Story",
                aliases = setOf("Alias"),
                catalogEntries = listOf(
                    CatalogEntry(
                        id = CatalogEntryId("catalog.example:story-1"),
                        catalogPluginId = PluginId("catalog.example"),
                        externalStoryId = "story-1",
                        sourceUrl = "https://catalog.example/story-1",
                        title = "Catalog Story",
                        aliases = setOf("Catalog Alias"),
                        authors = linkedSetOf("Author A", "Author B"),
                        description = "Description",
                        genres = linkedSetOf("Fantasy", "Adventure"),
                        contentType = ContentType.WEB_NOVEL,
                        languageTags = setOf(LanguageTag("en")),
                        coverReference = "https://catalog.example/cover.jpg",
                        publicationStatus = "ONGOING",
                        score = 8.4,
                        scoreScale = 10.0,
                        popularityRank = 12L,
                        pluginVersion = "1.0.0",
                        fetchedAtEpochMillis = 1_000L,
                    ),
                ),
            )

            repository.addToLibrary(
                story = story,
                status = LibraryStatus.WANT_TO_READ,
            )

            assertEquals(
                story,
                repository.observeStory(story.id).first(),
            )
        } finally {
            database.close()
        }
    }

    private fun createDatabase(): OpenStoryDatabase =
        Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OpenStoryDatabase::class.java,
        ).build()
}
