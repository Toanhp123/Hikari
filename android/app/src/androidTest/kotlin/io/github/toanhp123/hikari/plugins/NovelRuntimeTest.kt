package io.github.toanhp123.hikari.plugins

import android.test.InstrumentationTestCase
import io.github.toanhp123.hikari.extensions.lnreader.LnReaderContract
import io.github.toanhp123.hikari.extensions.lnreader.LnReaderHost
import io.github.toanhp123.hikari.extensions.lnreader.LnReaderRuntime
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Authored offline bytes only; no live providers or package actions. */
@Suppress("DEPRECATION")
class NovelRuntimeTest : InstrumentationTestCase() {
    private fun metadata(version: String = "1.0", id: String = "frozen") = mapOf<String, Any>(
        "id" to id, "name" to "Frozen", "version" to version, "site" to "https://example.org/",
        "repository" to "https://example.org/index.json", "origins" to listOf("https://example.org"))
    private fun bundle(label: String) = """
        module.exports={searchNovels(){return [{name:'$label',path:'/novel'}]},
          parseNovel(){return {name:'$label',totalPages:2,chapters:[]}},
          parseChapter(){return '<p>$label</p>'}};
    """.trimIndent()
    private fun rejects(block: () -> Unit) {
        try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {}
    }
    fun testFrozenBytesSurviveUpdateUninstallAndEvidenceDoesNotCapture() {
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".localmediatest"))
        val dir = File(context.cacheDir, "runtime-${System.nanoTime()}")
        try {
            val store = NovelArtifacts(dir, inspect = { _, _ -> }) // Test-only custody setup, not production admission.
            fun install(version: String, label: String): Map<String, Any> {
                val stage = store.stage(metadata(version), {}, { it.write(bundle(label).toByteArray()) })
                val artifact = stage["artifact"] as Map<String, Any>
                store.install(stage["token"] as String, artifact)
                return artifact
            }
            val first = install("1.0", "First")
            var captures = 0
            val runtime = LnReaderRuntime { _, budget -> captures++; store.capture(budget) }
            assertEquals("notInitialized", runtime.evidence()["initialization"])
            assertEquals(0, captures)
            runtime.initialize(context)
            val oldSources = runtime.sources()
            assertTrue(runtime.invoke(context, "frozen", "searchNovels", "[\"q\",1]").contains("First"))
            try { runtime.invoke(context, "frozen", "parseNovel", "[\"/novel\"]"); fail("Missing parsePage") }
            catch (failure: LnReaderRuntime.Failure) { assertEquals("invalid-output", failure.reason) }
            val second = install("2.0", "Second")
            store.uninstall(second)
            runtime.initialize(context)
            assertEquals(1, captures)
            assertEquals(oldSources, runtime.sources())
            assertTrue(runtime.invoke(context, "frozen", "parseChapter", "[\"/chapter\"]").contains("First"))
            val loaded = runtime.evidence()["loaded"] as List<Map<String, Any?>>
            assertEquals(first, loaded.single { it["id"] == "frozen" }["artifact"])
            (loaded.single { it["id"] == "frozen" }["artifact"] as MutableMap<String, Any>)["name"] = "tampered copy"
            assertEquals("Frozen", ((runtime.evidence()["loaded"] as List<Map<String, Any?>>)
                .single { it["id"] == "frozen" }["artifact"] as Map<*, *>)["name"])
        } finally { dir.deleteRecursively() }
    }
    fun testCollisionAndCaptureFailureStayFailedWithoutRetry() {
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".localmediatest"))
        val artifact = JSONObject(metadata() + ("digest" to "a".repeat(64))).toString()
        var captures = 0
        val runtime = LnReaderRuntime { _, _ ->
            captures++
            val code = NovelArtifacts.CapturedCode(artifact, bundle("First"))
            NovelArtifacts.Capture(listOf(code, code), emptyList())
        }
        repeat(2) { try { runtime.initialize(context); fail("Expected collision") }
            catch (failure: LnReaderRuntime.Failure) { assertEquals("collision", failure.reason) } }
        assertEquals(1, captures)
        assertEquals("failed", runtime.evidence()["initialization"])
        assertTrue((runtime.evidence()["loaded"] as List<*>).isEmpty())
    }
    fun testPaginatedHappyContractAndStickyCaptureFailure() {
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".localmediatest"))
        val artifact = JSONObject(metadata() + ("digest" to "a".repeat(64))).toString()
        val code = bundle("First").replace("parseChapter(){", "parsePage(path,page){return {chapters:[{name:'Page '+page,path:'/chapter-'+page}]}},parseChapter(){")
        val runtime = LnReaderRuntime { _, _ -> NovelArtifacts.Capture(listOf(NovelArtifacts.CapturedCode(artifact, code)), emptyList()) }
        runtime.initialize(context)
        assertEquals(2, JSONObject(runtime.invoke(context, "frozen", "parseNovel", "[\"/novel\"]")).getInt("totalPages"))
        assertEquals("/chapter-2", JSONObject(runtime.invoke(context, "frozen", "parsePage", "[\"/novel\",\"2\"]"))
            .getJSONArray("chapters").getJSONObject(0).getString("path"))
        var attempts = 0
        val broken = LnReaderRuntime { _, _ -> attempts++; error("capture failed") }
        repeat(2) { try { broken.initialize(context); fail("Expected capture failure") }
            catch (failure: LnReaderRuntime.Failure) { assertEquals("snapshot-failed", failure.reason) } }
        assertEquals(1, attempts)
    }
    fun testCaptureRehashesAndEnforcesBudget() {
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".localmediatest"))
        val dir = File(context.cacheDir, "capture-${System.nanoTime()}")
        try {
            val store = NovelArtifacts(dir, inspect = { _, _ -> })
            val stage = store.stage(metadata(), {}, { it.write(bundle("First").toByteArray()) })
            val artifact = stage["artifact"] as Map<String, Any>
            store.install(stage["token"] as String, artifact)
            assertEquals(bundle("First"), store.capture(4096).code.single().code)
            rejects { store.capture(1) }
            val healthyStage = store.stage(metadata(id = "healthy"), {}, { it.write(bundle("Healthy").toByteArray()) })
            store.install(healthyStage["token"] as String, healthyStage["artifact"] as Map<String, Any>)
            val file = File(dir, "artifacts/${artifact["digest"]}.js")
            file.writeText("corrupt")
            assertEquals("corrupt", store.capture(4096).issues.single().reason)
            assertEquals(bundle("Healthy"), store.capture(4096).code.single().code)
            file.delete()
            assertEquals("missing", store.capture(4096).issues.single().reason)
            val pointer = File(dir, "state.json")
            val legacy = JSONObject(pointer.readText()).put("schema", 1)
            val entries = legacy.getJSONArray("entries")
            repeat(entries.length()) { entries.getJSONObject(it).remove("currentAdmission"); entries.getJSONObject(it).remove("previousAdmission") }
            pointer.writeText(legacy.toString())
            var evaluations = 0
            val reopened = NovelArtifacts(dir, inspect = { _, _ -> evaluations++ })
            assertTrue(reopened.capture(4096).code.isEmpty())
            assertTrue(reopened.capture(4096).issues.all { it.reason == "admission-unknown" })
            assertEquals(0, evaluations)
        } finally { dir.deleteRecursively() }
    }
    fun testHealthySnapshotsAndPackagedCollision() {
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".localmediatest"))
        check(context.assets.list("lnreader/plugins").orEmpty().contains("contract.json")) { "Enable lnreaderContractFixture" }
        fun captured(id: String) = NovelArtifacts.CapturedCode(JSONObject(metadata(id = id) + ("digest" to "a".repeat(64))).toString(), bundle(id))
        val healthy = LnReaderRuntime { _, _ -> NovelArtifacts.Capture(listOf(captured("one"), captured("two")), emptyList()) }
        healthy.initialize(context)
        assertTrue(healthy.sources().map { it["id"] }.containsAll(listOf("one", "two", "hikari-contract")))
        var captures = 0
        val collision = LnReaderRuntime { _, _ -> captures++; NovelArtifacts.Capture(listOf(captured("hikari-contract")), emptyList()) }
        repeat(2) { try { collision.initialize(context); fail("Expected packaged collision") }
            catch (failure: LnReaderRuntime.Failure) { assertEquals("collision", failure.reason) } }
        assertEquals(1, captures)
        assertTrue((collision.evidence()["loaded"] as List<*>).isEmpty())
    }
    fun testGlobalBudgetFailurePublishesNothingAndNeverRetries() {
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".localmediatest"))
        val dir = File(context.cacheDir, "budget-${System.nanoTime()}")
        try {
            val store = NovelArtifacts(dir, inspect = { _, _ -> })
            for (id in listOf("one", "two")) {
                val stage = store.stage(metadata(id = id), {}, { it.write(bundle(id).toByteArray()) })
                store.install(stage["token"] as String, stage["artifact"] as Map<String, Any>)
            }
            var captures = 0
            val runtime = LnReaderRuntime { _, _ -> captures++; store.capture(bundle("one").toByteArray().size.toLong()) }
            repeat(2) { try { runtime.initialize(context); fail("Expected total capture budget failure") }
                catch (failure: LnReaderRuntime.Failure) { assertEquals("snapshot-failed", failure.reason) } }
            assertEquals(1, captures)
            assertEquals("failed", runtime.evidence()["initialization"])
            assertTrue((runtime.evidence()["loaded"] as List<*>).isEmpty())
            assertEquals(2, store.inventory().size)
        } finally { dir.deleteRecursively() }
    }
    fun testFatalCaptureFailureNeverRetriesOrPublishesPartialSources() {
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".localmediatest"))
        var captures = 0
        val fatal = AssertionError("private fatal detail")
        val runtime = LnReaderRuntime { _, _ -> captures++; throw fatal }
        assertEquals("notInitialized", runtime.evidence()["initialization"])
        assertEquals(0, captures)
        try { runtime.initialize(context); fail("Expected fatal capture error") }
        catch (error: AssertionError) { assertTrue(error === fatal) }
        assertEquals("failed", runtime.evidence()["initialization"])
        assertEquals("snapshot-failed", runtime.evidence()["failure"])
        assertTrue((runtime.evidence()["loaded"] as List<*>).isEmpty())
        try { runtime.initialize(context); fail("Expected sticky failure") }
        catch (failure: LnReaderRuntime.Failure) { assertEquals("snapshot-failed", failure.reason) }
        assertEquals(1, captures)
    }
    fun testShapeAndExactOriginBoundaries() {
        assertEquals(LnReaderHost.canonicalOrigin("https://example.org:443/a"), LnReaderHost.canonicalOrigin("https://example.org./"))
        rejects { LnReaderHost.canonicalOrigin("https://example.org:444/") }
        rejects { LnReaderContract.arguments("parseChapter", "[\"bad path\"]") }
        rejects { LnReaderContract.arguments("searchNovels", "[\"q\",1.5]") }
        rejects { LnReaderContract.output("searchNovels", JSONArray("[{\"name\":4,\"path\":\"/n\"}]"), false) }
        rejects { LnReaderContract.output("parseNovel", JSONObject("{\"name\":\"N\",\"totalPages\":2}"), false) }
        rejects { LnReaderContract.output("parseNovel", JSONObject("{\"name\":\"N\",\"author\":[2]}"), true) }
        rejects { LnReaderContract.output("parseChapter", JSONObject(), true) }
        LnReaderContract.output("parseNovel", JSONObject("{\"name\":\"N\",\"totalPages\":2,\"chapters\":[]}"), true)
    }
}
