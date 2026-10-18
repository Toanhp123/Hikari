package io.github.toanhp123.hikari.plugins

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

/** Platform-inspected package/certificate values, never repository claims or proof of installability. */
internal data class ApkIdentity(
    val family: String, val packageName: String, val versionCode: Long, val versionName: String,
    val library: String, val entrypoint: String, val signers: List<String>, val history: List<String>,
) {
    val compatibility: String get() = when {
        family !in setOf("manga", "anime") || !packageName.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")) ||
            versionCode < 0 || versionName.isBlank() || entrypoint.isBlank() -> "invalidMetadata"
        signers.size != 1 || !signers.all { it.matches(Regex("[a-f0-9]{64}")) } -> "unsupportedSigner"
        family == "manga" && library !in setOf("1.4", "1.6") -> "unsupportedAbi"
        family == "anime" && library !in setOf("14", "16", "17") -> "unsupportedAbi"
        else -> "compatible"
    }
    fun matchExpected(family: String, packageName: String, code: Long, signer: String, installed: ApkIdentity?) {
        require(compatibility == "compatible") { "Unsupported APK: $compatibility" }
        require(this.family == family && this.packageName == packageName && versionCode == code) { "APK metadata differs from offer" }
        require(signers.single() == signer) { "APK signer differs from selected expectation" }
        if (installed != null) {
            require(installed.family == family && installed.compatibility == "compatible") { "Installed package identity unsupported" }
            require(code >= installed.versionCode) { "APK downgrade denied" }
            // Conservative first policy: no signer rotation/replacement, even when
            // platform-authenticated history exists. History is evidence, not consent.
            require(signers == installed.signers) { "Installed signer replacement denied" }
        }
    }
    fun toMap(trusted: Boolean): Map<String, Any> = mapOf("family" to family, "packageName" to packageName,
        "versionCode" to versionCode, "versionName" to versionName, "library" to library,
        "entrypoint" to entrypoint, "signers" to signers, "history" to history,
        "compatibility" to compatibility, "trusted" to trusted)
}

/** Inspection only. No class loader, extension entrypoint or script evaluation. */
internal class ApkInspector(private val context: Context) {
    @Suppress("DEPRECATION")
    fun inventory(): List<ApkIdentity> = context.packageManager.getInstalledPackages(flags()).mapNotNull(::identity)

    @Suppress("DEPRECATION")
    fun packagePresent(packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, flags()); true
    } catch (_: PackageManager.NameNotFoundException) { false }

    @Suppress("DEPRECATION")
    fun inspectInstalled(packageName: String): ApkIdentity? = try {
        identity(context.packageManager.getPackageInfo(packageName, flags()))
    } catch (_: PackageManager.NameNotFoundException) { null }

    @Suppress("DEPRECATION")
    fun installedForUpdate(packageName: String): ApkIdentity? = try {
        identity(context.packageManager.getPackageInfo(packageName, flags()))
            ?: error("Existing package is not an extension; replacement denied")
    } catch (_: PackageManager.NameNotFoundException) { null }

    @Suppress("DEPRECATION")
    fun inspectArchive(file: File): ApkIdentity {
        val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags())
            ?: error("Platform archive inspection failed")
        return identity(info) ?: error("APK is not an extension")
    }

    @Suppress("DEPRECATION")
    fun identity(info: PackageInfo): ApkIdentity? {
        val manga = info.reqFeatures.orEmpty().any { it.name == "tachiyomi.extension" }
        val anime = info.reqFeatures.orEmpty().any { it.name == "tachiyomi.animeextension" }
        if (!manga && !anime) return null
        val family = if (manga) "manga" else "anime"
        val metadata = info.applicationInfo?.metaData
        val classKey = if (family == "manga") "tachiyomi.extension.class" else "tachiyomi.animeextension.class"
        val libKey = if (family == "manga") "tachiyomix.extensionLib" else "aniyomix.extensionLib"
        val raw = metadata?.get(libKey)?.toString()
        val library = if (family == "manga") {
            val parsed = raw?.toDoubleOrNull()?.takeUnless { it == 0.0 }
                ?: info.versionName?.substringBeforeLast('.', "")?.toDoubleOrNull()
            parsed?.toString().orEmpty()
        } else raw?.toIntOrNull()?.toString().orEmpty()
        val current: List<String>
        val history: List<String>
        if (Build.VERSION.SDK_INT >= 28) {
            val signing = info.signingInfo
            current = signing?.apkContentsSigners.orEmpty().map { digest(it.toByteArray()) }.sorted()
            history = if (signing != null && !signing.hasMultipleSigners())
                signing.signingCertificateHistory.orEmpty().map { digest(it.toByteArray()) } else emptyList()
        } else {
            current = info.signatures.orEmpty().map { digest(it.toByteArray()) }.sorted()
            // Pre-28 has no authenticated rotation history API. Current signer
            // remains verified by PackageManager; do not invent lineage evidence.
            history = emptyList()
        }
        return ApkIdentity(family, info.packageName, if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong(),
            info.versionName.orEmpty().ifBlank { "unknown" }, library,
            if (manga && anime) "" else metadata?.getString(classKey).orEmpty(), current, history)
    }
    companion object {
        @Suppress("DEPRECATION")
        fun flags(): Int = PackageManager.GET_CONFIGURATIONS or PackageManager.GET_META_DATA or PackageManager.GET_SIGNATURES or
            if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else 0
        fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
