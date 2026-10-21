package io.github.toanhp123.hikari.plugins

import android.test.InstrumentationTestCase
import java.io.File
import java.net.InetAddress
import okhttp3.ResponseBody.Companion.toResponseBody

@Suppress("DEPRECATION")
class PluginFoundationTest : InstrumentationTestCase() {
    private lateinit var directory: File
    override fun setUp() {
        super.setUp()
        check(instrumentation.targetContext.packageName.endsWith(".localmediatest"))
        directory = File(instrumentation.targetContext.cacheDir, "plugins-${System.nanoTime()}").apply { mkdirs() }
    }
    override fun tearDown() { directory.deleteRecursively(); super.tearDown() }

    fun testPublicAddressPolicy() {
        for (address in listOf("0.0.0.0", "10.0.0.1", "127.0.0.1", "100.64.0.1", "169.254.1.1", "172.16.0.1", "192.0.0.9", "192.168.1.1", "192.0.2.1", "198.18.0.1", "198.51.100.1", "203.0.113.1", "224.0.0.1", "240.0.0.1", "::", "::1", "fc00::1", "fe80::1", "ff02::1", "2001:db8::1", "2002:808:808::1", "64:ff9b::a00:1", "::ffff:127.0.0.1")) {
            assertFalse(address, PublicHttps.isPublic(InetAddress.getByName(address).address))
        }
        for (address in listOf("8.8.8.8", "1.1.1.1", "2606:4700:4700::1111")) {
            assertTrue(address, PublicHttps.isPublic(InetAddress.getByName(address).address))
        }
        for (url in listOf("http://example.com/a", "https://u:p@example.com/a", "https://localhost/a", "https://127.0.0.1/a", "https://[fc00::1]/a", "https://example.com/a#x", "https://example.com/a/../b", "https://example.com/%2e%2e/b")) {
            rejects { PublicHttps.url(url) }
        }
        assertEquals("https://example.com/a", PublicHttps.url("https://example.com/a").toString())
        val signed = "https://release-assets.githubusercontent.com/asset?rsct=application%2Foctet-stream&sig=abc%252Fdef"
        assertEquals("application/octet-stream", PublicHttps.url(signed).queryParameter("rsct"))
        assertEquals("release-assets.githubusercontent.com", PublicHttps.redirect(
            PublicHttps.url("https://github.com/org/repo/releases/download/v1/file.apk"), signed, 1).host)
        for (path in listOf("/%2e%2e/asset", "/%252e%252e/asset", "/a%2Fb", "/a%252Fb", "/../asset")) {
            rejects { PublicHttps.url("https://example.com$path?rsct=application%2Foctet-stream") }
            rejects { PublicHttps.redirect(PublicHttps.url("https://example.com/base"), path, 1) }
        }

        rejects { PublicHttps.redirect(PublicHttps.url("https://example.com/a"), "https://10.0.0.1/b", 1) }
        rejects { PublicHttps.redirect(PublicHttps.url("https://example.com/a"), "https://cdn.example.com/b", 6) }
        assertEquals("cdn.example.com", PublicHttps.redirect(PublicHttps.url("https://example.com/a"), "https://cdn.example.com/b", 5).host)
    }

    fun testSingleSignerTrustAndExactIdentity() {
        val original = identity()
        assertEquals("compatible", original.compatibility)
        assertEquals("unsupportedSigner", identity(signers = listOf(A, B)).compatibility)
        assertEquals("unsupportedAbi", identity(library = "1.7").compatibility)
        assertEquals("compatible", identity(family = "anime", library = "17").compatibility)
        assertEquals("unsupportedAbi", identity(family = "anime", library = "18").compatibility)
        assertEquals("invalidMetadata", identity(entrypoint = "").compatibility)
        val store = ApkTrustStore(File(directory, "state.json"))
        assertFalse(store.isTrusted(original))
        store.approve(original)
        assertTrue(ApkTrustStore(File(directory, "state.json")).isTrusted(original))
        assertFalse(store.isTrusted(identity(signers = listOf(B), history = listOf(A, B))))
        assertFalse(store.isTrusted(identity(family = "anime", library = "17")))
        assertFalse(store.isTrusted(identity(signers = listOf(A, B))))
        assertTrue(store.isTrusted(identity(signers = listOf(ApkTrustStore.OFFICIAL_SIGNER))))
        assertFalse(store.isTrusted(identity(signers = listOf(B), history = listOf(ApkTrustStore.OFFICIAL_SIGNER, B))))
        rejects { store.approve(identity(signers = listOf(A, B))) }
        rejects { original.matchExpected("manga", "org.example.other", 10, A, null) }
        rejects { original.matchExpected("anime", original.packageName, 10, A, null) }
        rejects { original.matchExpected("manga", original.packageName, 11, A, null) }
        rejects { original.matchExpected("manga", original.packageName, 10, B, null) }
        rejects { identity(code = 9).matchExpected("manga", original.packageName, 9, A, original) }
        rejects { identity(signers = listOf(B), history = listOf(A, B)).matchExpected("manga", original.packageName, 10, B, original) }
        original.matchExpected("manga", original.packageName, 10, A, original)
    }

    fun testAtomicTrustFailureDoesNotPublish() {
        val file = File(directory, "state.json")
        val store = ApkTrustStore(file)
        store.approve(identity())
        // AtomicFile's new-file path cannot open when occupied by a directory.
        File(directory, "state.json.new").mkdir()
        rejects { store.approve(identity(signers = listOf(B))) }
        assertFalse(store.isTrusted(identity(signers = listOf(B))))
        assertTrue(ApkTrustStore(file).isTrusted(identity()))
    }

    fun testBoundedRedirectTransportAndStageFailure() {
        var hops = 0
        val client = PublicHttps.client(5).newBuilder().addInterceptor { chain ->
            val request = chain.request()
            val builder = okhttp3.Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1).message("fixture")
            when (request.url.encodedPath) {
                "/private" -> builder.code(302).header("Location", "https://127.0.0.1/denied").body("".toResponseBody()).build()
                "/loop" -> { hops++; builder.code(302).header("Location", "/loop").body("".toResponseBody()).build() }
                "/large" -> builder.code(200).body(ByteArray(100).toResponseBody()).build()
                else -> builder.code(200).body("not an APK".toResponseBody()).build()
            }
        }.build()
        val transport = PluginTransport(client)
        fun read(path: String, limit: Int) {
            val request = transport.accept(path)
            try { transport.read("https://example.com/$path", limit, request) }
            finally { transport.finish(request) }
        }
        rejects { read("private", 100) }
        rejects { read("loop", 100) }
        assertEquals(6, hops)
        rejects { read("large", 10) }
        val stages = ApkStages(directory, ApkInspector(instrumentation.targetContext), transport)
        val invalid = transport.accept("invalid")
        try { rejects { stages.stage("https://example.com/invalid", "manga", "org.example.plugin", 10, A, invalid) } }
        finally { transport.finish(invalid) }
        assertTrue(directory.listFiles().orEmpty().none { it.extension == "apk" || it.name.endsWith(".tmp") })
    }

    fun testSignedGithubReleaseRedirectReachesInertStagingTransport() {
        val signed = "https://release-assets.githubusercontent.com/asset?rsct=application%2Foctet-stream&sig=abc%252Fdef"
        val visited = mutableListOf<String>()
        val client = PublicHttps.client(5).newBuilder().addInterceptor { chain ->
            val url = chain.request().url
            visited.add(url.host)
            val response = okhttp3.Response.Builder().request(chain.request())
                .protocol(okhttp3.Protocol.HTTP_1_1).message("fixture")
            if (url.host == "github.com") {
                response.code(302).header("Location", signed).body("".toResponseBody()).build()
            } else {
                assertEquals("application/octet-stream", url.queryParameter("rsct"))
                response.code(200).body("inert bytes".toResponseBody()).build()
            }
        }.build()
        val transport = PluginTransport(client)
        val request = transport.accept("signed-redirect")
        try {
            val bytes = transport.read("https://github.com/org/repo/releases/download/v1/artifact.apk", 128, request)
            assertEquals("inert bytes", bytes.toString(Charsets.UTF_8))
        } finally { transport.finish(request); transport.close() }
        assertEquals(listOf("github.com", "release-assets.githubusercontent.com"), visited)
    }

    fun testTransportReportsSanitizedHttpStatusAndRedirectRejection() {
        val client = PublicHttps.client(5).newBuilder().addInterceptor { chain ->
            val response = okhttp3.Response.Builder().request(chain.request())
                .protocol(okhttp3.Protocol.HTTP_1_1).message("fixture")
            if (chain.request().url.encodedPath == "/unsafe") {
                response.code(302).header("Location", "/%252e%252e/secret")
                    .body("".toResponseBody()).build()
            } else response.code(403).body("private server error".toResponseBody()).build()
        }.build()
        val transport = PluginTransport(client)
        for ((path, expected) in listOf("blocked" to "plugin_http_failed", "unsafe" to "plugin_redirect_rejected")) {
            val request = transport.accept(path)
            try {
                try {
                    transport.read("https://github.com/$path", 128, request)
                    fail("Expected transport failure")
                } catch (error: PluginTransferFailure) {
                    assertEquals(expected, error.code)
                    assertFalse(error.message.orEmpty().contains("private server error"))
                    if (path == "blocked") assertEquals(403, error.status)
                }
            } finally { transport.finish(request) }
        }
        transport.close()
    }

    fun testAcceptedRequestLifecycle() {
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val client = PublicHttps.client(5).newBuilder().addInterceptor { chain ->
            entered.countDown()
            check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200).message("fixture").body("ok".toResponseBody()).build()
        }.build()
        val transport = PluginTransport(client)
        repeat(1000) { transport.cancel("unknown-$it") }
        rejects { transport.cancel("bad id") }
        // Accepted but queued: cancellation survives until worker starts; terminal cleanup frees ID.
        val queued = transport.accept("queued")
        transport.cancel("queued")
        rejects { transport.read("https://example.com/ok", 100, queued) }
        transport.finish(queued)
        val reused = transport.accept("queued")
        transport.finish(queued) // Stale cleanup cannot delete replacement with same ID.
        rejects { transport.accept("queued") }
        transport.finish(reused)
        val active = transport.accept("active")
        val failure = java.util.concurrent.atomic.AtomicReference<Throwable>()
        val thread = Thread {
            try { transport.read("https://example.com/ok", 100, active) }
            catch (error: Throwable) { failure.set(error) }
            finally { transport.finish(active) }
        }.apply { start() }
        assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
        rejects { transport.accept("active") }
        transport.cancel("active")
        release.countDown()
        thread.join(5000)
        assertFalse(thread.isAlive)
        assertNotNull(failure.get())
        repeat(1000) { transport.cancel("active") }
        val accepted = (0..4).map { transport.accept("bounded-$it") }
        rejects { transport.accept("overflow") }
        transport.finish(accepted.last()) // Queue rejection cleanup uses own handle.
        val replacement = transport.accept("replacement")
        transport.close()
        rejects { transport.read("https://example.com/ok", 100, replacement) }
        accepted.forEach(transport::finish)
        transport.finish(replacement)
        rejects { transport.accept("closed") }
    }

    fun testCancellationAtRedirectBoundary() {
        lateinit var transport: PluginTransport
        var hops = 0
        val client = PublicHttps.client(5).newBuilder().addInterceptor { chain ->
            hops++
            transport.cancel("redirect")
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(302).message("fixture").header("Location", "/next").body("".toResponseBody()).build()
        }.build()
        transport = PluginTransport(client)
        val request = transport.accept("redirect")
        try { rejects { transport.read("https://example.com/start", 100, request) } }
        finally { transport.finish(request) }
        assertEquals(1, hops)
        transport.finish(transport.accept("redirect"))
        transport.close()
    }

    fun testQueuedChannelCancellationAndRejectionCleanup() {
        // Hold worker before any network access; test actual channel acceptance/error paths.
        val messenger = java.lang.reflect.Proxy.newProxyInstance(
            io.flutter.plugin.common.BinaryMessenger::class.java.classLoader,
            arrayOf(io.flutter.plugin.common.BinaryMessenger::class.java)
        ) { _, _, _ -> null } as io.flutter.plugin.common.BinaryMessenger
        val channel = PluginChannel(instrumentation.targetContext, messenger)
        val worker = PluginChannel::class.java.getDeclaredField("worker").apply { isAccessible = true }
            .get(channel) as java.util.concurrent.ThreadPoolExecutor
        val transport = PluginChannel::class.java.getDeclaredField("transport").apply { isAccessible = true }
            .get(channel) as PluginTransport
        val handle = PluginChannel::class.java.getDeclaredMethod("handle", io.flutter.plugin.common.MethodCall::class.java,
            io.flutter.plugin.common.MethodChannel.Result::class.java).apply { isAccessible = true }
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        fun invoke(method: String, id: String): ChannelResult {
            val result = ChannelResult()
            handle.invoke(channel, io.flutter.plugin.common.MethodCall(method,
                mapOf("requestId" to id, "url" to "https://example.com/unused")), result)
            return result
        }
        try {
            worker.execute { entered.countDown(); check(release.await(5, java.util.concurrent.TimeUnit.SECONDS)) }
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            val queued = (0..3).map { invoke("metadata", "queued-$it") }
            assertEquals("plugin_operation_failed", invoke("metadata", "queued-0").error)
            assertEquals("plugin_operation_failed", invoke("cancel", "bad id").error)
            assertEquals("plugin_manager_busy", invoke("metadata", "rejected").error)
            // Rejected task released its own handle without disturbing queued ones.
            transport.finish(transport.accept("rejected"))
            (0..3).forEach { invoke("cancel", "queued-$it") }
            release.countDown()
            queued.forEach {
                assertTrue(it.completed.await(5, java.util.concurrent.TimeUnit.SECONDS))
                assertEquals("plugin_operation_failed", it.error)
            }
            // All queued pre-cancels reached terminal cleanup, not merely close cleanup.
            (0..3).forEach { transport.finish(transport.accept("queued-$it")) }
            val closingEntered = java.util.concurrent.CountDownLatch(1)
            val closingRelease = java.util.concurrent.CountDownLatch(1)
            worker.execute { closingEntered.countDown(); check(closingRelease.await(5, java.util.concurrent.TimeUnit.SECONDS)) }
            assertTrue(closingEntered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            val closing = invoke("metadata", "closing")
            channel.close()
            closingRelease.countDown()
            assertTrue(closing.completed.await(5, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals("plugin_operation_failed", closing.error)
            assertTrue(worker.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS))
        } finally { release.countDown(); channel.close() }
    }

    fun testConfirmationQueuesWithoutBlockingMainAndCloseSuppressesLaunch() {
        val messenger = java.lang.reflect.Proxy.newProxyInstance(
            io.flutter.plugin.common.BinaryMessenger::class.java.classLoader,
            arrayOf(io.flutter.plugin.common.BinaryMessenger::class.java)
        ) { _, _, _ -> null } as io.flutter.plugin.common.BinaryMessenger
        val channel = PluginChannel(instrumentation.targetContext, messenger)
        val worker = PluginChannel::class.java.getDeclaredField("worker").apply { isAccessible = true }
            .get(channel) as java.util.concurrent.ThreadPoolExecutor
        val handle = PluginChannel::class.java.getDeclaredMethod("handle", io.flutter.plugin.common.MethodCall::class.java,
            io.flutter.plugin.common.MethodChannel.Result::class.java).apply { isAccessible = true }
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val result = ChannelResult()
        try {
            worker.execute { entered.countDown(); check(release.await(5, java.util.concurrent.TimeUnit.SECONDS)) }
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                channel.onResume()
                handle.invoke(channel, io.flutter.plugin.common.MethodCall("resumeInstallConfirmation",
                    mapOf("operationId" to java.util.UUID.randomUUID().toString())), result)
            }
            // Main invocation returned while worker still blocked; no singleton/journal IO on main.
            assertEquals(1L, result.completed.count)
            val responsive = java.util.concurrent.CountDownLatch(1)
            android.os.Handler(android.os.Looper.getMainLooper()).post { responsive.countDown() }
            assertTrue(responsive.await(1, java.util.concurrent.TimeUnit.SECONDS))
            instrumentation.runOnMainSync { channel.onPause(); channel.close() }
            release.countDown()
            assertTrue(result.completed.await(5, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals("plugin_operation_failed", result.error)
            assertTrue(worker.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS))
        } finally { release.countDown(); channel.close() }
    }

    fun testStaleConfirmationLifecycleDispatchNeverLaunches() {
        val messenger = java.lang.reflect.Proxy.newProxyInstance(
            io.flutter.plugin.common.BinaryMessenger::class.java.classLoader,
            arrayOf(io.flutter.plugin.common.BinaryMessenger::class.java)
        ) { _, _, _ -> null } as io.flutter.plugin.common.BinaryMessenger
        lateinit var activity: android.app.Activity
        instrumentation.runOnMainSync {
            activity = object : android.app.Activity() {
                override fun getApplicationContext() = instrumentation.targetContext.applicationContext
                override fun getFilesDir() = File(directory, "activity-files").apply { mkdirs() }
                override fun startActivity(intent: android.content.Intent) { fail("Stale confirmation launched") }
            }
        }
        val channel = PluginChannel(activity, messenger)
        val operation = ApkInstallOperation(java.util.UUID.randomUUID().toString(), "manga", "org.example.plugin", 10,
            A, "install", java.util.UUID.randomUUID().toString(), status = InstallStatus.PENDING_USER_ACTION)
        val ticket = ApkInstaller.Confirmation(operation, android.content.Intent("inert.never.launched"))
        try {
            instrumentation.runOnMainSync {
                channel.onResume()
                channel.onPause()
                rejects { channel.dispatchConfirmation(ticket, 0) }
                channel.onResume()
                rejects { channel.dispatchConfirmation(ticket, 0) } // Old epoch stays stale after resume.
                ticket.invalidate()
                rejects { channel.dispatchConfirmation(ticket, 1) }
                channel.close()
                rejects { channel.dispatchConfirmation(ticket, 1) }
            }
        } finally { channel.close() }
    }

    fun testExplicitStageDiscard() {
        val transport = PluginTransport()
        val stages = ApkStages(directory, ApkInspector(instrumentation.targetContext), transport)
        // Inert synthetic stage exercises release without executing or approving an APK.
        @Suppress("UNCHECKED_CAST")
        val entries = ApkStages::class.java.getDeclaredField("stages").apply { isAccessible = true }
            .get(stages) as MutableMap<String, Pair<File, ApkIdentity>>
        val kept = File(directory, "kept.apk").apply { writeText("inert") }
        val dismissed = File(directory, "dismissed.apk").apply { writeText("inert") }
        entries["kept"] = kept to identity()
        entries["dismissed"] = dismissed to identity()
        stages.discard("dismissed")
        assertFalse(dismissed.exists())
        assertTrue(kept.exists())
        assertEquals(setOf("kept"), entries.keys)
        rejects { stages.inspected("dismissed") }
        rejects { stages.discard("dismissed") }
        stages.close()
        transport.close()
    }

    private class ChannelResult : io.flutter.plugin.common.MethodChannel.Result {
        val completed = java.util.concurrent.CountDownLatch(1)
        @Volatile var error: String? = null
        override fun success(result: Any?) { completed.countDown() }
        override fun error(code: String, message: String?, details: Any?) { error = code; completed.countDown() }
        override fun notImplemented() { error = "not_implemented"; completed.countDown() }
    }

    fun testInspectionNeverLoadsArchiveCode() {
        val inspector = ApkInspector(instrumentation.targetContext)
        val identity = inspector.inspectInstalled(instrumentation.targetContext.packageName)
        assertNull(identity) // Ordinary app has no extension feature.
        rejects { inspector.installedForUpdate(instrumentation.targetContext.packageName) }
        val invalid = File(directory, "invalid.apk").apply { writeText("not an APK") }
        rejects { inspector.inspectArchive(invalid) }
        val installed = inspector.inventory()
        assertTrue(installed.all { it.family in setOf("manga", "anime") })
        installed.forEach { assertTrue(it.signers.all { signer -> signer.matches(Regex("[a-f0-9]{64}")) }) }
        // Device-dependent inventory evidence; no package is installed or executed.
        android.util.Log.i("HikariPluginTest", "Inspected extension packages: ${installed.map { it.packageName }}")
    }

    private fun identity(family: String = "manga", library: String = "1.6", code: Long = 10,
        signers: List<String> = listOf(A), history: List<String> = signers, entrypoint: String = ".Plugin") =
        ApkIdentity(family, "org.example.plugin", code, "1.6.10", library, entrypoint, signers, history)
    private fun rejects(action: () -> Unit) {
        try { action(); fail("Expected rejection") } catch (expected: IllegalArgumentException) {
        } catch (expected: IllegalStateException) {
        } catch (expected: java.io.IOException) { }
    }
    companion object { private val A = "a".repeat(64); private val B = "b".repeat(64) }
}
