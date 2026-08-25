package app.openstory.plugins.runtime.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openstory.common.id.PluginId
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidKeystorePluginSessionStoreTest {
    @Test
    fun roundTripUsesNoBackupStorageWithoutPlaintextSecret() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = AndroidKeystorePluginSessionStore(context)
        val pluginId = PluginId("org.example.secure")
        store.clear(pluginId)
        val record = PluginSessionRecord(
            pluginId = pluginId,
            targetHost = "api.example.com",
            targetPathPrefix = "/v1",
            cookieName = "session",
            cookieValue = SecretCookieValue.of("never-write-this-secret"),
            createdAtEpochMillis = 1,
            expiresAtEpochMillis = 2,
            authenticationPolicyFingerprint = "a".repeat(64),
        )

        store.replaceAll(pluginId, listOf(record))

        assertEquals("never-write-this-secret", store.readAll(pluginId).single().cookieValue.raw)
        val sessionFile = File(context.noBackupFilesDir, "plugin-sessions/${pluginId.value}.json")
        assertFalse("never-write-this-secret" in sessionFile.readText())
        store.clear(pluginId)
    }
}
