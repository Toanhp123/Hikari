package app.openstory.benchmark

internal data class BenchmarkFixtureProfileRequest(
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
    companion object {
        val SMALL = BenchmarkFixtureProfileRequest(
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

        val MEDIUM = BenchmarkFixtureProfileRequest(
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

        val AGED = BenchmarkFixtureProfileRequest(
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

        val AGED_CATALOG = SMALL.copy(
            catalogStories = AGED.catalogStories,
            metadataWidth = AGED.metadataWidth,
        )

        val READER_IMAGES_AGED_CACHE = SMALL.copy(
            readerImagePages = SMALL.chapterCount,
            readerAssetMetadataRows = AGED.readerAssetMetadataRows,
            automaticCacheRows = AGED.automaticCacheRows,
        )

        val STRESS = BenchmarkFixtureProfileRequest(
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
    }
}
