package app.openstory.catalog.model

data class Score(
    val value: Double,
    val scale: Double,
) {
    init {
        require(scale > 0.0) { "Score scale must be positive" }
        require(value in 0.0..scale) { "Score value must be within its scale" }
    }
}
