package io.github.toanhp123.hikari.localmedia

import android.app.Activity
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.test.InstrumentationTestCase
import android.test.mock.MockContentResolver
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.StandardMethodCodec
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Real MethodChannel codec + resolver/cursor path. No user tree or grant touched. */
@Suppress("DEPRECATION")
class LocalMediaChannelTest : InstrumentationTestCase() {
    private val parent = "content://fixture/tree/root/document/root"
    private lateinit var bridge: LocalMediaChannel
    private lateinit var messenger: Messenger
    private lateinit var provider: Provider
    private lateinit var directory: File

    override fun setUp() {
        super.setUp()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".localmediatest")) {
            "Run only with -PlocalMediaTestApp=true; never install over user app."
        }
        directory = File(context.cacheDir, "bridge-test").apply { mkdirs() }
        provider = Provider(File(directory, "page").apply { writeBytes(byteArrayOf(7)) })
        provider.attachInfo(context, ProviderInfo().apply {
            authority = "fixture"
            exported = true
            applicationInfo = context.applicationInfo
        })
        val resolver = MockContentResolver(context).apply { addProvider("fixture", provider) }
        val activity = object : Activity() {
            override fun getContentResolver() = resolver
            override fun getCacheDir() = directory
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                context.getSharedPreferences("fixture-$name", mode)
        }
        messenger = Messenger()
        instrumentation.runOnMainSync { bridge = LocalMediaChannel(activity, messenger) }
    }

    override fun tearDown() {
        provider.release.countDown()
        closeBridge()
        directory.deleteRecursively()
        super.tearDown()
    }

    fun testBlockedScanDoesNotBlockReaderAndCancelCompletesOnce() {
        provider.block = true
        val scan = invoke("scanChildren", mapOf("id" to parent, "token" to "1", "remaining" to 49999))
        if (!provider.entered.await(5, TimeUnit.SECONDS)) scan.value()
        assertEquals(0L, provider.entered.count)
        val read = invoke("read", mapOf("id" to "$parent/page", "limit" to 32))
        assertEquals(7.toByte(), (read.value() as ByteArray).single())
        invoke("cancelScan", "1").value()
        provider.release.countDown()
        assertEquals("cancelled", scan.errorCode())
        assertEquals(1, scan.completions.get())
        assertTrue(provider.cursorClosed.await(5, TimeUnit.SECONDS))
        drainMain()
        assertTrue(provider.lastCursor?.isClosed == true)
        // Late cancellation must not poison a new token or retain token state.
        invoke("cancelScan", "1").value()
        provider.block = false
        val next = invoke("scanChildren", mapOf("id" to parent, "token" to "2", "remaining" to 49999))
        assertEquals(1, ((next.value() as Map<*, *>)["rows"] as List<*>).size)
    }

    fun testCloseCompletesRunningQueuedAndRejectedCallsOnce() {
        provider.block = true
        val active = invoke("scanChildren", mapOf("id" to parent, "token" to "1", "remaining" to 49999))
        assertTrue(provider.entered.await(5, TimeUnit.SECONDS))
        val queued = invoke("scanChildren", mapOf("id" to parent, "token" to "2", "remaining" to 49999))
        // Saved handler exercises a call already dispatched across shutdown.
        val handler = messenger.handler!!
        closeBridge()
        assertEquals("closed", active.errorCode())
        assertEquals("closed", queued.errorCode())
        val rejected = Reply()
        dispatch(handler, "children", parent, rejected)
        assertEquals("closed", rejected.errorCode())
        provider.release.countDown()
        assertTrue(provider.cursorClosed.await(5, TimeUnit.SECONDS))
        drainMain()
        assertEquals(1, active.completions.get())
        assertEquals(1, queued.completions.get())
    }

    fun testNullLoadingCursorFailureAndRemainingLimitNeverSucceed() {
        for (mode in listOf("null", "loading", "throw", "empty-id", "null-mime", "limit")) {
            provider.mode = mode
            val reply = invoke("scanChildren", mapOf("id" to parent, "token" to mode,
                "remaining" to if (mode == "limit") 0 else 49999))
            assertEquals("storage", reply.errorCode())
            if (mode != "null") assertTrue("cursor closed for $mode", provider.lastCursor?.isClosed == true)
        }
    }

    private fun drainMain() = instrumentation.runOnMainSync { }
    private fun closeBridge() = instrumentation.runOnMainSync { bridge.close() }

    private fun dispatch(handler: BinaryMessenger.BinaryMessageHandler, method: String, arguments: Any?, reply: Reply) {
        instrumentation.runOnMainSync {
            handler.onMessage(StandardMethodCodec.INSTANCE.encodeMethodCall(MethodCall(method, arguments)).apply { flip() }, reply)
        }
    }

    private fun invoke(method: String, arguments: Any?): Reply {
        val reply = Reply()
        instrumentation.runOnMainSync {
            messenger.handler!!.onMessage(
                StandardMethodCodec.INSTANCE.encodeMethodCall(MethodCall(method, arguments)).apply { flip() }, reply)
        }
        return reply
    }

    private class Reply : BinaryMessenger.BinaryReply {
        val completions = AtomicInteger()
        private val ready = CountDownLatch(1)
        private var buffer: ByteBuffer? = null
        override fun reply(reply: ByteBuffer?) {
            completions.incrementAndGet()
            buffer = reply?.apply { flip() }
            ready.countDown()
        }
        fun value(): Any? {
            check(ready.await(5, TimeUnit.SECONDS)) { "MethodChannel reply missing" }
            return StandardMethodCodec.INSTANCE.decodeEnvelope(requireNotNull(buffer))
        }
        fun errorCode(): String = try {
            value()
            "unexpected-success"
        } catch (error: io.flutter.plugin.common.FlutterException) { error.code }
    }

    private class Messenger : BinaryMessenger {
        var handler: BinaryMessenger.BinaryMessageHandler? = null
        override fun send(channel: String, message: ByteBuffer?) = error("Unexpected outbound call")
        override fun send(channel: String, message: ByteBuffer?, callback: BinaryMessenger.BinaryReply?) =
            error("Unexpected outbound call")
        override fun setMessageHandler(channel: String, handler: BinaryMessenger.BinaryMessageHandler?) {
            this.handler = handler
        }
    }

    private class Provider(private val page: File) : ContentProvider() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val cursorClosed = CountDownLatch(1)
        var block = false
        var mode = "ok"
        var lastCursor: Cursor? = null
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?): Cursor? =
            query(uri, projection, selection, selectionArgs, sortOrder, null)
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?, signal: CancellationSignal?): Cursor? {
            if (block) {
                entered.countDown()
                // Deliberately ignore signal: provider interruption is not guaranteed.
                try {
                    release.await(30, TimeUnit.SECONDS)
                } catch (_: InterruptedException) {
                    // Simulate provider returning a cursor despite worker shutdown.
                }
            }
            if (mode == "null") return null
            return object : MatrixCursor(arrayOf("document_id", "_display_name", "mime_type")) {
                init { addRow(arrayOf(if (mode == "empty-id") "" else "page", "1.jpg", if (mode == "null-mime") null else "image/jpeg")) }
                override fun close() {
                    super.close()
                    cursorClosed.countDown()
                }
                override fun getExtras() = Bundle().apply {
                    putBoolean(DocumentsContract.EXTRA_LOADING, mode == "loading")
                }
                override fun onMove(oldPosition: Int, newPosition: Int): Boolean {
                    if (mode == "throw") error("fixture cursor failure")
                    return super.onMove(oldPosition, newPosition)
                }
            }.also { lastCursor = it }
        }
        override fun openFile(uri: Uri, mode: String) =
            ParcelFileDescriptor.open(page, ParcelFileDescriptor.MODE_READ_ONLY)
        override fun getType(uri: Uri) = "image/jpeg"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = error("unused")
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    }
}
