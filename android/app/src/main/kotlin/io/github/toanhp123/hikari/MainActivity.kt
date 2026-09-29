package io.github.toanhp123.hikari

import android.content.Intent
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.github.toanhp123.hikari.extensions.mihon.MihonExtensionChannel

class MainActivity : FlutterActivity() {
    private var lnReader: io.github.toanhp123.hikari.extensions.lnreader.LnReaderChannel? = null
    private var localMedia: LocalMediaChannel? = null
    private var mihonExtensions: MihonExtensionChannel? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val messenger = flutterEngine.dartExecutor.binaryMessenger
        lnReader = io.github.toanhp123.hikari.extensions.lnreader.LnReaderChannel(this, messenger)
        localMedia = LocalMediaChannel(this, messenger)
        mihonExtensions = MihonExtensionChannel(this, messenger)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (localMedia?.onActivityResult(requestCode, resultCode, data) != true) {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun cleanUpFlutterEngine(flutterEngine: FlutterEngine) {
        lnReader?.close()
        lnReader = null
        localMedia?.close()
        localMedia = null
        mihonExtensions?.close()
        mihonExtensions = null
        super.cleanUpFlutterEngine(flutterEngine)
    }
}
