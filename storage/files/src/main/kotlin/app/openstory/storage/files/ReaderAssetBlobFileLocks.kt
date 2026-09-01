package app.openstory.storage.files

import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex

internal class ReaderAssetBlobFileLocks {
    internal class Entry {
        val mutex = Mutex()
        var operationUsers: Int = 0
        var activeLeases: Int = 0
        var deleteRequests: Int = 0
        var zeroLeases = completedSignal()
    }

    internal data class LeaseToken(
        val key: String,
        val entry: Entry,
    )

    internal data class DeleteRequest(
        val key: String,
        val entry: Entry,
        val zeroLeases: CompletableDeferred<Unit>,
    )

    private val entries = mutableMapOf<String, Entry>()

    suspend fun <T> withLock(file: File, block: suspend (Entry) -> T): T {
        val key = fileKey(file)
        val entry = synchronized(entries) {
            entries.getOrPut(key, ::Entry).also { it.operationUsers += 1 }
        }
        var locked = false
        return try {
            entry.mutex.lock()
            locked = true
            block(entry)
        } finally {
            if (locked) entry.mutex.unlock()
            synchronized(entries) {
                entry.operationUsers -= 1
                removeIfIdle(key, entry)
            }
        }
    }

    fun acquireLease(file: File, entry: Entry): LeaseToken? = synchronized(entry) {
        if (entry.deleteRequests > 0) return@synchronized null
        if (entry.activeLeases == 0) entry.zeroLeases = CompletableDeferred()
        entry.activeLeases += 1
        LeaseToken(fileKey(file), entry)
    }

    fun releaseLease(token: LeaseToken) {
        synchronized(token.entry) {
            check(token.entry.activeLeases > 0) { "Reader asset lease count underflow." }
            token.entry.activeLeases -= 1
            if (token.entry.activeLeases == 0) token.entry.zeroLeases.complete(Unit)
        }
        synchronized(entries) { removeIfIdle(token.key, token.entry) }
    }

    fun requestDelete(file: File, entry: Entry): DeleteRequest = synchronized(entry) {
        entry.deleteRequests += 1
        DeleteRequest(fileKey(file), entry, entry.zeroLeases)
    }

    fun finishDelete(request: DeleteRequest) {
        synchronized(request.entry) {
            check(request.entry.deleteRequests > 0) { "Reader asset delete request underflow." }
            request.entry.deleteRequests -= 1
        }
        synchronized(entries) { removeIfIdle(request.key, request.entry) }
    }

    fun hasActiveLeases(entry: Entry): Boolean = synchronized(entry) { entry.activeLeases > 0 }

    private fun removeIfIdle(key: String, entry: Entry) {
        val idle = synchronized(entry) {
            entry.operationUsers == 0 && entry.activeLeases == 0 && entry.deleteRequests == 0
        }
        if (idle && entries[key] === entry) entries.remove(key)
    }

    private fun fileKey(file: File): String = file.toPath().toAbsolutePath().normalize().toString()

    companion object {
        val Shared = ReaderAssetBlobFileLocks()

        private fun completedSignal() = CompletableDeferred(Unit)
    }
}
