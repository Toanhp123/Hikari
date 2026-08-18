package app.openstory.di

import app.openstory.plugins.runtime.PluginRuntime
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// Live integration tests read the production SingletonComponent through EntryPointAccessors.
// Keep this declaration in src/main so the generated production component implements the entry point.
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PluginRuntimeEntryPoint {
    fun runtime(): PluginRuntime
}
