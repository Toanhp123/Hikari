package app.openstory.reader.routing

enum class ReaderNetworkState {
    OFFLINE,
    METERED,
    UNMETERED,
    UNKNOWN,
}

fun interface ReaderNetworkFactsPort {
    suspend fun current(): ReaderNetworkState
}
