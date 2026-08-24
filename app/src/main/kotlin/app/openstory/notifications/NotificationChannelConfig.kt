package app.openstory.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannelConfig {
    const val CHAPTER_UPDATES_CHANNEL_ID = "chapter-updates"

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHAPTER_UPDATES_CHANNEL_ID,
                "Chapter updates",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "New chapters and preferred-language releases"
                setShowBadge(true)
            },
        )
    }

    fun isEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.getNotificationChannel(CHAPTER_UPDATES_CHANNEL_ID)
            ?.importance
            ?.let { it != NotificationManager.IMPORTANCE_NONE }
            ?: false
    }
}
