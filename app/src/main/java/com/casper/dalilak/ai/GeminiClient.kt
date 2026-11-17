package com.casper.dalilak.ai

import com.casper.dalilak.I18n
import com.casper.dalilak.tr
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * عميل Gemini (REST) مع أداة google_search.
 * بيتكتب بحيث تبديل المزوّد أو الموديل سهل: كل حاجة خاصة بـ Gemini جوه الملف ده بس.
 * استدعاء blocking: يتنادى من Dispatchers.IO.
 */
object GeminiClient {
    private val MODEL_OK = Regex("^[A-Za-z0-9._-]{1,80}$")

    private const val SYSTEM = """You are the engine of "Dalilak", a personal assistant that finds the best digital tools (websites, Android/iOS apps, desktop software, AI tools, online services, platforms, marketplaces) for a task the user describes.
Rules:
- Use Google Search to check current facts before recommending: is the service still working, current price, free plan and its limits, key features, supported platforms, watermark, sign-up requirement, trial, recent changes.
- NEVER invent facts. Anything you could not confirm goes to "unverified", and pricing must be "UNKNOWN" if not confirmed.
- "verified" lists short facts you actually confirmed in this search.
- Return at most 5 tools. If only 1 or 2 really fit, return only those. Never pad the list with weak options.
- If the data is sufficient, set "preferred" to the exact name of the single best tool, otherwise "".
- Prefer free or low-cost options when they are sufficient. Respect the user's devices and experience level. Avoid tools the user rejected before unless clearly better now. Check whether tools already in the local database are still the best choice or whether something newer or better exists.
- Use only URLs you saw in search results. Leave url empty if unsure.
- Keep every text short (one sentence). Keep tool names in English.
- Output ONLY one JSON object. No markdown, no code fences. Exact shape:
{"summary":"","preferred":"","note":"","tools":[{"name":"","type":"","pricing":"FREE|FREEMIUM|PAID|UNKNOWN","reason":"","limitation":"","url":"","details":"","verified":[""],"unverified":[""]}]}"""

    fun search(apiKey: String, model: String, task: String, context: String): AiResult {
        if (!MODEL_OK.matches(model)) throw AiException(tr("اسم الموديل غير صالح"))
        val langLine = if (I18n.lang == "ar") {
            "Write all text values in Egyptian Arabic colloquial."
        } else {
            "Write all text values in English."
        }
        val userText = "$langLine\n\nTASK: $task\n\n$context"
        val body = JSONObject().apply {
            put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM)))
            )
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", userText)))
                )
            )
            put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
            put("generationConfig", JSONObject().put("temperature", 0.2))
        }
        val conn = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20_000
            conn.readTimeout = 120_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("x-goog-api-key", apiKey)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""
            if (code !in 200..299) throw AiException(errorMessage(code, text))
            return parseResponse(text)
        } finally {
            conn.disconnect()
        }
    }

    private fun errorMessage(code: Int, text: String): String {
        val server = try {
            JSONObject(text).getJSONObject("error").optString("message")
        } catch (e: Exception) {
            ""
        }.take(160)
        val base = when {
            code == 400 -> tr("الطلب مرفوض أو المفتاح مش صالح")
            code == 401 || code == 403 -> tr("المفتاح مش صالح أو مفيش صلاحية")
            code == 404 -> tr("الموديل مش موجود، غيّره من الإعدادات")
            code == 429 -> tr("وصلت للحد المسموح، جرب بعد شوية")
            code >= 500 -> tr("خدمة Gemini مشغولة، جرب تاني")
            else -> tr("حصل خطأ")
        }
        return if (server.isNotBlank()) "$base ($code): $server" else "$base ($code)"
    }

    private fun parseResponse(text: String): AiResult {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw AiException(tr("الرد مش بصيغة مفهومة"))
        }
        val cands = root.optJSONArray("candidates")
        if (cands == null || cands.length() == 0) throw AiException(tr("مفيش رد من الموديل (ممكن اتحجب)"))
        val cand = cands.getJSONObject(0)

        val sb = StringBuilder()
        val parts = cand.optJSONObject("content")?.optJSONArray("parts")
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val p = parts.optJSONObject(i) ?: continue
                if (p.optBoolean("thought", false)) continue
                sb.append(p.optString("text", ""))
            }
        }

        val queries = ArrayList<String>()
        val sources = ArrayList<SourceRef>()
        val gm = cand.optJSONObject("groundingMetadata")
        if (gm != null) {
            val q = gm.optJSONArray("webSearchQueries")
            if (q != null) for (i in 0 until q.length()) {
                val s = q.optString(i).trim()
                if (s.isNotEmpty() && s !in queries) queries.add(s)
            }
            val chunks = gm.optJSONArray("groundingChunks")
            if (chunks != null) for (i in 0 until chunks.length()) {
                val web = chunks.optJSONObject(i)?.optJSONObject("web") ?: continue
                val uri = AiTool.safeUrl(web.optString("uri"))
                if (uri.isNotEmpty() && sources.none { it.uri == uri }) {
                    sources.add(SourceRef(web.optString("title").ifBlank { uri }, uri))
                }
            }
        }

        val obj = extractJson(sb.toString())
        return AiResult.parse(
            obj, sources, queries, System.currentTimeMillis(),
            tr("ما اتحققش بالبحث"),
            tr("الموديل ما بحثش على النت المرة دي، فكل المعلومات غير مؤكدة")
        )
    }

    private fun extractJson(t: String): JSONObject {
        val s = t.indexOf('{')
        val e = t.lastIndexOf('}')
        if (s < 0 || e <= s) throw AiException(tr("الرد مش بصيغة مفهومة"))
        return try {
            JSONObject(t.substring(s, e + 1))
        } catch (ex: JSONException) {
            throw AiException(tr("الرد مش بصيغة مفهومة"))
        }
    }
}
