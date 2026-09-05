package app.openstory.storage.files

import android.system.ErrnoException
import android.system.OsConstants
import java.util.Collections
import java.util.IdentityHashMap

fun interface ReaderAssetStorageErrorClassifier {
    fun isNoSpace(failure: Throwable): Boolean

    companion object {
        val Platform = ReaderAssetStorageErrorClassifier(::containsNoSpaceCause)

        private fun containsNoSpaceCause(failure: Throwable): Boolean {
            val visited = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
            var current: Throwable? = failure
            while (current != null && visited.add(current)) {
                if (current is ErrnoException && current.errno == OsConstants.ENOSPC) return true
                current = current.cause
            }
            return false
        }
    }
}
