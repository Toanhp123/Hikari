package app.openstory.storage.room.catalog

internal fun mergeLatestUpdate(
    existingAt: Long?,
    existingLabel: String?,
    incomingAt: Long?,
    incomingLabel: String?,
): Pair<Long?, String?> = when {
    incomingAt == null -> existingAt to existingLabel
    existingAt == null -> incomingAt to incomingLabel
    incomingAt > existingAt -> incomingAt to incomingLabel
    else -> existingAt to existingLabel
}
