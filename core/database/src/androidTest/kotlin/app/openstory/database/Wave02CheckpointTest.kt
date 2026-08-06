package app.openstory.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Wave02CheckpointTest {

    @Test
    fun metadataOnlyStorySurvivesDatabaseReopen() =
        runTest {
            withFreshCheckpointDatabase(
                METADATA_DATABASE_NAME,
            ) { context ->
                val story =
                    metadataOnlyStory()

                persistMetadataStory(
                    context = context,
                    story = story,
                )

                assertMetadataStoryAfterReopen(
                    context = context,
                    story = story,
                )
            }
        }

    @Test
    fun twoSourceReleasesPersistUnderOneCanonicalChapter() =
        runTest {
            withFreshCheckpointDatabase(
                RELEASE_DATABASE_NAME,
            ) { context ->
                persistTwoSourceReleases(context)

                assertTwoSourceReleasesAfterReopen(
                    context,
                )
            }
        }

    @Test
    fun sourceRemovalPreservesCanonicalProgress() =
        runTest {
            withFreshCheckpointDatabase(
                SOURCE_REMOVAL_DATABASE_NAME,
            ) { context ->
                persistProgressForSource(context)
                removeSource(context)

                assertSourceRemovalAfterReopen(
                    context,
                )
            }
        }
}
