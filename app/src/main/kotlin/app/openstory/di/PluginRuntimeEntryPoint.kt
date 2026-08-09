package app.openstory.di

import app.openstory.plugins.runtime.PluginRuntime
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PluginRuntimeEntryPoint {
    fun runtime(): PluginRuntime
}
