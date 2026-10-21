package io.github.toanhp123.hikari.extensions.lnreader

import android.content.Context
import io.github.toanhp123.hikari.plugins.PublicHttps
import org.json.JSONObject

/** Consented, side-effect-denied export inspection, not JS safety or provider proof. */
internal class NovelAdmission(private val context: Context) {
    internal class Rejected(val reason: String = "execution-rejected") : IllegalStateException("Novel admission rejected")
    fun inspect(artifact: Map<String, Any>, bundle: String) {
        try {
            val host = LnReaderHost(context, artifact.getValue("id") as String, emptySet(), admissionOnly = true)
            val bootstrap = context.assets.open("lnreader/host.js").use {
                it.readBytes().toString(Charsets.UTF_8)
            }
            val script = """
                $bootstrap
                var module={exports:{}};var exports=module.exports;
                const admissionHost=__hikariHost;
                const rejectAdmission=function(reason){
                  try{admissionHost(reason)}finally{throw 'Admission rejected'}
                };
                const admissionPromise=Promise.resolve();
                const finishAdmission=admissionPromise.then.bind(admissionPromise);
                globalThis.require=(function(originalRequire){return function(name){
                  try{return originalRequire(name)}catch(error){
                    rejectAdmission('{"op":"unsupportedModule"}');
                  }
                };})(require);
                $bundle
                finishAdmission(function(){
                  const plugin=module.exports.default||module.exports;
                  if(!plugin || (typeof plugin!=='object' && typeof plugin!=='function')) rejectAdmission('{"op":"invalidMetadata"}');
                  if(typeof plugin.searchNovels!=='function' || typeof plugin.parseNovel!=='function' ||
                     typeof plugin.parseChapter!=='function') rejectAdmission('{"op":"missingRequiredMethod"}');
                  if(plugin.parsePage!==undefined && typeof plugin.parsePage!=='function') rejectAdmission('{"op":"missingRequiredMethod"}');
                  const identity={id:plugin.id,name:plugin.name,version:plugin.version,site:plugin.site};
                  if(typeof identity.id!=='string' || typeof identity.name!=='string' ||
                     typeof identity.version!=='string' || typeof identity.site!=='string') rejectAdmission('{"op":"invalidMetadata"}');
                  return identity;
                })
            """.trimIndent()
            val result = BoundedQuickJs().execute(script, host)
            host.admissionReason?.let { throw Rejected(it) }
            if (result == null) throw Rejected()
            try {
            val exported = JSONObject(result)
            require(exported.keys().asSequence().toSet() == setOf("id", "name", "version", "site"))
            for (key in listOf("id", "name", "version")) require(exported.get(key) == artifact.getValue(key))
            val site = exported.get("site") as? String ?: throw Rejected()
            // Same native URL canonicalization as frozen consent; only root spelling differs.
            val parsed = PublicHttps.url(site)
            val canonical = parsed.newBuilder().host(parsed.host.trimEnd('.')).build().toString()
            require(canonical == artifact.getValue("site"))
            } catch (_: Exception) { throw Rejected("export-metadata-mismatch") }
        } catch (rejected: Rejected) { throw rejected }
        catch (_: Exception) { throw Rejected() }
    }
    companion object { const val POLICY = 1 }
}
