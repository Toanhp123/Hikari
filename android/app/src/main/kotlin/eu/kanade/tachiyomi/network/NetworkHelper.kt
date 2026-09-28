package eu.kanade.tachiyomi.network

import android.content.Context
import android.webkit.CookieManager
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.io.File
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Suppress("Unused")
class NetworkHelper(context: Context) {
    private val appContext = context.applicationContext

    val cookieJar: CookieJar = AndroidWebViewCookieJar()

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(30.seconds)
        .readTimeout(30.seconds)
        .callTimeout(2.minutes)
        .cache(okhttp3.Cache(File(appContext.cacheDir, "extension_network"), 5L * 1024 * 1024))
        .addInterceptor(UncaughtExceptionInterceptor())
        .addInterceptor(UserAgentInterceptor(::defaultUserAgentProvider))
        .addInterceptor(CloudflareInterceptor())
        .build()

    @Deprecated("The regular client handles Cloudflare by default")
    val cloudflareClient: OkHttpClient = client

    fun defaultUserAgentProvider(): String =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/149.0.0.0 Mobile Safari/537.36"
}

private class AndroidWebViewCookieJar : CookieJar {
    private val manager = CookieManager.getInstance().apply { setAcceptCookie(true) }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { manager.setCookie(url.toString(), it.toString()) }
        manager.flush()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        manager.getCookie(url.toString())
            ?.split(';')
            ?.mapNotNull { Cookie.parse(url, it.trim()) }
            .orEmpty()
}
