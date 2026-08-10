package app.openstory.di

import app.openstory.library.LibraryRepository
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.library.RoomLibraryRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LibraryModule {
    @Provides
    @Singleton
    fun provideLibraryRepository(database: OpenStoryDatabase): LibraryRepository =
        RoomLibraryRepository(database)
}
