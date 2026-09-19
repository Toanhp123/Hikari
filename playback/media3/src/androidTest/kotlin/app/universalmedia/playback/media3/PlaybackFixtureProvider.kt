package app.universalmedia.playback.media3

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

/** Test APK only. Explicit read grants expose one generated, silent MP4. */
class PlaybackFixtureProvider : ContentProvider() {
    private lateinit var fixture: File

    override fun onCreate(): Boolean {
        val context = requireNotNull(context)
        fixture = File(context.cacheDir, "playback-fixture.mp4")
        // A reinstalled test APK can retain cache from an older fixture.
        context.assets.open("fixture.mp4").use { input ->
            fixture.outputStream().use(input::copyTo)
        }
        return true
    }
    override fun getType(uri: Uri): String = "video/mp4"
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = error("read only")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        error("read only")
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = error("read only")
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        require(mode == "r" && uri.path == "/fixture.mp4")
        return ParcelFileDescriptor.open(fixture, ParcelFileDescriptor.MODE_READ_ONLY)
    }
}
