package io.github.toanhp123.hikari

import android.content.Intent
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.github.toanhp123.hikari.extensions.mihon.MihonExtensionChannel
import io.github.toanhp123.hikari.localmedia.LocalMediaChannel

class MainActivity : FlutterActivity() {
    private var lnReader: io.github.toanhp123.hikari.extensions.lnreader.LnReaderChannel? = null
    private var localMedia: LocalMediaChannel? = null
    private var mihonExtensions: MihonExtensionChannel? = null
    private var plugins: io.github.toanhp123.hikari.plugins.PluginChannel? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val messenger = flutterEngine.dartExecutor.binaryMessenger
        lnReader = io.github.toanhp123.hikari.extensions.lnreader.LnReaderChannel(this, messenger)
        localMedia = LocalMediaChannel(this, messenger)
        mihonExtensions = MihonExtensionChannel(this, messenger)
        plugins = io.github.toanhp123.hikari.plugins.PluginChannel(this, messenger)
    }

    override fun onResume() {
        super.onResume()
        plugins?.onResume()
    }

    override fun onPause() {
        plugins?.onPause()
        super.onPause()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (plugins?.onActivityResult(requestCode, resultCode) == true) return
        if (localMedia?.onActivityResult(requestCode, resultCode, data) != true) {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun cleanUpFlutterEngine(flutterEngine: FlutterEngine) {
        plugins?.close()
        plugins = null
        lnReader?.close()
        lnReader = null
        localMedia?.close()
        localMedia = null
        mihonExtensions?.close()
        mihonExtensions = null
        super.cleanUpFlutterEngine(flutterEngine)
    }
}
