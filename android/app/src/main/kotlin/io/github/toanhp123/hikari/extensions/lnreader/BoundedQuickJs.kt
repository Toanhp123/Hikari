package io.github.toanhp123.hikari.extensions.lnreader

/** Fresh native runtime per operation: 32 MiB heap, 512 KiB stack, 5s deadline. */
class BoundedQuickJs {
    private external fun executeNative(script: ByteArray, host: LnReaderHost): ByteArray?
    fun execute(script: String, host: LnReaderHost): String? =
        executeNative(script.toByteArray(Charsets.UTF_8), host)?.toString(Charsets.UTF_8)
    companion object { init { System.loadLibrary("hikari_lnreader") } }
}
