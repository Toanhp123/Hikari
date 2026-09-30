package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.awaitSingleCompat
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.net.URI
import java.security.MessageDigest

@Suppress("Unused")
abstract class HttpSource : CatalogueSource {
    protected val network: NetworkHelper by injectLazy()

    abstract val baseUrl: String

    open fun getHomeUrl(): String = baseUrl

    open val versionId: Int = 1

    override val id: Long by lazy { generateId(name, lang, versionId) }

    val headers: Headers by lazy { headersBuilder().build() }

    open val client: OkHttpClient get() = network.client

    protected open fun headersBuilder(): Headers.Builder =
        Headers.Builder().set("User-Agent", network.defaultUserAgentProvider())

    override fun toString(): String = "$name (${lang.uppercase()})"

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
    override fun fetchPopularManga(page: Int): Observable<MangasPage> = requestObservable(
        request = { popularMangaRequest(page) },
        parse = ::popularMangaParse,
    )

    protected open fun popularMangaRequest(page: Int): Request =
        throw UnsupportedOperationException("Popular manga is not implemented by $name")

    protected open fun popularMangaParse(response: Response): MangasPage =
        throw UnsupportedOperationException("Popular manga is not implemented by $name")

    @Deprecated("Use getSearchManga instead")
    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> = requestObservable(
        request = { searchMangaRequest(page, query, filters) },
        parse = ::searchMangaParse,
    )

    protected open fun searchMangaRequest(
        page: Int,
        query: String,
        filters: FilterList,
    ): Request = throw UnsupportedOperationException("Search is not implemented by $name")

    protected open fun searchMangaParse(response: Response): MangasPage =
        throw UnsupportedOperationException("Search is not implemented by $name")

    @Deprecated("Use getLatestUpdates instead")
    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> = requestObservable(
        request = { latestUpdatesRequest(page) },
        parse = ::latestUpdatesParse,
    )

    protected open fun latestUpdatesRequest(page: Int): Request =
        throw UnsupportedOperationException("Latest updates are not implemented by $name")

    protected open fun latestUpdatesParse(response: Response): MangasPage =
        throw UnsupportedOperationException("Latest updates are not implemented by $name")

    @Deprecated("Use getMangaUpdate instead")
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = requestObservable(
        request = { mangaDetailsRequest(manga) },
        parse = { response -> mangaDetailsParse(response).apply { initialized = true } },
    )

    open fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    protected open fun mangaDetailsParse(response: Response): SManga =
        throw UnsupportedOperationException("Manga details are not implemented by $name")

    override val supportsRelatedMangas: Boolean get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> =
        requestObservable(
            request = { relatedMangaListRequest(manga) },
            parse = ::relatedMangaListParse,
        ).awaitSingleCompat()

    protected open fun relatedMangaListRequest(manga: SManga): Request =
        throw UnsupportedOperationException("Related manga are not implemented by $name")

    protected open fun relatedMangaListParse(response: Response): List<SManga> =
        throw UnsupportedOperationException("Related manga are not implemented by $name")

    @Deprecated("Use getMangaUpdate instead")
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = requestObservable(
        request = { chapterListRequest(manga) },
        parse = ::chapterListParse,
    )

    protected open fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    protected open fun chapterListParse(response: Response): List<SChapter> =
        throw UnsupportedOperationException("Chapter list is not implemented by $name")

    @Deprecated("Use getPageList instead")
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = requestObservable(
        request = { pageListRequest(chapter) },
        parse = ::pageListParse,
    )

    protected open fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)

    protected open fun pageListParse(response: Response): List<Page> =
        throw UnsupportedOperationException("Page list is not implemented by $name")

    @Deprecated("Use getImageUrl instead")
    open fun fetchImageUrl(page: Page): Observable<String> = requestObservable(
        request = { imageUrlRequest(page) },
        parse = ::imageUrlParse,
    )

    protected open fun imageUrlRequest(page: Page): Request = GET(page.url, headers)

    protected open fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException("Image URL resolution is not implemented by $name")

    @Deprecated("Use getImageUrl/imageRequest instead")
    fun fetchImage(page: Page): Observable<Response> {
        val imageUrl = page.imageUrl
        return if (imageUrl == null) {
            fetchImageUrl(page).flatMap { resolved ->
                page.imageUrl = resolved
                client.newCall(imageRequest(page)).asObservableSuccess()
            }
        } else {
            client.newCall(imageRequest(page)).asObservableSuccess()
        }
    }

    open suspend fun getImageUrl(page: Page): String = fetchImageUrl(page).awaitSingleCompat()

    protected open fun imageRequest(page: Page): Request =
        Request.Builder().url(requireNotNull(page.imageUrl)).headers(headers).get().build()

    suspend fun getImage(page: Page): Response {
        if (page.imageUrl == null) page.imageUrl = getImageUrl(page)
        return client.newCall(imageRequest(page)).awaitSuccess()
    }

    fun SChapter.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    fun SManga.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    open fun getMangaUrl(manga: SManga): String = mangaDetailsRequest(manga).url.toString()

    open fun getChapterUrl(chapter: SChapter): String = pageListRequest(chapter).url.toString()

    open fun prepareNewChapter(chapter: SChapter, manga: SManga) = Unit

    override fun getFilterList(): FilterList = FilterList()

    private fun <T> requestObservable(
        request: () -> Request,
        parse: (Response) -> T,
    ): Observable<T> = Observable.defer {
        client.newCall(request()).asObservableSuccess().map(parse)
    }

    private fun getUrlWithoutDomain(orig: String): String = try {
        val uri = URI(orig.replace(" ", "%20"))
        buildString {
            append(uri.rawPath ?: "/")
            uri.rawQuery?.let { append('?').append(it) }
            uri.rawFragment?.let { append('#').append(it) }
        }
    } catch (_: Exception) {
        orig
    }

    protected fun generateId(name: String, lang: String, versionId: Int): Long {
        val key = "${name.lowercase()}/$lang/$versionId"
        val digest = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        var value = 0L
        for (index in 0 until 8) {
            value = (value shl 8) or (digest[index].toLong() and 0xff)
        }
        return value and Long.MAX_VALUE
    }
}
