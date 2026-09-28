package io.github.toanhp123.hikari.extensions

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import dalvik.system.DelegateLastClassLoader
import dalvik.system.PathClassLoader
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
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
) {
    fun toMap(): Map<String, Any> = mapOf(
        "sourceKey" to sourceKey,
        "name" to name,
        "language" to language,
        "packageName" to packageName,
        "baseUrl" to baseUrl,
    )
}

internal class MihonExtensionRuntime(private val context: Context) {
    private data class LoadedSource(
        val descriptor: ExtensionSourceDescriptor,
        val source: HttpSource,
        val libraryVersion: Double,
    )

    private val sources = ConcurrentHashMap<String, LoadedSource>()
    private val loadLock = Any()

    @Volatile
    private var loaded = false

    fun listSources(): List<Map<String, Any>> {
        ensureLoaded()
        return sources.values
            .sortedWith(compareBy({ it.descriptor.name.lowercase() }, { it.descriptor.language }))
            .map { it.descriptor.toMap() }
    }

    suspend fun search(sourceKey: String, query: String): List<Map<String, Any?>> {
        val loadedSource = requireLoadedSource(sourceKey)
        return loadedSource.source
            .getSearchManga(1, query, loadedSource.source.getFilterList())
            .mangas
            .map { manga ->
                mapOf(
                    "title" to manga.title,
                    "url" to manga.url,
                    "memo" to manga.memoOrNull(loadedSource.libraryVersion),
                )
            }
    }

    suspend fun chapters(
        sourceKey: String,
        mangaUrl: String,
        mangaTitle: String?,
        mangaMemo: String?,
    ): List<Map<String, Any?>> {
        val loadedSource = requireLoadedSource(sourceKey)
        val source = loadedSource.source
        val manga = SManga.create().apply {
            url = mangaUrl
            title = mangaTitle ?: mangaUrl
            memo = parseMemo(mangaMemo)
        }
        return source.getMangaUpdate(
            manga = manga,
            chapters = emptyList(),
            fetchDetails = true,
            fetchChapters = true,
        ).chapters.map { chapter ->
            mapOf(
                "title" to chapter.name,
                "url" to chapter.url,
                "scanlator" to chapter.scanlator,
                "chapterNumber" to chapter.chapter_number.toDouble(),
                "dateUpload" to chapter.date_upload,
                "memo" to chapter.memoOrNull(loadedSource.libraryVersion),
            )
        }
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
        if (!isTrustedExtension(packageInfo)) {
            Log.w(TAG, "Skipping untrusted extension ${packageInfo.packageName}")
            return
        }

        val appInfo = packageInfo.applicationInfo ?: return
        val metadata = appInfo.metaData ?: return
        val entrypoint = metadata.getString(META_EXTENSION_CLASS)?.takeIf(String::isNotBlank)
            ?: return
        val libraryVersion = extensionLibraryVersion(packageInfo, metadata) ?: return
        if (libraryVersion !in SUPPORTED_LIBRARY_VERSIONS) return

        runCatching {
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
                    ),
                    source = source,
                    libraryVersion = libraryVersion,
                )
            }
            check(loadedSources.map { it.descriptor.sourceKey }.distinct().size == loadedSources.size) {
                "Extension ${packageInfo.packageName} exposes duplicate source ids."
            }
            val collision = loadedSources.firstOrNull { sources.containsKey(it.descriptor.sourceKey) }
            check(collision == null) {
                "Duplicate extension source id ${collision?.descriptor?.sourceKey} from ${packageInfo.packageName}."
            }
            loadedSources.forEach { source ->
                sources[source.descriptor.sourceKey] = source
            }
        }.onFailure { error ->
            Log.w(TAG, "Skipping incompatible extension ${packageInfo.packageName}", error)
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

    private fun extensionLibraryVersion(
        packageInfo: PackageInfo,
        metadata: android.os.Bundle,
    ): Double? {
        val metadataVersion = when (val value = metadata.get(META_EXTENSION_LIB)) {
            is Number -> value.toString().toDoubleOrNull()
            is String -> value.trim().toDoubleOrNull()
            else -> null
        }
        if (metadataVersion != null && metadataVersion != 0.0) return metadataVersion
        return packageInfo.versionName
            ?.substringBeforeLast('.', missingDelimiterValue = "")
            ?.toDoubleOrNull()
    }

    private fun SManga.memoOrNull(libraryVersion: Double): String? =
        if (libraryVersion >= 1.6) memo.toString() else null

    private fun SChapter.memoOrNull(libraryVersion: Double): String? =
        if (libraryVersion >= 1.6) memo.toString() else null

    private fun isExtensionPackage(packageInfo: PackageInfo): Boolean =
        packageInfo.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE }

    private fun isTrustedExtension(packageInfo: PackageInfo): Boolean =
        signatureDigests(packageInfo).any(TRUSTED_EXTENSION_SIGNATURES::contains)

    @Suppress("DEPRECATION")
    private fun signatureDigests(packageInfo: PackageInfo): List<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = packageInfo.signingInfo ?: return emptyList()
            if (info.hasMultipleSigners()) {
                info.apkContentsSigners
            } else {
                info.signingCertificateHistory
            }
        } else {
            packageInfo.signatures
        }
        return signatures.orEmpty().map { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
        }
    }

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

    private companion object {
        const val TAG = "HikariExtensions"
        const val EXTENSION_FEATURE = "tachiyomi.extension"
        val SUPPORTED_LIBRARY_VERSIONS = setOf(1.4, 1.6)
        const val META_EXTENSION_CLASS = "tachiyomi.extension.class"
        const val META_EXTENSION_LIB = "tachiyomix.extensionLib"
        const val MAX_PAGE_BYTES = 32 * 1024 * 1024

        // Keiyoushi official extension repository signing key (repo.json).
        val TRUSTED_EXTENSION_SIGNATURES = setOf(
            "9add655a78e96c4ec7a53ef89dccb557cb5d767489fac5e785d671a5a75d4da2",
        )

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
