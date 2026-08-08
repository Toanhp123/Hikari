package app.openstory.plugin.host.selector.binding

@JvmInline
value class SelectorFieldPath private constructor(
    val value: String,
) {
    fun field(name: String): SelectorFieldPath {
        require(name.matches(PATH_SEGMENT)) {
            "Selector field path segment must be a safe machine token."
        }
        return SelectorFieldPath("$value.$name")
    }

    fun index(index: Int): SelectorFieldPath {
        require(index >= 0) {
            "Selector field path index must not be negative."
        }
        return SelectorFieldPath("$value.$index")
    }

    companion object {
        private val PATH_SEGMENT = Regex("[A-Za-z][A-Za-z0-9_]*")

        fun root(name: String): SelectorFieldPath {
            require(name.matches(PATH_SEGMENT)) {
                "Selector field path root must be a safe machine token."
            }
            return SelectorFieldPath(name)
        }
    }
}
