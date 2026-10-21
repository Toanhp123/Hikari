package io.github.toanhp123.hikari.extensions.mihon

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import dalvik.system.DelegateLastClassLoader
import dalvik.system.PathClassLoader
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import java.io.ByteArrayOutputStream
import io.github.toanhp123.hikari.plugins.ApkInspector
import io.github.toanhp123.hikari.plugins.ApkTrustStore
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

internal data class ExtensionSourceDescriptor(
    val sourceKey: String,
    val name: String,
    val language: String,
    val packageName: String,
    val baseUrl: String,
    val versionCode: Long,
    val signer: String,
) {
    fun toMap(): Map<String, Any> = mapOf(
        "sourceKey" to sourceKey,
        "name" to name,
        "language" to language,
        "packageName" to packageName,
        "baseUrl" to baseUrl,
        "versionCode" to versionCode,
        "signer" to signer,
        "preferencesSupported" to false,
    )
}

internal class MihonExtensionRuntime(private val context: Context) {
    private data class LoadedSource(
        val descriptor: ExtensionSourceDescriptor,
        val source: HttpSource,
        val libraryVersion: Double,
    )

    private val sources = ConcurrentHashMap<String, LoadedSource>()
    private val errors = mutableMapOf<String, String>()
    // Reserve every colliding source ID for this frozen snapshot: no discovery-order winner.
    private val blockedSourceKeys = mutableSetOf<String>()
    private val loadLock = Any()

    /** Evidence only: never loads code on management refresh. */
    fun runtimeSnapshot(): Map<String, Any> = synchronized(loadLock) {
        mapOf("initialized" to loaded, "sources" to sources.values.map { it.descriptor.toMap() },
            "errors" to errors.map { mapOf("packageName" to it.key, "reason" to it.value) },
            "preferencesSupported" to false)
    }

    @Volatile
    private var loaded = false

    fun listSources(): List<Map<String, Any>> {
        ensureLoaded()
        return sources.values
            .sortedWith(compareBy({ it.descriptor.name.lowercase() }, { it.descriptor.language }))
            .map { it.descriptor.toMap() }
    }

    suspend fun search(sourceKey: String, query: String, page: Int): Map<String, Any> {
        require(page > 0) { "Page must be positive." }
        val loadedSource = requireLoadedSource(sourceKey)
        val result = loadedSource.source
            .getSearchManga(page, query, loadedSource.source.getFilterList())
        return mapOf(
            "items" to result.mangas.map { manga -> manga.toMap(loadedSource.libraryVersion) },
            "hasNextPage" to result.hasNextPage,
        )
    }

    private fun SManga.toMap(libraryVersion: Double): Map<String, Any?> = mapOf(
        "title" to title,
        "url" to url,
        "thumbnailUrl" to thumbnail_url,
        "summary" to description,
        "authors" to listOfNotNull(author),
        "artists" to listOfNotNull(artist),
        "genres" to genre?.split(",")?.map(String::trim)?.filter(String::isNotEmpty).orEmpty(),
        "status" to statusName(status),
        "rawStatus" to status.toString(),
        "rating" to null,
        "memo" to memoOrNull(libraryVersion),
    )

    private fun statusName(status: Int): String? = when (status) {
        SManga.ONGOING -> "ongoing"
        SManga.COMPLETED -> "completed"
        SManga.LICENSED -> "licensed"
        SManga.PUBLISHING_FINISHED -> "publishingFinished"
        SManga.CANCELLED -> "cancelled"
        SManga.ON_HIATUS -> "onHiatus"
        else -> null
    }

    suspend fun chapters(
        sourceKey: String,
        mangaUrl: String,
        mangaTitle: String?,
        mangaMemo: String?,
    ): Map<String, Any?> {
        val loadedSource = requireLoadedSource(sourceKey)
        val source = loadedSource.source
        val manga = SManga.create().apply {
            url = mangaUrl
            title = mangaTitle ?: mangaUrl
            memo = parseMemo(mangaMemo)
        }
        val update = source.getMangaUpdate(
            manga = manga,
            chapters = emptyList(),
            fetchDetails = true,
            fetchChapters = true,
        )
        return mapOf(
            "manga" to update.manga.toMap(loadedSource.libraryVersion),
            "chapters" to update.chapters.map { chapter ->
                mapOf(
                    "title" to chapter.name,
                    "url" to chapter.url,
                    "scanlator" to chapter.scanlator,
                    "chapterNumber" to chapter.chapter_number
                        .takeUnless { it == -1f }?.toDouble(),
                    "dateUpload" to chapter.date_upload.takeUnless { it == 0L },
                    "memo" to chapter.memoOrNull(loadedSource.libraryVersion),
                )
            },
        )
    }

    suspend fun readArtwork(sourceKey: String, url: String): ByteArray {
        val source = requireSource(sourceKey)
        return source.client.newCall(eu.kanade.tachiyomi.network.GET(url, source.headers)).awaitSuccess()
            .use { response -> response.body.byteStream().use { it.readLimited(MAX_PAGE_BYTES) } }
    }

    suspend fun pages(
        sourceKey: String,
        chapterUrl: String,
        chapterTitle: String?,
        chapterNumber: Double?,
        chapterScanlator: String?,
        chapterDateUpload: Long?,
        chapterMemo: String?,
    ): List<Map<String, Any?>> {
        val source = requireSource(sourceKey)
        val chapter = SChapter.create().apply {
            url = chapterUrl
            name = chapterTitle ?: chapterUrl
            chapter_number = chapterNumber?.toFloat() ?: -1f
            scanlator = chapterScanlator
            date_upload = chapterDateUpload ?: 0L
            memo = parseMemo(chapterMemo)
        }
        return source.getPageList(chapter).map { page -> page.toMap() }
    }

    suspend fun readPage(sourceKey: String, rawPage: Map<*, *>): ByteArray {
        val source = requireSource(sourceKey)
        val index = (rawPage["index"] as? Number)?.toInt()
            ?: error("Missing page index.")
        val url = rawPage["url"] as? String ?: error("Missing page URL.")
        val imageUrl = rawPage["imageUrl"] as? String
        val uri = rawPage["uri"] as? String
        val page = Page(index = index, url = url, imageUrl = imageUrl).apply {
            if (uri != null) this.uri = android.net.Uri.parse(uri)
        }

        page.uri?.let { pageUri ->
            return context.contentResolver.openInputStream(pageUri).use { input ->
                requireNotNull(input) { "Extension page URI could not be opened." }
                input.readLimited(MAX_PAGE_BYTES)
            }
        }

        return source.getImage(page).use { response ->
            response.body.byteStream().use { it.readLimited(MAX_PAGE_BYTES) }
        }
    }

    private fun Page.toMap(): Map<String, Any?> = mapOf(
        "index" to index,
        "url" to url,
        "imageUrl" to imageUrl,
        "uri" to uri?.toString(),
    )

    private fun requireSource(sourceKey: String): HttpSource =
        requireLoadedSource(sourceKey).source

    private fun requireLoadedSource(sourceKey: String): LoadedSource {
        ensureLoaded()
        return sources[sourceKey]
            ?: error("Extension source $sourceKey is not available.")
    }

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(loadLock) {
            if (loaded) return
            installedPackages().forEach(::loadPackage)
            loaded = true
        }
    }

    private fun loadPackage(packageInfo: PackageInfo) {
        if (!isExtensionPackage(packageInfo)) return
        val inspector = ApkInspector(context)
        val identity = inspector.identity(packageInfo)
        if (identity == null || identity.family != "manga" || identity.compatibility != "compatible") {
            errors[packageInfo.packageName] = "incompatible"; return
        }
        if (!ApkTrustStore.get(context).isTrusted(identity)) {
            errors[packageInfo.packageName] = "untrusted"; return
        }
        val appInfo = packageInfo.applicationInfo ?: return
        val metadata = appInfo.metaData ?: return
        val entrypoint = identity.entrypoint
        val libraryVersion = identity.library.toDouble()
        var failure = "load_failure"
        runCatching {
            check(inspector.inspectInstalled(identity.packageName) == identity) { "Package revision changed" }
            val classLoader = extensionClassLoader(appInfo, packageInfo.packageName)
            val instances = entrypoint
                .split(';', ',')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .flatMap { className ->
                    instantiateSources(classLoader, resolveClassName(packageInfo, className))
                }
            check(instances.all { it is HttpSource }) {
                "Extension ${packageInfo.packageName} exposes an unsupported source type."
            }

            val loadedSources = instances.map { source ->
                source as HttpSource
                val key = source.id.toString()
                LoadedSource(
                    descriptor = ExtensionSourceDescriptor(
                        sourceKey = key,
                        name = source.name,
                        language = source.lang,
                        packageName = packageInfo.packageName,
                        baseUrl = source.baseUrl,
                        versionCode = identity.versionCode,
                        signer = identity.signers.single(),
                    ),
                    source = source,
                    libraryVersion = libraryVersion,
                )
            }
            failure = "collision"
            check(loadedSources.map { it.descriptor.sourceKey }.distinct().size == loadedSources.size) {
                "Extension ${packageInfo.packageName} exposes duplicate source ids."
            }
            val collisions = loadedSources.map { it.descriptor.sourceKey }.filter { key ->
                key in blockedSourceKeys || sources.containsKey(key)
            }.toSet()
            if (collisions.isNotEmpty()) {
                // A duplicate is ambiguous even when the first package was loaded earlier.
                // Revoke every previously captured source from affected packages, not just
                // the duplicate entry; keep the ambiguous IDs unavailable for later packages.
                val displacedPackages = collisions.mapNotNull { key ->
                    sources[key]?.descriptor?.packageName
                }.toSet()
                // A rejected package cannot contribute *any* source ID, even if a
                // later package claims one of its other IDs after it was displaced.
                blockedSourceKeys.addAll(loadedSources.map { it.descriptor.sourceKey })
                blockedSourceKeys.addAll(
                    sources.values.filter { it.descriptor.packageName in displacedPackages }
                        .map { it.descriptor.sourceKey },
                )
                sources.entries.removeAll { (_, loaded) ->
                    loaded.descriptor.packageName in displacedPackages
                }
                displacedPackages.forEach { errors[it] = "collision" }
                error("Ambiguous extension source IDs: ${collisions.joinToString()}")
            }
            failure = "load_failure"
            check(inspector.inspectInstalled(identity.packageName) == identity) { "Package revision changed during loading" }
            loadedSources.forEach { source ->
                sources[source.descriptor.sourceKey] = source
            }
        }.onFailure {
            errors[packageInfo.packageName] = failure
            Log.w(TAG, "Extension package load rejected: $failure")
        }
    }

    private fun instantiateSources(classLoader: ClassLoader, className: String): List<Source> {
        val instance = Class.forName(className, true, classLoader)
            .getDeclaredConstructor()
            .newInstance()
        return when (instance) {
            is SourceFactory -> instance.createSources()
            is Source -> listOf(instance)
            else -> error("Extension entrypoint $className does not expose a Source.")
        }
    }

    private fun extensionClassLoader(appInfo: ApplicationInfo, packageName: String): ClassLoader {
        val sourceDir = requireNotNull(appInfo.sourceDir) {
            "Extension APK path is missing for $packageName."
        }
        val parent = context.classLoader
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            DelegateLastClassLoader(sourceDir, appInfo.nativeLibraryDir, parent)
        } else {
            PathClassLoader(sourceDir, appInfo.nativeLibraryDir, parent)
        }
    }

    private fun parseMemo(raw: String?): JsonObject =
        if (raw.isNullOrBlank()) {
            JsonObject(emptyMap())
        } else {
            Json.parseToJsonElement(raw).jsonObject
        }

    private fun resolveClassName(packageInfo: PackageInfo, name: String): String = when {
        name.startsWith('.') -> packageInfo.packageName + name
        '.' !in name -> packageInfo.packageName + "." + name
        else -> name
    }

    private fun SManga.memoOrNull(libraryVersion: Double): String? =
        if (libraryVersion >= 1.6) memo.toString() else null

    private fun SChapter.memoOrNull(libraryVersion: Double): String? =
        if (libraryVersion >= 1.6) memo.toString() else null

    private fun isExtensionPackage(packageInfo: PackageInfo): Boolean =
        packageInfo.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE }

    @Suppress("DEPRECATION")
    private fun installedPackages(): List<PackageInfo> {
        val manager = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.getInstalledPackages(PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()))
        } else {
            manager.getInstalledPackages(PACKAGE_FLAGS)
        }
    }

    private fun java.io.InputStream.readLimited(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "Extension page exceeds ${limit / 1024 / 1024} MiB." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    companion object {
        private var instance: MihonExtensionRuntime? = null
        @Synchronized fun get(context: Context): MihonExtensionRuntime = instance
            ?: MihonExtensionRuntime(context.applicationContext).also { instance = it }
        private const val TAG = "HikariExtensions"
        const val EXTENSION_FEATURE = "tachiyomi.extension"
        const val MAX_PAGE_BYTES = 32 * 1024 * 1024

        @Suppress("DEPRECATION")
        val PACKAGE_FLAGS = PackageManager.GET_CONFIGURATIONS or
            PackageManager.GET_META_DATA or
            PackageManager.GET_SIGNATURES or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                0
            }
    }
}
