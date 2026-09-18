package app.universalmedia.data

import android.content.Context
import android.database.sqlite.SQLiteDatabaseCorruptException
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

@Database(
    entities = [
        MediaRow::class, MediaUnitRow::class, TargetRow::class, SourceRow::class,
        RootRow::class, BindingRow::class, AssetRow::class, LocatorRow::class, LibraryRow::class,
        ProgressRow::class, AnchorRow::class, RunRow::class, ScopeRow::class, SeenRow::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class UniversalMediaDatabase : RoomDatabase() {
    internal abstract fun catalog(): CatalogDao

    companion object {
        fun open(context: Context, name: String = "universal-media.db"): UniversalMediaDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                UniversalMediaDatabase::class.java,
                name,
            )
                .openHelperFactory(PreservingOpenHelperFactory)
                .addCallback(TargetIntegrity)
                .build()
    }
}

// The platform's default corruption handler deletes database files. Canonical user state must
// survive for recovery.
private object PreservingOpenHelperFactory : SupportSQLiteOpenHelper.Factory {
    override fun create(
        configuration: SupportSQLiteOpenHelper.Configuration,
    ): SupportSQLiteOpenHelper {
        val delegate = configuration.callback
        val callback = object : SupportSQLiteOpenHelper.Callback(delegate.version) {
            override fun onConfigure(db: SupportSQLiteDatabase) = delegate.onConfigure(db)
            override fun onCreate(db: SupportSQLiteDatabase) = delegate.onCreate(db)
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                delegate.onUpgrade(db, oldVersion, newVersion)
            override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                delegate.onDowngrade(db, oldVersion, newVersion)
            override fun onOpen(db: SupportSQLiteDatabase) = delegate.onOpen(db)
            override fun onCorruption(db: SupportSQLiteDatabase): Unit =
                throw SQLiteDatabaseCorruptException(
                    "Canonical database requires explicit recovery; files preserved",
                )
        }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
                .name(configuration.name)
                .callback(callback)
                .noBackupDirectory(configuration.useNoBackupDirectory)
                .build(),
        )
    }
}

// Room cannot express this cross-column CHECK. Keep both triggers with every future migration.
private object TargetIntegrity : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        for (operation in listOf("INSERT", "UPDATE")) {
            db.execSQL(
                """
                CREATE TRIGGER target_referent_${operation.lowercase()}
                BEFORE $operation ON consumption_target
                WHEN NOT (
                  (NEW.target_kind = 'MEDIA' AND NEW.media_id IS NOT NULL AND NEW.unit_id IS NULL)
                  OR (NEW.target_kind = 'UNIT' AND NEW.unit_id IS NOT NULL AND NEW.media_id IS NULL)
                )
                BEGIN SELECT RAISE(ABORT, 'Invalid consumption target'); END
                """.trimIndent(),
            )
        }
    }
}
