package app.universalmedia.data

internal fun nextProgressRevision(current: Long, expected: Long): Long? {
    require(expected >= 0)
    return if (current == expected) Math.addExact(current, 1) else null
}

internal fun representationRevision(
    current: Long,
    oldSize: Long?,
    oldModified: Long?,
    newSize: Long?,
    newModified: Long?,
): Long = if (oldSize == newSize &&
    oldModified == newModified
) {
    current
} else {
    Math.addExact(current, 1)
}
