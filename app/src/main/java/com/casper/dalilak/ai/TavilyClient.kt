package com.casper.dalilak.ai

import com.casper.dalilak.tr
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class WebHit(val title: String, val url: String, val content: String)

/**
 * Tavily Search API (خطة مجانية 1000 رصيد/شهر، البحث العادي = 1 رصيد).
 * بيدوّر على النت ويرجّع عناوين + روابط + مقتطفات. استدعاء blocking: من Dispatchers.IO.
 */
object TavilyClient {
    fun search(apiKey: String, query: String, maxResults: Int = 5): List<WebHit> {
        val body = JSONObject()
            .put("query", query)
            .put("search_depth", "basic")
            .put("max_results", maxResults)
        val conn = URL("https://api.tavily.com/search").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""
            if (code !in 200..299) throw failure(code)
            return parse(text)
        } finally {
            conn.disconnect()
        }
    }

    private fun failure(code: Int): AiException = when (code) {
        401 -> AiException(tr("مفتاح Tavily مش صالح"), code)
        429, 432, 433 -> AiException(tr("رصيد Tavily خلص"), code, limit = true)
        else -> AiException(tr("حصل خطأ في Tavily") + " ($code)", code)
    }

    private fun parse(text: String): List<WebHit> {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw AiException(tr("الرد مش بصيغة مفهومة"))
        }
        val arr = root.optJSONArray("results") ?: return emptyList()
        val out = ArrayList<WebHit>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val url = AiTool.safeUrl(o.optString("url"))
            if (url.isEmpty()) continue
            out.add(WebHit(o.optString("title").trim().ifBlank { url }, url, o.optString("content").trim()))
        }
        return out
    }
}
