package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import rx.Observable

@Suppress("Unused")
interface CatalogueSource : Source {
    val lang: String

    val supportsRelatedMangas: Boolean get() = false
    val disableRelatedMangasBySearch: Boolean get() = false
    val disableRelatedMangas: Boolean get() = false

    suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> =
        throw UnsupportedOperationException("Unsupported!")

    override suspend fun getPopularManga(page: Int): MangasPage =
        fetchPopularManga(page).awaitSingleCompat()

    override suspend fun getLatestUpdates(page: Int): MangasPage =
        fetchLatestUpdates(page).awaitSingleCompat()

    override suspend fun getSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage = fetchSearchManga(page, query, filters).awaitSingleCompat()

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = supervisorScope {
        val updatedManga = if (fetchDetails) {
            async { fetchMangaDetails(manga).awaitSingleCompat() }
        } else {
            null
        }
        val updatedChapters = if (fetchChapters) {
            async { fetchChapterList(manga).awaitSingleCompat() }
        } else {
            null
        }
        SMangaUpdate(
            updatedManga?.await() ?: manga,
            updatedChapters?.await() ?: chapters,
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> =
        fetchPageList(chapter).awaitSingleCompat()

    @Deprecated("Use getPopularManga instead")
    fun fetchPopularManga(page: Int): Observable<MangasPage> =
        throw UnsupportedOperationException()

    @Deprecated("Use getSearchManga instead")
    fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> = throw UnsupportedOperationException()

    @Deprecated("Use getLatestUpdates instead")
    fun fetchLatestUpdates(page: Int): Observable<MangasPage> =
        throw UnsupportedOperationException()
}
