package app.openstory.model

import kotlinx.serialization.Serializable

@Serializable
enum class LibraryStatus {
    WANT_TO_READ,
    READING,
    PAUSED,
    COMPLETED,
    DROPPED,
}
