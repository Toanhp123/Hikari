package app.openstory.common.id

import app.openstory.common.StableId

@JvmInline
value class StoryId(
    val value: String,
) {
    init {
        StableId.requireValid(value)
    }
}
