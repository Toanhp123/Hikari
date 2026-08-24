package app.openstory.di

import android.content.Context
import app.openstory.catalog.orchestration.CanonicalEngineWorkScheduler
import app.openstory.catalog.orchestration.PostMergeDerivedWorkDispatcher
import app.openstory.chapters.maintenance.ChapterReaggregator
import app.openstory.work.WorkManagerCanonicalEngineWorkScheduler
import app.openstory.work.WorkManagerPostMergeDerivedWorkDispatcher
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
