package app.openstory.catalog.storage

import android.content.Context
import androidx.room.Room
import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import app.openstory.catalog.domain.failure.CatalogStorageOperation
import java.util.concurrent.CancellationException

class CatalogStorageFactory(
    context: Context,
    private val databaseName: String = CatalogDatabase.NAME,
) {
    private val applicationContext = context.applicationContext

    fun open(): RoomCatalogStore {
        var database: CatalogDatabase? = null
        return runCatching {
            val openedDatabase = Room.databaseBuilder(
                applicationContext,
                CatalogDatabase::class.java,
                databaseName,
            ).build()
            database = openedDatabase
            openedDatabase.openHelper.writableDatabase
            RoomCatalogStore(openedDatabase)
        }.getOrElse { error ->
            runCatching { database?.close() }
                .exceptionOrNull()
                ?.let(error::addSuppressed)
            throw error.toStorageFailure(CatalogStorageOperation.OPEN)
        }
    }
}

internal fun Throwable.toStorageFailure(operation: CatalogStorageOperation): Throwable = when (this) {
    is CancellationException, is CatalogFailureException -> this
    else -> CatalogFailureException(CatalogFailure.Storage(operation), this)
}
