package com.casper.dalilak.ai

import org.json.JSONArray
import org.json.JSONObject

/** code = كود HTTP لو الخطأ من خدمة خارجية، وlimit = الخطأ بسبب حد استخدام/رصيد (بيتعامل معاه بـ fallback). */
class AiException(message: String, val code: Int = 0, val limit: Boolean = false) : Exception(message)

data class SourceRef(val title: String, val uri: String)

data class AiTool(
    val name: String,
    val type: String,
    val pricing: String, // FREE | FREEMIUM | PAID | UNKNOWN
    val reason: String,
    val limitation: String,
    val url: String,
    val details: String,
    val verified: List<String>,
    val unverified: List<String>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name); put("type", type); put("pricing", pricing)
        put("reason", reason); put("limitation", limitation); put("url", url)
        put("details", details)
        put("verified", JSONArray(verified)); put("unverified", JSONArray(unverified))
    }

    companion object {
        fun fromJson(o: JSONObject, grounded: Boolean = true, unverifiedNote: String = ""): AiTool {
            var verified = strList(o, "verified")
            var unverified = strList(o, "unverified")
            var pricing = normPricing(o.optString("pricing"))
            if (!grounded) {
                // الموديل ما بحثش على النت: ممنوع نعتبر أي معلومة متحقق منها.
                verified = emptyList()
                if (unverifiedNote.isNotBlank()) unverified = unverified + unverifiedNote
                pricing = "UNKNOWN"
            }
            return AiTool(
                name = o.optString("name").trim(),
                type = o.optString("type").trim(),
                pricing = pricing,
                reason = o.optString("reason").trim(),
                limitation = o.optString("limitation").trim(),
                url = safeUrl(o.optString("url")),
                details = o.optString("details").trim(),
                verified = verified,
                unverified = unverified
            )
        }

        private fun normPricing(s: String): String = when (val u = s.trim().uppercase()) {
            "FREE", "FREEMIUM", "PAID" -> u
            else -> "UNKNOWN"
        }

        fun safeUrl(u: String): String {
            val t = u.trim()
            return if (t.startsWith("https://") || t.startsWith("http://")) t else ""
        }

        private fun strList(o: JSONObject, key: String): List<String> {
            val v = o.opt(key)
            return when (v) {
                is JSONArray -> (0 until v.length()).map { v.optString(it).trim() }.filter { it.isNotEmpty() }
                is String -> if (v.isBlank()) emptyList() else listOf(v.trim())
                else -> emptyList()
            }
        }
    }
}

data class AiResult(
    val summary: String,
    val preferred: String,
    val note: String,
    val tools: List<AiTool>,
    val sources: List<SourceRef>,
    val searchQueries: List<String>,
    val verifiedAt: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("summary", summary); put("preferred", preferred); put("note", note)
        put("verifiedAt", verifiedAt)
        put("tools", JSONArray().also { a -> tools.forEach { a.put(it.toJson()) } })
        put("sources", JSONArray().also { a ->
            sources.forEach { a.put(JSONObject().put("title", it.title).put("uri", it.uri)) }
        })
        put("queries", JSONArray(searchQueries))
    }

    companion object {
        /** parse لرد الموديل (grounded = هل حصل بحث فعلي على النت) */
        fun parse(
            o: JSONObject,
            sources: List<SourceRef>,
            queries: List<String>,
            now: Long,
            unverifiedNote: String,
            noSearchNote: String
        ): AiResult {
            val grounded = queries.isNotEmpty()
            val arr = o.optJSONArray("tools")
            val tools = ArrayList<AiTool>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val t = arr.optJSONObject(i) ?: continue
                    val tool = AiTool.fromJson(t, grounded, unverifiedNote)
                    if (tool.name.isNotEmpty()) tools.add(tool)
                    if (tools.size == 5) break
                }
            }
            val pref = o.optString("preferred").trim()
            val prefOk = if (tools.any { it.name.equals(pref, ignoreCase = true) }) pref else ""
            val note = listOf(o.optString("note").trim(), if (grounded) "" else noSearchNote)
                .filter { it.isNotEmpty() }.joinToString("\n")
            return AiResult(o.optString("summary").trim(), prefOk, note, tools, sources, queries, now)
        }

        /** قراءة نتيجة متخزنة في السجل */
        fun fromStored(o: JSONObject): AiResult {
            val arr = o.optJSONArray("tools")
            val tools = ArrayList<AiTool>()
            if (arr != null) for (i in 0 until arr.length()) {
                val t = arr.optJSONObject(i) ?: continue
                tools.add(AiTool.fromJson(t))
            }
            val sArr = o.optJSONArray("sources")
            val sources = ArrayList<SourceRef>()
            if (sArr != null) for (i in 0 until sArr.length()) {
                val s = sArr.optJSONObject(i) ?: continue
                sources.add(SourceRef(s.optString("title"), AiTool.safeUrl(s.optString("uri"))))
            }
            val qArr = o.optJSONArray("queries")
            val queries = if (qArr == null) emptyList() else (0 until qArr.length()).map { qArr.optString(it) }
            return AiResult(
                o.optString("summary"), o.optString("preferred"), o.optString("note"),
                tools, sources, queries, o.optLong("verifiedAt", 0L)
            )
        }
    }
}
