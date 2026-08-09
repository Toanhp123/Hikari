package app.openstory.di

import app.openstory.plugin.host.js.JsIsolateExecutor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface JavaScriptExecutorEntryPoint {
    fun javaScriptExecutor(): JsIsolateExecutor
}
