package app.openstory.benchmark

import android.content.Intent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BenchmarkFixtureProfileTest {
    @Test
    fun `default profile preserves the existing benchmark fixture`() {
        assertEquals(
            BenchmarkFixtureProfile(
                catalogStories = 30,
                progressRows = 12,
                redirectRows = 0,
                libraryEntries = 31,
                explicitDownloadRecords = 0,
                readerImagePages = 0,
                readerAssetMetadataRows = 0,
                automaticCacheRows = 0,
                chapterCount = 12,
                chapterPageSize = 20,
                metadataWidth = 96,
            ),
            BenchmarkFixtureProfile.DEFAULT,
        )
    }

    @Test
    fun `named presets keep independent fixture dimensions explicit`() {
        val presets = listOf(
            BenchmarkFixtureProfile.SMALL,
            BenchmarkFixtureProfile.MEDIUM,
            BenchmarkFixtureProfile.AGED,
            BenchmarkFixtureProfile.STRESS,
        )

        assertEquals(4, presets.distinct().size)
        val changed = BenchmarkFixtureProfile.AGED.copy(progressRows = 111)
        assertEquals(111, changed.progressRows)
        assertEquals(BenchmarkFixtureProfile.AGED.catalogStories, changed.catalogStories)
        assertEquals(BenchmarkFixtureProfile.AGED.libraryEntries, changed.libraryEntries)
        assertEquals(BenchmarkFixtureProfile.AGED.explicitDownloadRecords, changed.explicitDownloadRecords)
        assertEquals(BenchmarkFixtureProfile.AGED.readerAssetMetadataRows, changed.readerAssetMetadataRows)
        assertEquals(BenchmarkFixtureProfile.AGED.automaticCacheRows, changed.automaticCacheRows)
    }

    @Test
    fun `profile rejects negative and unbounded fixture dimensions`() {
        assertFailsWith<IllegalArgumentException> {
            BenchmarkFixtureProfile.DEFAULT.copy(progressRows = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            BenchmarkFixtureProfile.DEFAULT.copy(catalogStories = Int.MAX_VALUE)
        }
        assertFailsWith<IllegalArgumentException> {
            BenchmarkFixtureProfile.DEFAULT.copy(chapterPageSize = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            BenchmarkFixtureProfile.DEFAULT.copy(metadataWidth = 1_000_000)
        }
        assertFailsWith<IllegalArgumentException> {
            BenchmarkFixtureProfile.DEFAULT.copy(explicitDownloadRecords = 13)
        }
    }

    @Test
    fun `intent codec preserves every independent fixture dimension`() {
        val intent = Intent()

        BenchmarkFixtureProfile.STRESS.writeTo(intent)

        assertEquals(BenchmarkFixtureProfile.STRESS, BenchmarkFixtureProfile.from(intent))
    }
}
