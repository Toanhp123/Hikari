package app.openstory.plugins.runtime.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import app.openstory.common.id.PluginId
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AndroidKeystorePluginSessionStore(
    context: Context,
    private val json: Json = Json,
) : PluginSessionStore {
    private val root = File(context.noBackupFilesDir, "plugin-sessions").apply { mkdirs() }

    override suspend fun readAll(pluginId: PluginId): List<PluginSessionRecord> = withContext(Dispatchers.IO) {
        val file = file(pluginId)
        if (!file.exists()) return@withContext emptyList()
        try {
            json.decodeFromString<SessionEnvelope>(file.readText()).records.map { stored ->
                stored.toRecord(pluginId, decrypt(stored, pluginId))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            file.delete()
            emptyList()
        }
    }

    override suspend fun replaceAll(pluginId: PluginId, records: List<PluginSessionRecord>) {
        withContext(Dispatchers.IO) {
            require(records.all { it.pluginId == pluginId })
            root.mkdirs()
            val target = file(pluginId)
            val temporary = File(root, "${pluginId.value}.tmp")
            val envelope = SessionEnvelope(records = records.map { record -> encrypt(record) })
            try {
                FileOutputStream(temporary).use { output ->
                    output.write(json.encodeToString(envelope).encodeToByteArray())
                    output.fd.sync()
                }
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } finally {
                temporary.delete()
            }
        }
    }

    override suspend fun clear(pluginId: PluginId) {
        withContext(Dispatchers.IO) {
            file(pluginId).delete()
            File(root, "${pluginId.value}.tmp").delete()
        }
    }

    private fun encrypt(record: PluginSessionRecord): StoredSessionRecord {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val metadata = StoredSessionRecord.from(record, nonce = "", ciphertext = "")
        cipher.updateAAD(metadata.aad(record.pluginId))
        return metadata.copy(
            nonce = android.util.Base64.encodeToString(cipher.iv, android.util.Base64.NO_WRAP),
            ciphertext = android.util.Base64.encodeToString(
                cipher.doFinal(record.cookieValue.raw.encodeToByteArray()),
                android.util.Base64.NO_WRAP,
            ),
        )
    }

    private fun decrypt(record: StoredSessionRecord, pluginId: PluginId): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(128, android.util.Base64.decode(record.nonce, android.util.Base64.NO_WRAP)),
        )
        cipher.updateAAD(record.aad(pluginId))
        return cipher.doFinal(
            android.util.Base64.decode(record.ciphertext, android.util.Base64.NO_WRAP),
        ).decodeToString()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private fun file(pluginId: PluginId) = File(root, "${pluginId.value}.json")

    private companion object {
        const val KEY_ALIAS = "openstory.plugin.sessions.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

@Serializable
private data class SessionEnvelope(val version: Int = 1, val records: List<StoredSessionRecord>)

@Serializable
private data class StoredSessionRecord(
    val targetHost: String,
    val targetPathPrefix: String,
    val cookieName: String,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val authenticationPolicyFingerprint: String,
    val nonce: String,
    val ciphertext: String,
) {
    fun aad(pluginId: PluginId): ByteArray = listOf(
        "1",
        pluginId.value,
        targetHost,
        targetPathPrefix,
        cookieName,
        authenticationPolicyFingerprint,
    ).joinToString("\u0000").encodeToByteArray()

    fun toRecord(pluginId: PluginId, secret: String) = PluginSessionRecord(
        pluginId = pluginId,
        targetHost = targetHost,
        targetPathPrefix = targetPathPrefix,
        cookieName = cookieName,
        cookieValue = SecretCookieValue.of(secret),
        createdAtEpochMillis = createdAtEpochMillis,
        expiresAtEpochMillis = expiresAtEpochMillis,
        authenticationPolicyFingerprint = authenticationPolicyFingerprint,
    )

    companion object {
        fun from(record: PluginSessionRecord, nonce: String, ciphertext: String) = StoredSessionRecord(
            targetHost = record.targetHost,
            targetPathPrefix = record.targetPathPrefix,
            cookieName = record.cookieName,
            createdAtEpochMillis = record.createdAtEpochMillis,
            expiresAtEpochMillis = record.expiresAtEpochMillis,
            authenticationPolicyFingerprint = record.authenticationPolicyFingerprint,
            nonce = nonce,
            ciphertext = ciphertext,
        )
    }
}
