package app.openstory.catalog.identity

fun interface StoryMergeExecutor {
    suspend fun execute(request: StoryMergeRequest): StoryMergeResult
}
