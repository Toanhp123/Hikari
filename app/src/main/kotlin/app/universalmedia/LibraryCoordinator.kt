package app.universalmedia

import app.universalmedia.core.domain.RootRegistrationEvidence
import app.universalmedia.core.domain.StorageRootStore
import app.universalmedia.core.model.RootId
import app.universalmedia.feature.library.LibraryError
import kotlinx.coroutines.CancellationException

internal class LibraryCoordinator(
    private val roots: StorageRootStore,
    private val enqueue: suspend (RootId) -> Unit,
) {
    suspend fun register(evidence: RootRegistrationEvidence): LibraryError? {
        val root = try {
            roots.registerOrReauthorize(evidence)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return LibraryError.STORAGE
        }
        return retry(root.id)
    }

    suspend fun retry(rootId: RootId): LibraryError? = try {
        enqueue(rootId)
        null
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        LibraryError.SCHEDULING
    }
}
