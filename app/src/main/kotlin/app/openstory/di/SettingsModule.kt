package app.openstory.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import app.openstory.common.dispatchers.AppDispatchers
import app.openstory.reader.preferences.ReaderPreferencesPort
import app.openstory.settings.AppSettingsRepository
import app.openstory.settings.DataStoreAppSettingsRepository
import app.openstory.settings.SharedPreferencesSettingsMigration
import app.openstory.settings.RedactedSettingsDiagnosticSink
import app.openstory.settings.SettingsCorruptionHandler
import app.openstory.settings.SettingsDefaults
import app.openstory.settings.SettingsDiagnosticSink
import app.openstory.settings.SettingsReaderPreferencesAdapter
import app.openstory.settings.RuntimePluginSessionControlAdapter
import app.openstory.settings.AndroidNotificationControlAdapter
import app.openstory.settings.WorkManagerBackgroundWorkStatusAdapter
import app.openstory.settings.AppStorageSummaryAdapter
import app.openstory.plugins.runtime.auth.InstalledAuthenticationPolicySource
import app.openstory.plugins.runtime.auth.PluginSessionService
import app.openstory.chapters.notification.NotificationEventRepository
import app.openstory.downloads.DownloadRepository
import app.openstory.downloads.cache.AutomaticCacheBudgetCoordinator
import app.openstory.notifications.NotificationPermissionGate
import app.openstory.settings.session.PluginSessionControlPort
import app.openstory.settings.notification.NotificationControlPort
import app.openstory.settings.background.BackgroundWorkStatusPort
import app.openstory.settings.storage.StorageSummaryPort
import app.openstory.settings.background.BackgroundPolicyCoordinator
import app.openstory.settings.background.BackgroundWorkSchedulePort
import app.openstory.work.WorkManagerBackgroundWorkScheduleAdapter
import app.openstory.work.WorkManagerPeriodicSyncScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.Locale
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {
    @Provides
    @Singleton
    fun providePluginSessionControlPort(
        @ApplicationContext context: Context,
        sessions: PluginSessionService,
        policies: InstalledAuthenticationPolicySource,
    ): PluginSessionControlPort = RuntimePluginSessionControlAdapter(context, sessions, policies)

    @Provides
    @Singleton
    fun provideNotificationControlPort(
        gate: NotificationPermissionGate,
        events: NotificationEventRepository,
    ): NotificationControlPort = AndroidNotificationControlAdapter(gate, events)

    @Provides
    @Singleton
    fun provideBackgroundWorkStatusPort(
        @ApplicationContext context: Context,
    ): BackgroundWorkStatusPort = WorkManagerBackgroundWorkStatusAdapter(context)

    @Provides
    @Singleton
    fun provideStorageSummaryPort(
        automaticCacheBudgetCoordinator: AutomaticCacheBudgetCoordinator,
        downloads: DownloadRepository,
        settings: AppSettingsRepository,
    ): StorageSummaryPort = AppStorageSummaryAdapter(automaticCacheBudgetCoordinator, downloads, settings)

    @Provides
    @Singleton
    fun provideSettingsDefaults(): SettingsDefaults {
        val deviceLanguage = Locale.getDefault().language.lowercase().trim().ifBlank { "en" }
        return SettingsDefaults(
            contentLanguageOrder = listOf(deviceLanguage, "en").distinct(),
        )
    }

    @Provides
    @Singleton
    fun provideSettingsDiagnostics(): SettingsDiagnosticSink = RedactedSettingsDiagnosticSink()

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
        defaults: SettingsDefaults,
        diagnostics: SettingsDiagnosticSink,
        dispatchers: AppDispatchers,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = SettingsCorruptionHandler(defaults, diagnostics).dataStoreHandler,
        migrations = listOf(
            SharedPreferencesSettingsMigration(
                legacy = context.getSharedPreferences(
                    SharedPreferencesSettingsMigration.LEGACY_PREFERENCES_NAME,
                    Context.MODE_PRIVATE,
                ),
                defaults = defaults,
            ),
        ),
        scope = CoroutineScope(SupervisorJob() + dispatchers.io),
        produceFile = { context.preferencesDataStoreFile("app_settings") },
    )

    @Provides
    @Singleton
    fun provideAppSettingsRepository(
        dataStore: DataStore<Preferences>,
        defaults: SettingsDefaults,
        diagnostics: SettingsDiagnosticSink,
    ): AppSettingsRepository = DataStoreAppSettingsRepository(dataStore, defaults, diagnostics)

    @Provides
    @Singleton
    fun provideReaderPreferencesPort(repository: AppSettingsRepository): ReaderPreferencesPort =
        SettingsReaderPreferencesAdapter(repository)

    @Provides
    @Singleton
    fun providePeriodicSyncScheduler(
        @ApplicationContext context: Context,
    ): WorkManagerPeriodicSyncScheduler = WorkManagerPeriodicSyncScheduler(context)

    @Provides
    @Singleton
    fun provideBackgroundWorkSchedulePort(
        scheduler: WorkManagerPeriodicSyncScheduler,
    ): BackgroundWorkSchedulePort = WorkManagerBackgroundWorkScheduleAdapter(scheduler)

    @Provides
    @Singleton
    fun provideBackgroundPolicyCoordinator(
        repository: AppSettingsRepository,
        scheduler: BackgroundWorkSchedulePort,
        dispatchers: AppDispatchers,
    ): BackgroundPolicyCoordinator = BackgroundPolicyCoordinator(repository, scheduler, dispatchers)
}
