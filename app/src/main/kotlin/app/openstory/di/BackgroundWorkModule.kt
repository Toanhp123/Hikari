package app.openstory.di

import android.content.Context
import app.openstory.chapters.notification.ChapterNotificationTargetSource
import app.openstory.chapters.notification.NotificationDrainScheduler
import app.openstory.catalog.orchestration.CanonicalEngineWorkScheduler
import app.openstory.catalog.orchestration.PostMergeDerivedWorkDispatcher
import app.openstory.chapters.maintenance.ChapterReaggregator
import app.openstory.work.WorkManagerCanonicalEngineWorkScheduler
import app.openstory.work.WorkManagerPostMergeDerivedWorkDispatcher
import app.openstory.navigation.DefaultNotificationIntentParser
import app.openstory.navigation.NotificationIntentParser
import app.openstory.notifications.AndroidChapterNotifier
import app.openstory.notifications.NotificationDeepLinkFactory
import app.openstory.notifications.NotificationDispatcher
import app.openstory.notifications.NotificationPermissionGate
import app.openstory.notifications.StoryNotificationBuilder
import app.openstory.notifications.WorkManagerNotificationDrainScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BackgroundWorkModule {
    @Provides
    @Singleton
    fun provideNotificationDrainScheduler(
        @ApplicationContext context: Context,
    ): WorkManagerNotificationDrainScheduler = WorkManagerNotificationDrainScheduler(context)

    @Provides
    fun provideNotificationDrainSchedulerPort(
        scheduler: WorkManagerNotificationDrainScheduler,
    ): NotificationDrainScheduler = scheduler

    @Provides
    @Singleton
    fun provideNotificationPermissionGate(
        @ApplicationContext context: Context,
    ): NotificationPermissionGate = NotificationPermissionGate(context)

    @Provides
    @Singleton
    fun provideStoryNotificationBuilder(
        @ApplicationContext context: Context,
    ): StoryNotificationBuilder = StoryNotificationBuilder(context)

    @Provides
    @Singleton
    fun provideAndroidChapterNotifier(
        @ApplicationContext context: Context,
        gate: NotificationPermissionGate,
        builder: StoryNotificationBuilder,
    ): AndroidChapterNotifier = NotificationDispatcher(context, gate, builder)

    @Provides
    @Singleton
    fun provideNotificationDeepLinkFactory(
        @ApplicationContext context: Context,
        targets: ChapterNotificationTargetSource,
    ): NotificationDeepLinkFactory = NotificationDeepLinkFactory(context, targets)

    @Provides
    @Singleton
    fun provideNotificationIntentParser(
        targets: ChapterNotificationTargetSource,
    ): NotificationIntentParser = DefaultNotificationIntentParser(targets)

    @Provides
    @Singleton
    fun provideWorkManagerCanonicalEngineWorkScheduler(
        @ApplicationContext context: Context,
    ): WorkManagerCanonicalEngineWorkScheduler = WorkManagerCanonicalEngineWorkScheduler(context)

    @Provides
    fun provideCanonicalEngineWorkScheduler(
        scheduler: WorkManagerCanonicalEngineWorkScheduler,
    ): CanonicalEngineWorkScheduler = scheduler

    @Provides
    @Singleton
    fun providePostMergeDerivedWorkDispatcher(
        @ApplicationContext context: Context,
        chapters: ChapterReaggregator,
    ): PostMergeDerivedWorkDispatcher = WorkManagerPostMergeDerivedWorkDispatcher(context, chapters)
}
