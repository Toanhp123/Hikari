package app.openstory.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.openstory.common.id.PluginId
import app.openstory.plugins.runtime.auth.InstalledAuthenticationPolicySource
import app.openstory.plugins.runtime.auth.PluginSessionRecord
import app.openstory.plugins.runtime.auth.PluginSessionService
import app.openstory.plugins.runtime.auth.SecretCookieValue
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class PluginLoginActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var coordinator: PluginLoginCoordinator
    private var captureAcquired = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pluginId = intent.getStringExtra(EXTRA_PLUGIN_ID)?.let(::PluginId) ?: return finishFailure()
        lifecycleScope.launch {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                PluginLoginEntryPoint::class.java,
            )
            val installed = entryPoint.authenticationPolicies().installedAuthenticationPolicies()
                .singleOrNull { it.pluginId == pluginId && it.enabled }
                ?: return@launch finishFailure()
            coordinator = PluginLoginCoordinator(entryPoint.sessionService())
            captureAcquired = coordinator.tryAcquireCapture()
            if (!captureAcquired) return@launch finishFailure()
            val policy = PluginLoginNavigationPolicy(installed.capability)
            clearBrowserState()
            WebView.setWebContentsDebuggingEnabled(false)
            webView = WebView(this@PluginLoginActivity).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)
                settings.mediaPlaybackRequiresUserGesture = true
                settings.setGeolocationEnabled(false)
                setDownloadListener { _, _, _, _, _ -> }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                        if (!policy.allows(url)) return true
                        if (policy.isCompletion(url)) {
                            completeLogin(pluginId, installed.capability, entryPoint.sessionService())
                            return true
                        }
                        return false
                    }
                }
            }
            setContentView(webView)
            webView.loadUrl(installed.capability.loginStartUrl)
        }
    }

    private fun completeLogin(
        pluginId: PluginId,
        capability: app.openstory.plugins.api.manifest.PluginAuthenticationCapability,
        sessions: PluginSessionService,
    ) {
        lifecycleScope.launch {
            try {
                val now = System.currentTimeMillis()
                val records = capability.credentialTargets.flatMap { target ->
                    val url = "https://${target.host}${target.pathPrefix}"
                    parseCookies(CookieManager.getInstance().getCookie(url)).mapNotNull { (name, value) ->
                        name.takeIf { it in target.cookieNames }?.let {
                            PluginSessionRecord(
                                pluginId = pluginId,
                                targetHost = target.host,
                                targetPathPrefix = target.pathPrefix,
                                cookieName = name,
                                cookieValue = SecretCookieValue.of(value),
                                createdAtEpochMillis = now,
                                expiresAtEpochMillis = now + capability.sessionTtlSeconds * 1000,
                                authenticationPolicyFingerprint = capability.policyFingerprint(),
                            )
                        }
                    }
                }
                require(records.isNotEmpty())
                coordinator.complete(pluginId, capability.policyFingerprint(), records)
                setResult(Activity.RESULT_OK)
                finish()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                finishFailure()
            }
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.clearHistory()
            webView.clearFormData()
            webView.clearCache(true)
            webView.destroy()
        }
        clearBrowserState()
        if (captureAcquired) {
            coordinator.releaseCapture()
            captureAcquired = false
        }
        super.onDestroy()
    }

    private fun clearBrowserState() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
    }

    private fun finishFailure() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    private fun parseCookies(header: String?): Map<String, String> = header.orEmpty()
        .split(';')
        .mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) null else part.substring(0, separator).trim() to part.substring(separator + 1).trim()
        }
        .toMap()

    companion object {
        const val EXTRA_PLUGIN_ID = "plugin_id"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PluginLoginEntryPoint {
    fun authenticationPolicies(): InstalledAuthenticationPolicySource
    fun sessionService(): PluginSessionService
}
