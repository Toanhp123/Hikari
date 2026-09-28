package io.github.toanhp123.hikari.extensions.mihon

import android.app.Activity
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MihonExtensionChannel(
    private val activity: Activity,
    messenger: BinaryMessenger,
) {
    private val channel = MethodChannel(messenger, "hikari/mihon_extensions")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runtime: MihonExtensionRuntime

    init {
        MihonExtensionHost.initialize(activity.application)
        runtime = MihonExtensionRuntime(activity.applicationContext)
        channel.setMethodCallHandler(::handle)
    }

    private fun handle(call: MethodCall, result: MethodChannel.Result) {
        scope.launch {
            runCatching {
                when (call.method) {
                    "listSources" -> runtime.listSources()
                    "search" -> runtime.search(
                        sourceKey = call.requiredString("sourceKey"),
                        query = call.requiredString("query"),
                    )
                    "chapters" -> runtime.chapters(
                        sourceKey = call.requiredString("sourceKey"),
                        mangaUrl = call.requiredString("mangaUrl"),
                        mangaTitle = call.argument("mangaTitle"),
                        mangaMemo = call.argument("mangaMemo"),
                    )
                    "pages" -> runtime.pages(
                        sourceKey = call.requiredString("sourceKey"),
                        chapterUrl = call.requiredString("chapterUrl"),
                        chapterTitle = call.argument("chapterTitle"),
                        chapterNumber = call.argument<Number>("chapterNumber")?.toDouble(),
                        chapterScanlator = call.argument("chapterScanlator"),
                        chapterDateUpload = call.argument<Number>("chapterDateUpload")?.toLong(),
                        chapterMemo = call.argument("chapterMemo"),
                    )
                    "readPage" -> runtime.readPage(
                        sourceKey = call.requiredString("sourceKey"),
                        rawPage = call.argument<Map<*, *>>("page")
                            ?: error("Missing page."),
                    )
                    else -> return@launch activity.runOnUiThread { result.notImplemented() }
                }
            }.onSuccess { value ->
                activity.runOnUiThread { result.success(value) }
            }.onFailure { error ->
                activity.runOnUiThread {
                    result.error("extension", error.message ?: "Extension operation failed.", null)
                }
            }
        }
    }

    private fun MethodCall.requiredString(name: String): String =
        argument<String>(name) ?: error("Missing $name.")

    fun close() {
        channel.setMethodCallHandler(null)
        scope.cancel()
    }
}
