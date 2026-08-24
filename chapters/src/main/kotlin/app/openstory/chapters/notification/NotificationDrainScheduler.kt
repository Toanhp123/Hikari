package app.openstory.chapters.notification

fun interface NotificationDrainScheduler {
    suspend fun schedule()
}
