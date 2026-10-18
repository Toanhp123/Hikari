package io.github.toanhp123.hikari.extensions.lnreader

import org.json.JSONArray
import org.json.JSONObject

/** Only shapes consumed by LnReaderNovelSource; unknown optional fields remain allowed. */
internal object LnReaderContract {
    fun path(value: Any?): String = (value as? String ?: error("Invalid path")).also {
        require(it.isNotEmpty() && it.length <= 8192 && it.none { c -> c <= ' ' || c == '\u007f' })
    }
    fun arguments(method: String, encoded: String): JSONArray {
        require(encoded.toByteArray(Charsets.UTF_8).size <= 4 * 1024 * 1024)
        val args = JSONArray(encoded)
        when (method) {
            "searchNovels" -> {
                require(args.length() == 2 && args.get(0) is String && args.getString(0).length <= 8192)
                integer(args.get(1), 1, Int.MAX_VALUE)
            }
            "parseNovel", "parseChapter" -> { require(args.length() == 1); path(args.get(0)) }
            "parsePage" -> {
                require(args.length() == 2); path(args.get(0))
                val page = args.get(1) as? String ?: error("Invalid page")
                require(page.toIntOrNull() in 2..100 && page == page.toInt().toString())
            }
            else -> error("Unsupported method")
        }
        return args
    }
    private fun integer(value: Any?, min: Int, max: Int): Int {
        require(value is Number && value.toDouble().isFinite())
        val number = value.toLong()
        require(number in min.toLong()..max.toLong() && value.toDouble() == number.toDouble())
        return number.toInt()
    }
    private fun optionalText(row: JSONObject, key: String) {
        if (!row.isNull(key)) require(row.get(key) is String)
    }
    private fun names(row: JSONObject, key: String) {
        if (row.isNull(key)) return
        val value = row.get(key)
        require(value is String || value is JSONArray && (0 until value.length()).all { value.get(it) is String })
    }
    private fun number(row: JSONObject, key: String) {
        if (!row.isNull(key)) require(row.get(key) is Number && (row.get(key) as Number).toDouble().isFinite())
    }
    private fun metadata(row: JSONObject, requirePath: Boolean) {
        require(row.get("name") is String)
        if (requirePath || !row.isNull("path")) path(row.get("path"))
        listOf("cover", "summary", "status").forEach { optionalText(row, it) }
        listOf("author", "artist", "genres").forEach { names(row, it) }
        number(row, "rating")
    }
    private fun chapters(rows: JSONArray) {
        require(rows.length() <= 50000)
        val paths = mutableSetOf<String>()
        repeat(rows.length()) {
            val row = rows.getJSONObject(it)
            require(row.get("name") is String && paths.add(path(row.get("path"))))
            number(row, "chapterNumber"); optionalText(row, "releaseTime"); names(row, "scanlator")
        }
    }
    fun output(method: String, value: Any?, hasParsePage: Boolean) {
        when (method) {
            "searchNovels" -> {
                val rows = value as? JSONArray ?: error("Invalid search")
                require(rows.length() <= 50000)
                repeat(rows.length()) { metadata(rows.getJSONObject(it), true) }
            }
            "parseNovel" -> {
                val row = value as? JSONObject ?: error("Invalid details")
                metadata(row, false)
                // Dart adapter casts totalPages to int, not num.
                val pages = if (row.isNull("totalPages")) 1 else {
                    require(row.get("totalPages") is Int || row.get("totalPages") is Long)
                    integer(row.get("totalPages"), 1, 100)
                }
                require(pages == 1 || hasParsePage)
                if (!row.isNull("chapters")) chapters(row.getJSONArray("chapters"))
            }
            "parsePage" -> chapters((value as? JSONObject ?: error("Invalid page")).getJSONArray("chapters"))
            "parseChapter" -> require(value is String)
            else -> error("Unsupported method")
        }
    }
}
