package com.casper.dalilak.ai

import com.casper.dalilak.I18n
import com.casper.dalilak.tr
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * محرك البحث والترشيح. Gemini هو المخ، والبحث على النت بيتم بإحدى طريقتين:
 *  1) Tavily (مجاني، من غير كارت) ← Gemini بيقرا النتايج بس.
 *  2) بحث جوجل الرسمي (Grounding) لو مفيش مفتاح Tavily، وده متاح بس في الخطة المدفوعة.
 * ولو مفيش أي بحث متاح: الترشيح بيكمّل من غير بحث وكل المعلومات بتتعلّم "غير مؤكدة".
 * كل حاجة خاصة بـ Gemini جوه الملف ده بس عشان تبديل المزوّد يبقى سهل.
 * استدعاء blocking: يتنادى من Dispatchers.IO.
 */
object GeminiClient {
    private val MODEL_OK = Regex("^[A-Za-z0-9._-]{1,80}$")
    private const val MAX_HITS = 12
    private const val MAX_SNIPPET = 600

    private const val RULES = """- NEVER invent facts. Anything you could not confirm goes to "unverified", and pricing must be "UNKNOWN" if not confirmed.
- "verified" lists short facts you actually confirmed from the evidence described above.
- Return at most 5 tools. If only 1 or 2 really fit, return only those. Never pad the list with weak options.
- If the data is sufficient, set "preferred" to the exact name of the single best tool, otherwise "".
- Prefer free or low-cost options when they are sufficient. Respect the user's devices and experience level. Avoid tools the user rejected before unless clearly better now. Check whether tools already in the local database are still the best choice or whether something newer or better exists.
- Keep every text short (one sentence). Keep tool names in English.
- Output ONLY one JSON object. No markdown, no code fences. Exact shape:
{"summary":"","preferred":"","note":"","tools":[{"name":"","type":"","pricing":"FREE|FREEMIUM|PAID|UNKNOWN","reason":"","limitation":"","url":"","details":"","verified":["fact"],"unverified":["fact"]}]}"""

    private const val INTRO = """You are the engine of "Dalilak", a personal assistant that finds the best digital tools (websites, Android/iOS apps, desktop software, AI tools, online services, platforms, marketplaces) for a task the user describes.
Check: is the service still working, current price, free plan and its limits, key features, supported platforms, watermark, sign-up requirement, trial, recent changes.
"""

    /** مع بحث جوجل الرسمي */
    private const val SYSTEM_GROUNDED = INTRO + """Use Google Search to check current facts before recommending.
Use only URLs you saw in search results. Leave url empty if unsure.
""" + RULES

    /** مع نتايج Tavily جاهزة */
    private const val SYSTEM_RESULTS = INTRO + """You are given numbered SEARCH RESULTS gathered from the web for this task. They are your ONLY evidence.
A fact may go to "verified" only if it is stated in those results. Anything you only know from memory goes to "unverified".
Use only URLs that appear in the results. Leave url empty if unsure.
""" + RULES

    /** من غير أي بحث */
    private const val SYSTEM_OFFLINE = INTRO + """You have NO web access in this run, so you cannot verify anything. Put every fact in "unverified" and set pricing to "UNKNOWN" unless you are certain. Leave url empty unless you are sure of the official address.
""" + RULES

    private const val SYSTEM_QUERIES = """Turn the user's task into at most 3 short web search queries in English that would find the best current tools for it, including their pricing, free plan and limits. Output ONLY one JSON object, no markdown: {"queries":["query 1","query 2"]}"""

    private class Reply(val text: String, val queries: List<String>, val sources: List<SourceRef>)

    fun search(
        geminiKey: String,
        tavilyKey: String?,
        model: String,
        task: String,
        context: String
    ): AiResult {
        if (!MODEL_OK.matches(model)) throw AiException(tr("اسم الموديل غير صالح"))
        val langLine = if (I18n.lang == "ar") {
            "Write all text values in Egyptian Arabic colloquial."
        } else {
            "Write all text values in English."
        }
        val base = "$langLine\n\nTASK: $task\n\n$context"

        if (!tavilyKey.isNullOrBlank()) {
            try {
                return viaTavily(geminiKey, tavilyKey, model, task, base)
            } catch (e: AiException) {
                if (!e.limit) throw e
                return offline(geminiKey, model, base, tr("رصيد Tavily خلص الشهر ده، فالنتايج من غير بحث وكلها غير مؤكدة"))
            }
        }
        return try {
            val r = generate(geminiKey, model, SYSTEM_GROUNDED, base, useSearch = true)
            toResult(r, tr("الموديل ما بحثش على النت المرة دي، فكل المعلومات غير مؤكدة"))
        } catch (e: AiException) {
            if (e.code == 400 || e.code == 403 || e.code == 429) {
                offline(
                    geminiKey, model, base,
                    tr("بحث جوجل مش متاح في الخطة المجانية، فالنتايج من غير بحث وكلها غير مؤكدة. ضيف مفتاح Tavily من الإعدادات.")
                )
            } else {
                throw e
            }
        }
    }

    private fun viaTavily(geminiKey: String, tavilyKey: String, model: String, task: String, base: String): AiResult {
        val queries = makeQueries(geminiKey, model, task)
        val hits = LinkedHashMap<String, WebHit>()
        for (q in queries) {
            for (h in TavilyClient.search(tavilyKey, q)) {
                if (hits.size >= MAX_HITS) break
                if (!hits.containsKey(h.url)) hits[h.url] = h
            }
        }
        if (hits.isEmpty()) {
            return offline(geminiKey, model, base, tr("البحث ما رجّعش نتايج، فالنتايج من غير بحث وكلها غير مؤكدة"))
        }
        val list = hits.values.toList()
        val evidence = buildString {
            appendLine("SEARCH RESULTS:")
            list.forEachIndexed { i, h ->
                appendLine("[${i + 1}] ${h.title}")
                appendLine(h.url)
                appendLine(h.content.take(MAX_SNIPPET))
                appendLine()
            }
        }
        val r = generate(geminiKey, model, SYSTEM_RESULTS, "$base\n\n$evidence", useSearch = false)
        return toResult(
            Reply(r.text, queries, list.map { SourceRef(it.title, it.url) }),
            tr("الموديل ما بحثش على النت المرة دي، فكل المعلومات غير مؤكدة")
        )
    }

    /** Gemini بيطلّع جمل البحث. لو فشل، بنبحث بنص المهمة نفسه. */
    private fun makeQueries(geminiKey: String, model: String, task: String): List<String> {
        val fallback = listOf(task.take(200))
        return try {
            val r = generate(geminiKey, model, SYSTEM_QUERIES, "TASK: $task", useSearch = false)
            val arr = extractJson(r.text).optJSONArray("queries") ?: return fallback
            val q = (0 until arr.length()).map { arr.optString(it).trim() }.filter { it.isNotEmpty() }.take(3)
            if (q.isEmpty()) fallback else q
        } catch (e: AiException) {
            if (e.code != 0) throw e // مشكلة حقيقية في Gemini (مفتاح/حد): نوقف ونعرضها
            fallback
        }
    }

    private fun offline(geminiKey: String, model: String, base: String, note: String): AiResult {
        val r = generate(geminiKey, model, SYSTEM_OFFLINE, base, useSearch = false)
        return toResult(r, note)
    }

    private fun toResult(r: Reply, noSearchNote: String): AiResult {
        val obj = extractJson(r.text)
        return AiResult.parse(
            obj, r.sources, r.queries, System.currentTimeMillis(),
            tr("ما اتحققش بالبحث"), noSearchNote
        )
    }

    // ---------- استدعاء Gemini ----------
    private fun generate(apiKey: String, model: String, system: String, userText: String, useSearch: Boolean): Reply {
        val body = JSONObject().apply {
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", userText)))
                )
            )
            if (useSearch) put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
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
            if (code !in 200..299) throw AiException(errorMessage(code, text), code)
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

    private fun parseResponse(text: String): Reply {
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
        return Reply(sb.toString(), queries, sources)
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
