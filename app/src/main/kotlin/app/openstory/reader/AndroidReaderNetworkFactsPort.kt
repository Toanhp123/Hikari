package app.openstory.reader

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.openstory.reader.routing.ReaderNetworkFactsPort
import app.openstory.reader.routing.ReaderNetworkState

internal data class ReaderConnectivitySnapshot(
    val hasActiveNetwork: Boolean,
    val validated: Boolean?,
    val metered: Boolean?,
)

internal fun interface ReaderConnectivitySnapshotSource {
    fun read(): ReaderConnectivitySnapshot
}

class AndroidReaderNetworkFactsPort internal constructor(
    private val snapshots: ReaderConnectivitySnapshotSource,
) : ReaderNetworkFactsPort {
    constructor(context: Context) : this(AndroidReaderConnectivitySnapshotSource(context))

    override suspend fun current(): ReaderNetworkState = try {
        val snapshot = snapshots.read()
        when {
            !snapshot.hasActiveNetwork -> ReaderNetworkState.OFFLINE
            snapshot.validated != true || snapshot.metered == null -> ReaderNetworkState.UNKNOWN
            snapshot.metered -> ReaderNetworkState.METERED
            else -> ReaderNetworkState.UNMETERED
        }
    } catch (_: SecurityException) {
        ReaderNetworkState.UNKNOWN
    } catch (_: RuntimeException) {
        ReaderNetworkState.UNKNOWN
    }
}

private class AndroidReaderConnectivitySnapshotSource(
    context: Context,
) : ReaderConnectivitySnapshotSource {
    private val connectivity = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun read(): ReaderConnectivitySnapshot {
        val active = connectivity.activeNetwork
        val capabilities = active?.let(connectivity::getNetworkCapabilities)
        return when {
            active == null -> ReaderConnectivitySnapshot(
                hasActiveNetwork = false,
                validated = null,
                metered = null,
            )
            capabilities == null -> ReaderConnectivitySnapshot(
                hasActiveNetwork = true,
                validated = null,
                metered = null,
            )
            else -> ReaderConnectivitySnapshot(
                hasActiveNetwork = true,
                validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                metered = connectivity.isActiveNetworkMetered,
            )
        }
    }
}
