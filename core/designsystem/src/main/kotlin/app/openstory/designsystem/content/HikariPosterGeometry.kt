package app.openstory.designsystem.content

class HikariPosterGeometry private constructor(
    val artworkAspectRatio: Float,
) {
    companion object {
        val Standard = HikariPosterGeometry(104f / 150f)
        val Featured = HikariPosterGeometry(136f / 192f)
    }
}
