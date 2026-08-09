package app.openstory.di

import android.content.Context
import app.openstory.common.dispatchers.AppDispatchers
import app.openstory.plugin.host.PluginHost
import app.openstory.plugin.host.js.AndroidxJsIsolateExecutor
import app.openstory.plugin.host.js.JsIsolateExecutor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PluginHostModule {
    @Provides
    @Singleton
    fun provideJavaScriptExecutor(
        @ApplicationContext context: Context,
    ): JsIsolateExecutor = AndroidxJsIsolateExecutor(context)

    @Provides
    @Singleton
    fun providePluginHost(
        @ApplicationContext context: Context,
        dispatchers: AppDispatchers,
        javaScriptExecutor: JsIsolateExecutor,
    ): PluginHost = BundledDefaultCatalogHost(
        context = context,
        ioDispatcher = dispatchers.io,
        javaScriptExecutor = javaScriptExecutor,
    )
}
