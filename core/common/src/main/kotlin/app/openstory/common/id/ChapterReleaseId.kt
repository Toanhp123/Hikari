package app.openstory.common.id

import app.openstory.common.StableId

@JvmInline
value class ChapterReleaseId(
    val value: String,
) {
    init {
        StableId.requireValid(value)
    }
}
