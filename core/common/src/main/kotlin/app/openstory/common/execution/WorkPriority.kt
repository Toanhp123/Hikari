package app.openstory.common.execution

enum class WorkPriority {
    FOREGROUND_COMMAND,
    VISIBLE_ARTWORK,
    NONCRITICAL,
}

enum class WorkResource {
    NETWORK,
    DECODE,
}
