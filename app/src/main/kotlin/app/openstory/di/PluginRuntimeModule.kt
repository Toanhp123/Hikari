package app.openstory.di

import android.content.Context
import app.openstory.BuildConfig
import app.openstory.plugins.runtime.DefaultPluginRuntime
import app.openstory.plugins.runtime.PluginRuntime
import app.openstory.plugins.runtime.capabilities.CapabilityBroker
import app.openstory.plugins.runtime.capabilities.html.HtmlCapability
import app.openstory.plugins.runtime.capabilities.http.CompositeManagedCredentialProvider
import app.openstory.plugins.runtime.capabilities.http.ManagedCredentialProvider
import app.openstory.plugins.runtime.capabilities.http.PluginHttpCapability
import app.openstory.plugins.runtime.auth.AndroidKeystorePluginSessionStore
import app.openstory.plugins.runtime.auth.DefaultPluginSessionService
import app.openstory.plugins.runtime.auth.InstalledAuthenticationPolicySource
import app.openstory.plugins.runtime.auth.InstalledPackageAuthenticationPolicySource
import app.openstory.plugins.runtime.auth.PluginSessionManagedCredentialProvider
import app.openstory.plugins.runtime.auth.PluginSessionService
import app.openstory.plugins.runtime.auth.PluginSessionStore
import app.openstory.plugins.runtime.capabilities.log.SafePluginLogger
import app.openstory.plugins.runtime.execution.AndroidxJavaScriptEngine
import app.openstory.plugins.runtime.execution.JavaScriptEngine
import app.openstory.plugins.runtime.execution.PluginOperationRunner
import app.openstory.plugins.runtime.install.AndroidBundledPluginSource
import app.openstory.plugins.runtime.install.BundledPluginProvisioner
import app.openstory.plugins.runtime.install.PackageVerifier
import app.openstory.plugins.runtime.install.PluginInstaller
import app.openstory.plugins.runtime.install.PluginPackageStorage
import app.openstory.plugins.runtime.install.TransactionalPluginPackageStorage
import app.openstory.plugins.runtime.persistence.PluginDiagnosticsSink
import app.openstory.plugins.runtime.persistence.PluginStateStore
import app.openstory.plugins.runtime.update.PluginUpdateService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.nio.file.Path
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object PluginRuntimeModule {
    @Provides
    @Singleton
    fun provideJson(): Json = Json

    @Provides
    @Singleton
    fun providePluginPackageRoot(@ApplicationContext context: Context): Path =
        context.filesDir.toPath().resolve("plugin-runtime")

    @Provides
    @Singleton
    fun providePluginPackageStorage(root: Path): PluginPackageStorage =
        TransactionalPluginPackageStorage(root)

    @Provides
    @Singleton
    fun provideJavaScriptEngine(@ApplicationContext context: Context): JavaScriptEngine =
        AndroidxJavaScriptEngine(context)

    @Provides
    @Singleton
    fun providePluginSessionStore(
        @ApplicationContext context: Context,
        json: Json,
    ): PluginSessionStore = AndroidKeystorePluginSessionStore(context, json)

    @Provides
    @Singleton
    fun provideInstalledAuthenticationPolicySource(
        state: PluginStateStore,
        storage: PluginPackageStorage,
        json: Json,
    ): InstalledAuthenticationPolicySource = InstalledPackageAuthenticationPolicySource(state, storage, json)

    @Provides
    @Singleton
    fun providePluginSessionService(
        store: PluginSessionStore,
        policies: InstalledAuthenticationPolicySource,
    ): PluginSessionService = DefaultPluginSessionService(store, policies)

    @Provides
    @Singleton
    fun provideManagedCredentialProvider(
        sessions: PluginSessionService,
    ): ManagedCredentialProvider =
        CompositeManagedCredentialProvider(
            listOf(
                MyAnimeListManagedCredentials(BuildConfig.MYANIMELIST_CLIENT_ID),
                PluginSessionManagedCredentialProvider(sessions),
            ),
        )

    @Provides
    @Singleton
    fun providePluginRuntime(
        @ApplicationContext context: Context,
        state: PluginStateStore,
        diagnostics: PluginDiagnosticsSink,
        storage: PluginPackageStorage,
        engine: JavaScriptEngine,
        credentials: ManagedCredentialProvider,
        sessions: PluginSessionService,
        json: Json,
    ): PluginRuntime {
        val installer = PluginInstaller(
            PackageVerifier(),
            storage,
            state,
            onInstalled = { sessions.invalidateChangedPolicies() },
        )
        val updates = PluginUpdateService(installer, state)
        val bundled = BundledPluginProvisioner(
            source = AndroidBundledPluginSource(context, BundledPlugins.descriptors),
            installer = installer,
            updates = updates,
            state = state,
        )
        val capabilities = CapabilityBroker(
            http = PluginHttpCapability(
                client = OkHttpClient(),
                credentials = credentials,
            ),
            html = HtmlCapability(),
            logger = SafePluginLogger(diagnostics),
            sessions = sessions,
            json = json,
        )
        val runner = PluginOperationRunner(
            engine = engine,
            capabilities = capabilities,
            diagnostics = diagnostics,
            json = json,
        )
        return DefaultPluginRuntime(state, storage, runner, bundled, json)
    }
}
