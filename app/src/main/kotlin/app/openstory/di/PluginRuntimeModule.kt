package app.openstory.di

import android.content.Context
import app.openstory.BuildConfig
import app.openstory.catalog.source.CatalogSourceRegistry
import app.openstory.catalog.source.PluginCatalogSourceRegistry
import app.openstory.plugins.runtime.DefaultPluginRuntime
import app.openstory.plugins.runtime.PluginRuntime
import app.openstory.plugins.runtime.capabilities.CapabilityBroker
import app.openstory.plugins.runtime.capabilities.html.HtmlCapability
import app.openstory.plugins.runtime.capabilities.http.PluginHttpCapability
import app.openstory.plugins.runtime.capabilities.log.SafePluginLogger
import app.openstory.plugins.runtime.execution.AndroidxJavaScriptEngine
import app.openstory.plugins.runtime.execution.PluginOperationRunner
import app.openstory.plugins.runtime.install.AndroidBundledPluginSource
import app.openstory.plugins.runtime.install.BundledPluginProvisioner
import app.openstory.plugins.runtime.install.PackageVerifier
import app.openstory.plugins.runtime.install.PluginInstaller
import app.openstory.plugins.runtime.install.TransactionalPluginPackageStorage
import app.openstory.plugins.runtime.persistence.PluginDiagnosticsSink
import app.openstory.plugins.runtime.persistence.PluginStateStore
import app.openstory.plugins.runtime.update.PluginUpdateService
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.plugins.RoomPluginDiagnosticsSink
import app.openstory.storage.room.plugins.RoomPluginStateStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.nio.file.Paths
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object PluginRuntimeModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): OpenStoryDatabase = OpenStoryDatabase.open(context)

    @Provides
    @Singleton
    fun providePluginStateStore(database: OpenStoryDatabase): PluginStateStore = RoomPluginStateStore(database)

    @Provides
    @Singleton
    fun providePluginDiagnosticsSink(database: OpenStoryDatabase): PluginDiagnosticsSink =
        RoomPluginDiagnosticsSink(database)

    @Provides
    @Singleton
    fun providePluginRuntime(
        @ApplicationContext context: Context,
        state: PluginStateStore,
        diagnostics: PluginDiagnosticsSink,
    ): PluginRuntime {
        val json = Json
        val storage = TransactionalPluginPackageStorage(
            Paths.get(context.filesDir.absolutePath, "plugin-runtime"),
        )
        val installer = PluginInstaller(PackageVerifier(), storage, state)
        val updates = PluginUpdateService(installer, state)
        val bundled = BundledPluginProvisioner(
            source = AndroidBundledPluginSource(context, listOf(MyAnimeListBundledPlugin.descriptor)),
            installer = installer,
            updates = updates,
            state = state,
        )
        val capabilities = CapabilityBroker(
            http = PluginHttpCapability(
                client = OkHttpClient(),
                credentials = MyAnimeListManagedCredentials(BuildConfig.MYANIMELIST_CLIENT_ID),
            ),
            html = HtmlCapability(),
            logger = SafePluginLogger(diagnostics),
            json = json,
        )
        val runner = PluginOperationRunner(
            engine = AndroidxJavaScriptEngine(context),
            capabilities = capabilities,
            diagnostics = diagnostics,
            json = json,
        )
        return DefaultPluginRuntime(state, storage, runner, bundled, json)
    }

    @Provides
    @Singleton
    fun provideCatalogSourceRegistry(runtime: PluginRuntime): CatalogSourceRegistry =
        PluginCatalogSourceRegistry(runtime, Json)
}
