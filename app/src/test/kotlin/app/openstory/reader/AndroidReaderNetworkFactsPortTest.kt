package app.openstory.reader

import app.openstory.reader.routing.ReaderNetworkState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidReaderNetworkFactsPortTest {
    @Test
    fun noActiveNetworkIsOffline() = runTest {
        val port = AndroidReaderNetworkFactsPort(
            ReaderConnectivitySnapshotSource {
                ReaderConnectivitySnapshot(hasActiveNetwork = false, validated = null, metered = null)
            },
        )

        assertEquals(ReaderNetworkState.OFFLINE, port.current())
    }

    @Test
    fun validatedMeteredNetworkIsMetered() = runTest {
        val port = AndroidReaderNetworkFactsPort(
            ReaderConnectivitySnapshotSource {
                ReaderConnectivitySnapshot(hasActiveNetwork = true, validated = true, metered = true)
            },
        )

        assertEquals(ReaderNetworkState.METERED, port.current())
    }

    @Test
    fun validatedUnmeteredNetworkIsUnmetered() = runTest {
        val port = AndroidReaderNetworkFactsPort(
            ReaderConnectivitySnapshotSource {
                ReaderConnectivitySnapshot(hasActiveNetwork = true, validated = true, metered = false)
            },
        )

        assertEquals(ReaderNetworkState.UNMETERED, port.current())
    }

    @Test
    fun activeButUnvalidatedNetworkIsUnknown() = runTest {
        val port = AndroidReaderNetworkFactsPort(
            ReaderConnectivitySnapshotSource {
                ReaderConnectivitySnapshot(hasActiveNetwork = true, validated = false, metered = false)
            },
        )

        assertEquals(ReaderNetworkState.UNKNOWN, port.current())
    }

    @Test
    fun missingMeteredFactIsUnknown() = runTest {
        val port = AndroidReaderNetworkFactsPort(
            ReaderConnectivitySnapshotSource {
                ReaderConnectivitySnapshot(hasActiveNetwork = true, validated = true, metered = null)
            },
        )

        assertEquals(ReaderNetworkState.UNKNOWN, port.current())
    }

    @Test
    fun securityAndRuntimeFailuresAreUnknown() = runTest {
        listOf<RuntimeException>(SecurityException("denied"), IllegalStateException("boom")).forEach { failure ->
            val port = AndroidReaderNetworkFactsPort(
                ReaderConnectivitySnapshotSource { throw failure },
            )
            assertEquals(ReaderNetworkState.UNKNOWN, port.current())
        }
    }
}
