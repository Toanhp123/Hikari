package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class UncaughtExceptionInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = try {
        chain.proceed(chain.request())
    } catch (error: Exception) {
        if (error is IOException) throw error
        throw IOException(error)
    }
}

class UserAgentInterceptor(private val userAgent: () -> String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.header("User-Agent").isNullOrEmpty()) {
            return chain.proceed(request)
        }
        return chain.proceed(
            request.newBuilder()
                .header("User-Agent", userAgent())
                .build(),
        )
    }
}

// Compatibility hook expected by Keiyoushi's KeiSource.
class CloudflareInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
}
