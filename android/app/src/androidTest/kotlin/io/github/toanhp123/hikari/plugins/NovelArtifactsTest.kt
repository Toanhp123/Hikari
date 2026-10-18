package io.github.toanhp123.hikari.plugins

import android.test.InstrumentationTestCase
import java.io.File
import org.json.JSONObject

/** Inert bytes only. Never loads or evaluates downloaded code. */
@Suppress("DEPRECATION")
class NovelArtifactsTest : InstrumentationTestCase() {
    private lateinit var directory: File
    override fun setUp() {
        super.setUp()
        check(instrumentation.targetContext.packageName.endsWith(".localmediatest"))
        directory = File(instrumentation.targetContext.cacheDir, "novel-contract-${System.nanoTime()}")
    }
    override fun tearDown() { directory.deleteRecursively(); super.tearDown() }
    // Custody regressions inject inspection only here; production default rejects.
    private fun custody(directory: File, beforePublicationSync: () -> Unit = {},
        beforePointerSync: () -> Unit = {},
        finishWrite: (android.util.AtomicFile, java.io.FileOutputStream) -> Unit = { file, output -> file.finishWrite(output) },
        beforeCommit: () -> Unit = {}) = NovelArtifacts(directory, beforePublicationSync, beforePointerSync,
            finishWrite, inspect = { _, _ -> }, beforeCommit = beforeCommit)
    private fun metadata(version: String = "1.0") = mapOf<String, Any>(
        "id" to "inert", "name" to "Inert", "version" to version,
        "repository" to "https://example.com/index.json", "site" to "https://example.com/novels",
        "origins" to listOf("https://example.com"))
    private fun stage(store: NovelArtifacts, version: String = "1.0", bytes: ByteArray = "module.exports = {};".toByteArray()) =
        store.stage(metadata(version), {}, { it.write(bytes) })
    private fun revision(stage: Map<String, Any>) = stage["artifact"] as Map<String, Any>
    private fun rejects(block: () -> Unit) {
        try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {}
    }
    fun testLegacyAdmissionAndRejectedMutationKeepPointers() {
        val store = custody(directory)
        val first = stage(store)
        store.install(first["token"] as String, revision(first))
        val second = stage(store, "2.0", "second".toByteArray())
        store.install(second["token"] as String, revision(second))
        val pointer = File(directory, "state.json")
        val legacy = JSONObject(pointer.readText()).put("schema", 1)
        val entries = legacy.getJSONArray("entries")
        repeat(entries.length()) { entries.getJSONObject(it).remove("currentAdmission"); entries.getJSONObject(it).remove("previousAdmission") }
        pointer.writeText(legacy.toString())
        var evaluations = 0
        val rejecting = NovelArtifacts(directory, inspect = { _, _ -> evaluations++; throw io.github.toanhp123.hikari.extensions.lnreader.NovelAdmission.Rejected() })
        assertEquals(0, evaluations)
        assertEquals("unknown", rejecting.inventory().single()["admission"])
        val before = pointer.readText()
        rejects { rejecting.admit(revision(second) + ("name" to "changed")) }
        assertEquals(0, evaluations)
        rejects { rejecting.admit(revision(second)) }
        rejects { rejecting.rollback(revision(second), revision(first)) }
        val update = stage(rejecting, "3.0", "third".toByteArray())
        rejects { rejecting.install(update["token"] as String, revision(update)) }
        assertEquals(3, evaluations)
        assertEquals(before, pointer.readText())
        val admitting = custody(directory)
        admitting.admit(revision(second))
        val row = custody(directory).inventory().single()
        assertEquals("admitted", row["admission"])
        assertEquals("unknown", row["previousAdmission"])
        assertEquals(2, JSONObject(pointer.readText()).getInt("schema"))
        admitting.rollback(revision(second), revision(first))
        assertEquals("admitted", admitting.inventory().single()["admission"])
        val root = JSONObject(pointer.readText())
        root.getJSONArray("entries").getJSONObject(0).getJSONObject("currentAdmission")
            .getJSONObject("artifact").put("name", "forged")
        pointer.writeText(root.toString())
        rejects { custody(directory) }
    }
    fun testConsentUpdateRollbackStaleTokenAndUninstall() {
        val store = custody(directory)
        val first = stage(store)
        val identity = revision(first)
        rejects { store.install(first["token"] as String, identity + ("name" to "Changed")) }
        assertTrue(store.inventory().isEmpty())
        store.install(first["token"] as String, identity)
        rejects { store.install(first["token"] as String, identity) }
        val stale = stage(store, "2.0")
        val update = stage(store, "3.0")
        store.install(update["token"] as String, revision(update))
        rejects { store.install(stale["token"] as String, revision(stale)) }
        val row = store.inventory().single()
        assertEquals("admitted", row["admission"])
        assertEquals(true, row["restartRequired"])
        rejects { store.rollback(revision(update), identity + ("digest" to "b".repeat(64))) }
        store.rollback(revision(update), identity)
        assertEquals(identity, store.inventory().single()["current"])
        val downgrade = stage(store, "0.9")
        rejects { store.install(downgrade["token"] as String, revision(downgrade)) }
        assertEquals(identity, custody(directory).inventory().single()["current"])
        val userData = File(directory.parentFile, "preserve-${System.nanoTime()}").apply { writeText("user content") }
        try {
            store.uninstall(identity)
            assertTrue(store.inventory().isEmpty())
            assertEquals("user content", userData.readText())
        } finally { userData.delete() }
    }
    fun testStrictBytesLimitsTamperReopenAndNoOrphanPromotion() {
        val store = custody(directory)
        rejects { stage(store, bytes = byteArrayOf(0xc3.toByte(), 0x28)) }
        rejects { stage(store, bytes = byteArrayOf(0)) }
        rejects { stage(store, bytes = byteArrayOf()) }
        rejects { stage(store, bytes = ByteArray(1024 * 1024 + 1) { 65 }) }
        val stages = (1..4).map { stage(store) }
        rejects { stage(store) }
        stages.forEach { store.discard(it["token"] as String) }
        val tampered = stage(store)
        File(directory, "staging/${tampered["token"]}.js").writeText("changed")
        rejects { store.install(tampered["token"] as String, revision(tampered)) }
        val good = stage(store)
        store.install(good["token"] as String, revision(good))
        val digest = revision(good)["digest"] as String
        File(directory, "artifacts/$digest.js").writeText("corrupt")
        assertEquals("corrupt", custody(directory).inventory().single()["status"])
        File(directory, "artifacts/$digest.js").delete()
        assertEquals("missing", custody(directory).inventory().single()["status"])
        File(directory, "artifacts/${"b".repeat(64)}.js").writeText("orphan")
        assertEquals("missing", custody(directory).inventory().single()["status"])
    }
    fun testCancellationDiskBudgetAndCorruptPreviousNeverPublish() {
        val store = custody(directory)
        rejects { store.stage(metadata(), { error("cancelled before publication") }, { it.write(65) }) }
        assertTrue(store.inventory().isEmpty())
        assertTrue(File(directory, "staging").listFiles().orEmpty().isEmpty())
        val first = stage(store)
        store.install(first["token"] as String, revision(first))
        val second = stage(store, "2.0", "second inert bytes".toByteArray())
        store.install(second["token"] as String, revision(second))
        val previousFile = File(directory, "artifacts/${revision(first)["digest"]}.js")
        previousFile.writeText("corrupted prior bytes")
        rejects { store.rollback(revision(second), revision(first)) }
        assertEquals(revision(second), store.inventory().single()["current"])
        // Synthetic app-private disk pressure, no network or real package actions.
        val pressure = File(directory, "staging/pressure.js")
        java.io.RandomAccessFile(pressure, "rw").use { it.setLength(64L * 1024 * 1024) }
        rejects { stage(store, "3.0") }
        pressure.delete()
        val absentStage = stage(store, "3.0")
        store.uninstall(revision(second))
        rejects { store.install(absentStage["token"] as String, revision(absentStage)) }
        assertTrue(store.inventory().isEmpty())
    }
    fun testPublicationSyncFailureKeepsDurableStateAndDoesNotPromoteOrphan() {
        val store = custody(directory)
        val old = stage(store)
        store.install(old["token"] as String, revision(old))
        val before = File(directory, "state.json").readText()
        val broken = custody(directory, beforePublicationSync = { error("injected directory sync failure") })
        val next = stage(broken, "2.0", "distinct inert update".toByteArray())
        rejects { broken.install(next["token"] as String, revision(next)) }
        assertEquals(before, File(directory, "state.json").readText()) // Includes unchanged generation.
        assertEquals(revision(old), broken.inventory().single()["current"])
        assertFalse(File(directory, "staging/${next["token"]}.js").exists())
        assertTrue(File(directory, "artifacts/${revision(next)["digest"]}.js").exists())
        rejects { broken.install(next["token"] as String, revision(next)) } // Moved stage is not retryable.
        broken.discard(next["token"] as String)
        val reopened = custody(directory)
        assertEquals(revision(old), reopened.inventory().single()["current"])
        assertFalse(File(directory, "artifacts/${revision(next)["digest"]}.js").exists())
        assertTrue(File(directory, "artifacts/${revision(old)["digest"]}.js").exists())
    }
    fun testPostcommitSyncFailureReportsCommittedStateAndPreservesOldFiles() {
        val store = custody(directory)
        val old = stage(store)
        store.install(old["token"] as String, revision(old))
        val broken = custody(directory, beforePointerSync = { error("injected pointer directory sync failure") })
        val update = stage(broken, "2.0", "new inert bytes".toByteArray())
        try {
            broken.install(update["token"] as String, revision(update))
            fail("Expected committed durability failure")
        } catch (failure: NovelArtifacts.PointerSyncFailure) {
            assertEquals(revision(update), failure.evidence.single()["current"])
        }
        assertEquals(revision(update), broken.inventory().single()["current"])
        assertTrue(File(directory, "artifacts/${revision(old)["digest"]}.js").exists())
        assertTrue(File(directory, "artifacts/${revision(update)["digest"]}.js").exists())
        rejects { broken.uninstall(revision(update)) }
        val reopened = custody(directory)
        assertEquals(revision(update), reopened.inventory().single()["current"])
        assertEquals(revision(old), reopened.inventory().single()["previous"])
    }
    fun testSilentFinishFailureIsUnknownAndRetainsEveryPossiblyReferencedArtifact() {
        val store = custody(directory)
        val first = stage(store, "1.0", "first inert bytes".toByteArray())
        store.install(first["token"] as String, revision(first))
        val second = stage(store, "2.0", "second inert bytes".toByteArray())
        store.install(second["token"] as String, revision(second))
        val before = File(directory, "state.json").readText()
        var pointerSyncAttempted = false
        val broken = custody(directory, beforePointerSync = { pointerSyncAttempted = true },
            finishWrite = { _, output -> output.close() })
        val third = stage(broken, "3.0", "third inert bytes".toByteArray())
        try {
            broken.install(third["token"] as String, revision(third))
            fail("Expected unknown publication")
        } catch (failure: NovelArtifacts.PublicationUnknown) {
            assertEquals("unknown", failure.evidence["publication"])
            val confirmed = failure.evidence["lastConfirmedInventory"] as List<Map<String, Any?>>
            val attempted = failure.evidence["attemptedPointers"] as List<Map<String, Any?>>
            assertEquals(revision(second), confirmed.single()["current"])
            assertEquals(revision(first), confirmed.single()["previous"])
            assertEquals(revision(third), attempted.single()["current"])
            assertEquals(revision(second), attempted.single()["previous"])
            assertEquals(revision(second), broken.inventory().single()["current"])
        }
        assertFalse(pointerSyncAttempted)
        assertEquals(before, File(directory, "state.json").readText()) // Old durable generation remains.
        listOf(first, second, third).forEach {
            assertTrue(File(directory, "artifacts/${revision(it)["digest"]}.js").exists())
        }
        rejects { broken.uninstall(revision(second)) }
        rejects { stage(broken, "4.0") }
        val reopened = custody(directory)
        assertEquals(revision(second), reopened.inventory().single()["current"])
        assertEquals(revision(first), reopened.inventory().single()["previous"])
        assertFalse(File(directory, "artifacts/${revision(third)["digest"]}.js").exists())
        listOf(first, second).forEach {
            assertTrue(File(directory, "artifacts/${revision(it)["digest"]}.js").exists())
        }
    }
    fun testPublicationReadFailureIsUnknownEvenAfterRename() {
        val store = custody(directory)
        val first = stage(store, "1.0", "first inert bytes".toByteArray())
        store.install(first["token"] as String, revision(first))
        val second = stage(store, "2.0", "second inert bytes".toByteArray())
        store.install(second["token"] as String, revision(second))
        val base = File(directory, "state.json")
        val hidden = File(directory, "state-hidden.json")
        val broken = custody(directory, finishWrite = { file, output ->
            file.finishWrite(output)
            check(base.renameTo(hidden)) // Deterministic read failure, not filesystem permission dependent.
        })
        val third = stage(broken, "3.0", "third inert bytes".toByteArray())
        try {
            broken.install(third["token"] as String, revision(third))
            fail("Expected unknown publication")
        } catch (failure: NovelArtifacts.PublicationUnknown) {
            assertEquals("unknown", failure.evidence["publication"])
            assertEquals(revision(second), broken.inventory().single()["current"])
        }
        listOf(first, second, third).forEach {
            assertTrue(File(directory, "artifacts/${revision(it)["digest"]}.js").exists())
        }
        rejects { broken.rollback(revision(second), revision(first)) }
        check(hidden.renameTo(base))
        val reopened = custody(directory)
        assertEquals(revision(third), reopened.inventory().single()["current"])
        assertEquals(revision(second), reopened.inventory().single()["previous"])
        assertFalse(File(directory, "artifacts/${revision(first)["digest"]}.js").exists())
    }
    fun testRemoveReinstallSameRevisionRejectsABAStage() {
        val store = custody(directory)
        val original = stage(store)
        store.install(original["token"] as String, revision(original))
        val stale = stage(store, "2.0")
        store.uninstall(revision(original))
        val replacement = stage(store)
        store.install(replacement["token"] as String, revision(replacement))
        assertEquals(revision(original), store.inventory().single()["current"])
        rejects { store.install(stale["token"] as String, revision(stale)) }
    }
    fun testStateSizeAndEntryLimitsFailClosed() {
        custody(directory)
        val file = File(directory, "state.json")
        file.writeText(" ".repeat(1024 * 1024 + 1))
        rejects { custody(directory) }
        val rows = org.json.JSONArray()
        repeat(501) { rows.put(JSONObject()) }
        file.writeText(JSONObject().put("schema", 1).put("generation", 0).put("entries", rows).toString())
        rejects { custody(directory) }
    }
    fun testAtomicFailureAndValidationKeepPreviousPointer() {
        val store = custody(directory)
        val old = stage(store)
        store.install(old["token"] as String, revision(old))
        val broken = custody(directory) { error("injected precommit failure") }
        val next = stage(broken, "2.0")
        rejects { broken.install(next["token"] as String, revision(next)) }
        assertEquals(revision(old), custody(directory).inventory().single()["current"])
        rejects { store.stage(metadata() + ("origins" to listOf("https://example.com/path")), {}, { it.write(65) }) }
        rejects { store.stage(metadata() + ("origins" to listOf("https://other.com")), {}, { it.write(65) }) }
        rejects { store.stage(metadata() + ("unexpected" to true), {}, { it.write(65) }) }
        val file = File(directory, "state.json")
        val root = JSONObject(file.readText()).put("unexpected", true)
        file.writeText(root.toString())
        rejects { custody(directory) }
        assertTrue(NovelArtifacts.compareVersions("999999999999999999999", "2") > 0)
        assertTrue(NovelArtifacts.compareVersions("1.0-rc.2", "1.0-rc.10") < 0)
        assertEquals(0, NovelArtifacts.compareVersions("1.0+first", "1.0.0+second"))
    }
}
