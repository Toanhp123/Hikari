package io.github.toanhp123.hikari.plugins

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.test.InstrumentationTestCase
import io.github.toanhp123.hikari.extensions.lnreader.LnReaderHost
import io.github.toanhp123.hikari.extensions.lnreader.NovelAdmission
import java.io.File
import org.json.JSONObject

/** Authored bundles only. Bounded QuickJS, no live provider or package actions. */
@Suppress("DEPRECATION")
class NovelAdmissionTest : InstrumentationTestCase() {
    private val artifact = mapOf<String, Any>("id" to "authored", "name" to "Authored", "version" to "1.0",
        "site" to "https://example.org/", "repository" to "https://example.org/index.json",
        "origins" to listOf("https://example.org"), "digest" to "a".repeat(64))
    private val valid = """
        module.exports={id:'authored',name:'Authored',version:'1.0',site:'https://example.org',
          searchNovels(){throw Error('must not invoke')},parseNovel(){throw Error('must not invoke')},
          parseChapter(){throw Error('must not invoke')}};
    """.trimIndent()
    private fun context(): Context {
        val target = instrumentation.targetContext
        check(target.packageName.endsWith(".localmediatest"))
        return object : ContextWrapper(target) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                error("Admission must not access preferences")
        }
    }
    private fun rejected(bundle: String, reason: String? = null) {
        try { NovelAdmission(context()).inspect(artifact, bundle); fail("Expected rejected admission") }
        catch (failure: NovelAdmission.Rejected) {
            assertEquals("Novel admission rejected", failure.message)
            if (reason != null) assertEquals(reason, failure.reason)
        }
    }
    fun testUpstreamV3HelpersAreAdmittedWithoutProviderExecution() {
        // Representative compiled imports from LNReader 69shu/Hako/Light Novel VN.
        val bundle = """
            const status=require('@libs/novelStatus').NovelStatus;
            const filters=require('@libs/filterInputs').FilterTypes;
            const parser=require('htmlparser2').Parser;
            if(status.Completed!=='Completed'||filters.Picker!=='Picker'||typeof parser!=='function')
              throw Error('Upstream helpers unavailable');
            module.exports={id:'authored',name:'Authored',version:'1.0',site:'https://example.org',
              searchNovels(){throw Error('Provider must not run during admission')},
              parseNovel(){throw Error('Provider must not run during admission')},
              parseChapter(){throw Error('Provider must not run during admission')}};
        """.trimIndent()
        NovelAdmission(context()).inspect(artifact, bundle)
        // Loading helpers does not grant access to arbitrary modules or IO.
        rejected("require('fs');" + bundle, "unsupported-module")
        rejected("try{__hikariHost('{\"op\":\"fetch\"}')}catch(e){};" + bundle, "host-operation-denied")
    }

    fun testTextMultipartEnvelopeAndUnsafeFields() {
        val fields = org.json.JSONArray().put(JSONObject().put("name", "keyword").put("value", "Tiếng Việt"))
        val body = JSONObject().put("__hikariMultipart", "v1").put("fields", fields)
        val requestBody = LnReaderHost.requestBody(body)
        assertTrue(requestBody.contentType().toString().startsWith("multipart/form-data; boundary="))
        val output = okio.Buffer()
        requestBody.writeTo(output)
        val encoded = output.readUtf8()
        assertTrue(encoded.contains("name=\"keyword\""))
        assertTrue(encoded.contains("Tiếng Việt"))
        fun refuses(value: Any?) {
            try { LnReaderHost.requestBody(value); fail("Expected invalid body rejection") }
            catch (_: IllegalArgumentException) { }
            catch (_: org.json.JSONException) { }
        }
        refuses(JSONObject().put("__hikariMultipart", "v2").put("fields", fields))
        refuses(JSONObject().put("__hikariMultipart", "v1").put("fields", org.json.JSONArray()))
        refuses(JSONObject().put("__hikariMultipart", "v1").put("fields",
            org.json.JSONArray().put(JSONObject().put("name", "bad\r\nheader").put("value", "text"))))
        refuses(JSONObject().put("__hikariMultipart", "v1").put("fields",
            org.json.JSONArray().put(JSONObject().put("name", "keyword").put("value", "x".repeat(66000)))))
        refuses(JSONObject().put("__hikariMultipart", "v1").put("fields",
            org.json.JSONArray().put(JSONObject().put("name", "keyword").put("value", JSONObject()))))
        refuses(org.json.JSONArray().put("unexpected"))
    }

    fun testPinnedLnReaderV3ModuleTableAndDeniedSideEffects() {
        // The JavaScript host exposes the pure portions of the upstream module
        // map without allowing network, timers or storage during admission.
        val compiledImports = """
            const status=require('@libs/novelStatus').NovelStatus;
            const filters=require('@libs/filterInputs').FilterTypes;
            const {Parser}=require('htmlparser2');
            const absolute=require('@libs/isAbsoluteUrl').isUrlAbsolute;
            const {defaultCover}=require('@libs/defaultCover');
            const encoder=require('urlencode');
            const storage=require('@libs/storage').storage;
            if(status.Ongoing!=='Ongoing'||filters.Switch!=='Switch'||
               typeof Parser!=='function'||!absolute('//example.org')||
               !defaultCover.startsWith('https://')||encoder.decode(encoder.encode('Việt'))!=='Việt'||
               typeof storage.get!=='function') throw Error('LNReader v3 API unavailable');
        """.trimIndent()
        NovelAdmission(context()).inspect(artifact, compiledImports + valid)
        rejected("try{require('@libs/storage').storage.set('key',123)}catch(e){};" + valid, "host-operation-denied")
        rejected("try{setTimeout(()=>{},1)}catch(e){};" + valid, "host-operation-denied")
        rejected("try{require('fs')}catch(e){};" + valid, "unsupported-module")
    }

    fun testIdentityMethodsModulesAndNoProviderInvocation() {
        NovelAdmission(context()).inspect(artifact, valid)
        NovelAdmission(context()).inspect(artifact, valid + "module.exports.parsePage=function(){};")
        rejected(valid.replace("name:'Authored'", "name:'Changed'"), "export-metadata-mismatch")
        rejected(valid.replace("version:'1.0'", "version:1"))
        rejected(valid.replace("https://example.org'", "https://example.org/other'"))
        rejected(valid + "module.exports.searchNovels=null;", "missing-required-method")
        rejected(valid + "module.exports.parsePage=null;")
        rejected("require('fs');" + valid)
        rejected("try{require('fs')}catch(e){};" + valid, "unsupported-module")
    }
    fun testReplacedGlobalBridgeCannotBypassExportOrModuleRejection() {
        val replaced = "globalThis.__hikariHost=()=>null;JSON.stringify=()=>'{\\\"op\\\":\\\"absoluteUrl\\\"}';"
        rejected(replaced + valid + "module.exports.searchNovels=null;", "missing-required-method")
        rejected(replaced + valid + "module.exports.parsePage=1;", "missing-required-method")
        rejected(replaced + "try{require('fs')}catch(e){};" + valid, "unsupported-module")
        rejected(replaced + valid + "module.exports.name=123;", "export-metadata-mismatch")
    }
    fun testStickyDenialIncludesCaughtLateGetterAndSerialization() {
        for (op in listOf("fetch", "resource", "get", "set", "remove", "keys", "delay", "unknown")) {
            rejected("try{__hikariHost(JSON.stringify({op:'$op'}))}catch(e){};" + valid, "host-operation-denied")
        }
        rejected(valid + "Object.defineProperty(module.exports,'name',{get(){try{localStorage.getItem('x')}catch(e){};return 'Authored'}});")
        rejected(valid + "module.exports.site={toJSON(){try{localStorage.setItem('x','y')}catch(e){};return 'https://example.org/'}};")
        rejected(valid + "Object.prototype.toJSON=function(){try{__hikariHost('{\\\"op\\\":\\\"get\\\",\\\"key\\\":\\\"x\\\"}')}catch(e){};return this};")
        rejected(valid + "Promise.resolve().then(()=>{try{localStorage.removeItem('x')}catch(e){}});")
        val host = LnReaderHost(context(), "authored", emptySet(), admissionOnly = true)
        assertEquals("\"https://example.org/chapter\"", host.call("{\"op\":\"absoluteUrl\",\"base\":\"https://example.org\",\"path\":\"/chapter\"}"))
        assertFalse(host.admissionDenied)
    }
    fun testRealAdmissionRejectionPreservesCustodyAndSiblingUserData() {
        val isolated = context()
        val root = File(isolated.cacheDir, "novel-admission-${System.nanoTime()}")
        assertTrue(root.mkdir())
        try {
            val directory = File(root, "custody")
            val sentinel = File(root, "user-data.sqlite").apply { writeText("unchanged user data") }
            val userBytes = sentinel.readBytes()
            val admission = NovelAdmission(isolated)
            var inspections = 0
            fun custody() = NovelArtifacts(directory, inspect = { identity, bundle ->
                inspections++
                admission.inspect(identity, bundle)
            })
            val store = custody()
            val first = store.stage(artifact - "digest", {}, { it.write(valid.toByteArray(Charsets.UTF_8)) })
            val token = first.getValue("token") as String
            val identity = first.getValue("artifact") as Map<*, *>
            assertEquals(0, inspections)
            assertTrue(store.inventory().isEmpty())
            assertFalse(File(directory, "state.json").exists())
            try {
                store.install(token, identity + ("name" to "Changed"))
                fail("Expected exact-consent rejection")
            } catch (failure: IllegalStateException) {
                assertEquals("Displayed consent changed", failure.message)
            }
            assertEquals(0, inspections)
            assertTrue(store.inventory().isEmpty())
            assertFalse(File(directory, "state.json").exists())

            store.install(token, identity)
            assertEquals(1, inspections)
            val before = store.inventory()
            assertEquals(identity, before.single()["current"])
            assertNull(before.single()["previous"])
            assertEquals("admitted", before.single()["admission"])
            try {
                store.install(token, identity)
                fail("Expected consumed stage rejection")
            } catch (failure: IllegalStateException) {
                assertEquals("Unknown or consumed Novel stage", failure.message)
            }
            assertEquals(1, inspections)
            val pointer = File(directory, "state.json")
            val pointerBytes = pointer.readBytes()
            val generation = JSONObject(pointer.readText()).getLong("generation")
            val current = File(directory, "artifacts/${identity["digest"]}.js")
            assertEquals(valid, current.readText())

            val denied = "try{localStorage.setItem('private-key','private-value')}catch(e){};" +
                valid.replace("version:'1.0'", "version:'2.0'")
            val update = store.stage(artifact - "digest" + ("version" to "2.0"), {}, {
                it.write(denied.toByteArray(Charsets.UTF_8))
            })
            assertEquals(1, inspections)
            try {
                store.install(update.getValue("token") as String, update.getValue("artifact") as Map<*, *>)
                fail("Expected sticky host-operation denial")
            } catch (failure: NovelAdmission.Rejected) {
                assertEquals("Novel admission rejected", failure.message)
                assertEquals("host-operation-denied", failure.reason)
            }
            assertEquals(2, inspections)
            assertEquals(before, store.inventory())
            assertTrue(pointerBytes.contentEquals(pointer.readBytes()))
            assertEquals(generation, JSONObject(pointer.readText()).getLong("generation"))
            assertEquals(valid, current.readText())
            assertTrue(userBytes.contentEquals(sentinel.readBytes()))

            val reopened = custody()
            assertEquals(2, inspections)
            assertEquals(before, reopened.inventory())
            assertTrue(pointerBytes.contentEquals(pointer.readBytes()))
            assertEquals(valid, current.readText())
            assertEquals(listOf(current.name), File(directory, "artifacts").listFiles()!!.map { it.name })
            assertTrue(File(directory, "staging").listFiles()!!.isEmpty())
            try {
                reopened.install(update.getValue("token") as String, update.getValue("artifact") as Map<*, *>)
                fail("Expected stale stage rejection after reopen")
            } catch (failure: IllegalStateException) {
                assertEquals("Unknown or consumed Novel stage", failure.message)
            }
            assertEquals(2, inspections)
            assertEquals(before, reopened.inventory())
            assertTrue(pointerBytes.contentEquals(pointer.readBytes()))
            assertTrue(userBytes.contentEquals(sentinel.readBytes()))
        } finally { root.deleteRecursively() }
    }
    fun testRuntimeLimitsRejectInsteadOfPublishingAdmission() {
        rejected("while(true){}")
        rejected("let values=[];while(true)values.push(new Array(10000).fill('x'))")
        rejected("function recurse(){return recurse()+1};recurse()")
        rejected("throw Error('private source text must not escape')")
    }
}
