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
                        title = "Catalog Story",
                        description = "Description",
                        score = 8.4,
                        scoreScale = 10.0,
                        externalStoryId = "story-1",
                        sourceUrl = "https://catalog.example/story-1",
                        authors = linkedSetOf("Author A", "Author B"),
                        genres = linkedSetOf("Fantasy", "Adventure"),
                        coverReference = "https://catalog.example/cover.jpg",
                        publicationStatus = "ONGOING",
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
