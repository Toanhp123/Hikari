package eu.kanade.tachiyomi

import io.github.toanhp123.hikari.BuildConfig

@Suppress("Unused")
object AppInfo {
    fun getVersionCode(): Int = BuildConfig.VERSION_CODE
    fun getVersionName(): String = BuildConfig.VERSION_NAME
}
