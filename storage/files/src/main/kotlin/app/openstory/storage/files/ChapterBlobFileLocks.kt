package app.openstory.storage.files

import java.io.File
import kotlinx.coroutines.sync.Mutex

/** Coordinates mutations for one committed blob without serializing unrelated blob files. */
internal object ChapterBlobFileLocks {
    private data class Entry(
        val mutex: Mutex = Mutex(),
        var users: Int = 0,
    )

    private val entries = mutableMapOf<String, Entry>()

    suspend fun <T> withLock(file: File, block: suspend () -> T): T {
        val key = file.toPath().toAbsolutePath().normalize().toString()
        val entry = synchronized(entries) {
            entries.getOrPut(key, ::Entry).also { it.users += 1 }
        }
        var locked = false
        return try {
            entry.mutex.lock()
            locked = true
            block()
        } finally {
            if (locked) {
                entry.mutex.unlock()
            }
            synchronized(entries) {
                entry.users -= 1
                if (entry.users == 0 && entries[key] === entry) {
                    entries.remove(key)
                }
            }
        }
    }
}
