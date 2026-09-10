package app.openstory.designsystem.control

data class HikariSegmentedOption<T>(
    val key: T,
    val label: String,
    val enabled: Boolean = true,
)
