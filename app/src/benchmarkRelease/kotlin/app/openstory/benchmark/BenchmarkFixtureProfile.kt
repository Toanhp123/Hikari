package app.openstory.benchmark

import android.content.Intent

data class BenchmarkFixtureProfile(
    val catalogStories: Int,
    val progressRows: Int,
    val redirectRows: Int,
    val libraryEntries: Int,
    val explicitDownloadRecords: Int,
    val readerImagePages: Int,
    val readerAssetMetadataRows: Int,
    val automaticCacheRows: Int,
    val chapterCount: Int,
    val chapterPageSize: Int,
    val metadataWidth: Int,
) {
    init {
        require(catalogStories in 1..MAX_CATALOG_STORIES)
        require(progressRows in 0..MAX_PROGRESS_ROWS)
        require(redirectRows in 0..MAX_REDIRECT_ROWS)
        require(libraryEntries in 1..MAX_LIBRARY_ENTRIES)
        require(explicitDownloadRecords in 0..MAX_EXPLICIT_DOWNLOAD_RECORDS)
        require(readerImagePages in 0..MAX_READER_IMAGE_PAGES)
        require(readerAssetMetadataRows in 0..MAX_READER_ASSET_METADATA_ROWS)
        require(automaticCacheRows in 0..MAX_AUTOMATIC_CACHE_ROWS)
        require(chapterCount in 1..MAX_CHAPTER_COUNT)
        require(chapterPageSize in 1..MAX_CHAPTER_PAGE_SIZE)
        require(metadataWidth in 1..MAX_METADATA_WIDTH)
        require(libraryEntries <= catalogStories + 1)
        require(progressRows <= chapterCount)
        require(explicitDownloadRecords <= chapterCount)
        require(readerImagePages <= chapterCount)
    }

    fun writeTo(intent: Intent) {
        intent.putExtra(EXTRA_CATALOG_STORIES, catalogStories)
        intent.putExtra(EXTRA_PROGRESS_ROWS, progressRows)
        intent.putExtra(EXTRA_REDIRECT_ROWS, redirectRows)
        intent.putExtra(EXTRA_LIBRARY_ENTRIES, libraryEntries)
        intent.putExtra(EXTRA_EXPLICIT_DOWNLOAD_RECORDS, explicitDownloadRecords)
        intent.putExtra(EXTRA_READER_IMAGE_PAGES, readerImagePages)
        intent.putExtra(EXTRA_READER_ASSET_METADATA_ROWS, readerAssetMetadataRows)
        intent.putExtra(EXTRA_AUTOMATIC_CACHE_ROWS, automaticCacheRows)
        intent.putExtra(EXTRA_CHAPTER_COUNT, chapterCount)
        intent.putExtra(EXTRA_CHAPTER_PAGE_SIZE, chapterPageSize)
        intent.putExtra(EXTRA_METADATA_WIDTH, metadataWidth)
    }

    companion object {
        val DEFAULT = BenchmarkFixtureProfile(
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
        )

        val SMALL = DEFAULT

        val MEDIUM = BenchmarkFixtureProfile(
            catalogStories = 300,
            progressRows = 53,
            redirectRows = 17,
            libraryEntries = 127,
            explicitDownloadRecords = 41,
            readerImagePages = 8,
            readerAssetMetadataRows = 211,
            automaticCacheRows = 389,
            chapterCount = 64,
            chapterPageSize = 25,
            metadataWidth = 512,
        )

        val AGED = BenchmarkFixtureProfile(
            catalogStories = 3_000,
            progressRows = 257,
            redirectRows = 1_009,
            libraryEntries = 1_501,
            explicitDownloadRecords = 211,
            readerImagePages = 24,
            readerAssetMetadataRows = 5_003,
            automaticCacheRows = 7_001,
            chapterCount = 320,
            chapterPageSize = 40,
            metadataWidth = 4_096,
        )

        val STRESS = BenchmarkFixtureProfile(
            catalogStories = 12_000,
            progressRows = 1_501,
            redirectRows = 6_007,
            libraryEntries = 8_003,
            explicitDownloadRecords = 1_001,
            readerImagePages = 120,
            readerAssetMetadataRows = 20_003,
            automaticCacheRows = 30_011,
            chapterCount = 2_000,
            chapterPageSize = 75,
            metadataWidth = 16_384,
        )

        fun from(intent: Intent): BenchmarkFixtureProfile = BenchmarkFixtureProfile(
            catalogStories = intent.getIntExtra(EXTRA_CATALOG_STORIES, DEFAULT.catalogStories),
            progressRows = intent.getIntExtra(EXTRA_PROGRESS_ROWS, DEFAULT.progressRows),
            redirectRows = intent.getIntExtra(EXTRA_REDIRECT_ROWS, DEFAULT.redirectRows),
            libraryEntries = intent.getIntExtra(EXTRA_LIBRARY_ENTRIES, DEFAULT.libraryEntries),
            explicitDownloadRecords = intent.getIntExtra(
                EXTRA_EXPLICIT_DOWNLOAD_RECORDS,
                DEFAULT.explicitDownloadRecords,
            ),
            readerImagePages = intent.getIntExtra(EXTRA_READER_IMAGE_PAGES, DEFAULT.readerImagePages),
            readerAssetMetadataRows = intent.getIntExtra(
                EXTRA_READER_ASSET_METADATA_ROWS,
                DEFAULT.readerAssetMetadataRows,
            ),
            automaticCacheRows = intent.getIntExtra(
                EXTRA_AUTOMATIC_CACHE_ROWS,
                DEFAULT.automaticCacheRows,
            ),
            chapterCount = intent.getIntExtra(EXTRA_CHAPTER_COUNT, DEFAULT.chapterCount),
            chapterPageSize = intent.getIntExtra(EXTRA_CHAPTER_PAGE_SIZE, DEFAULT.chapterPageSize),
            metadataWidth = intent.getIntExtra(EXTRA_METADATA_WIDTH, DEFAULT.metadataWidth),
        )

        const val EXTRA_CATALOG_STORIES = "app.openstory.benchmark.CATALOG_STORIES"
        const val EXTRA_PROGRESS_ROWS = "app.openstory.benchmark.PROGRESS_ROWS"
        const val EXTRA_REDIRECT_ROWS = "app.openstory.benchmark.REDIRECT_ROWS"
        const val EXTRA_LIBRARY_ENTRIES = "app.openstory.benchmark.LIBRARY_ENTRIES"
        const val EXTRA_EXPLICIT_DOWNLOAD_RECORDS = "app.openstory.benchmark.EXPLICIT_DOWNLOAD_RECORDS"
        const val EXTRA_READER_IMAGE_PAGES = "app.openstory.benchmark.READER_IMAGE_PAGES"
        const val EXTRA_READER_ASSET_METADATA_ROWS = "app.openstory.benchmark.READER_ASSET_METADATA_ROWS"
        const val EXTRA_AUTOMATIC_CACHE_ROWS = "app.openstory.benchmark.AUTOMATIC_CACHE_ROWS"
        const val EXTRA_CHAPTER_COUNT = "app.openstory.benchmark.CHAPTER_COUNT"
        const val EXTRA_CHAPTER_PAGE_SIZE = "app.openstory.benchmark.CHAPTER_PAGE_SIZE"
        const val EXTRA_METADATA_WIDTH = "app.openstory.benchmark.METADATA_WIDTH"

        private const val MAX_CATALOG_STORIES = 20_000
        private const val MAX_PROGRESS_ROWS = 50_000
        private const val MAX_REDIRECT_ROWS = 50_000
        private const val MAX_LIBRARY_ENTRIES = 20_000
        private const val MAX_EXPLICIT_DOWNLOAD_RECORDS = 20_000
        private const val MAX_READER_IMAGE_PAGES = 2_000
        private const val MAX_READER_ASSET_METADATA_ROWS = 50_000
        private const val MAX_AUTOMATIC_CACHE_ROWS = 50_000
        private const val MAX_CHAPTER_COUNT = 10_000
        private const val MAX_CHAPTER_PAGE_SIZE = 500
        private const val MAX_METADATA_WIDTH = 65_536
    }
}
