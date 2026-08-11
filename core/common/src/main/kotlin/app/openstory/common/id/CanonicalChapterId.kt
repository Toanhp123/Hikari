package app.openstory.common.id

import app.openstory.common.StableId

@JvmInline
value class CanonicalChapterId(
    val value: String,
) {
    init {
        StableId.requireValid(value)
    }
}
