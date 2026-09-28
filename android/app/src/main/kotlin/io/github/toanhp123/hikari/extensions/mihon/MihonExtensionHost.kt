package io.github.toanhp123.hikari.extensions.mihon

import android.app.Application
import android.content.Context
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory

internal object MihonExtensionHost {
    @Volatile
    private var initialized = false

    @OptIn(ExperimentalSerializationApi::class)
    fun initialize(application: Application) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return

            val network = NetworkHelper(application)
            val json = Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }

            Injekt.addSingletonFactory<Application> { application }
            Injekt.addSingletonFactory<Context> { application }
            Injekt.addSingletonFactory<NetworkHelper> { network }
            Injekt.addSingletonFactory<Json> { json }
            Injekt.addSingletonFactory<ProtoBuf> { ProtoBuf }
            Injekt.addSingletonFactory<JavaScriptEngine> { JavaScriptEngine(application) }

            initialized = true
        }
    }
}
