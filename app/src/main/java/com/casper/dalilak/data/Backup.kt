package com.casper.dalilak.data

import androidx.room.withTransaction
import com.casper.dalilak.tr
import org.json.JSONArray
import org.json.JSONObject

data class ImportSummary(val tools: Int, val prefs: Int, val choices: Int, val searches: Int) {
    fun text(): String =
        "${tr("أدوات")}: $tools، ${tr("تفضيلات")}: $prefs، ${tr("اختيارات")}: $choices، ${tr("بحث")}: $searches"
}

/**
 * الباكاب: تصدير/استيراد كل الجداول في ملف JSON واحد.
 * مفتاح الـ API ما بيتضمّنش أبدًا. الاستيراد دمج (مفيش مسح لأي بيانات موجودة).
 */
object Backup {
    private const val APP = "dalilak"

    suspend fun export(db: AppDatabase): String {
        val tools = db.tools().all()
        val prefs = db.prefs().all()
        val choices = db.choices().all()
        val searches = db.searches().all()
        return JSONObject().apply {
            put("app", APP)
            put("dbVersion", 1)
            put("exportedAt", System.currentTimeMillis())
            put(
                "summary", JSONObject()
                    .put("tools", tools.size).put("preferences", prefs.size)
                    .put("choices", choices.size).put("searchHistory", searches.size)
            )
            put("tools", JSONArray().also { a -> tools.forEach { a.put(toolJ(it)) } })
            put("preferences", JSONArray().also { a -> prefs.forEach { a.put(prefJ(it)) } })
            put("choices", JSONArray().also { a -> choices.forEach { a.put(choiceJ(it)) } })
            put("searchHistory", JSONArray().also { a -> searches.forEach { a.put(searchJ(it)) } })
        }.toString(2)
    }

    suspend fun import(db: AppDatabase, text: String): ImportSummary {
        val root = JSONObject(text)
        if (root.optString("app") != APP) throw IllegalArgumentException(tr("الملف ده مش باكاب دليلك"))
        var nTools = 0
        var nPrefs = 0
        var nChoices = 0
        var nSearches = 0
        db.withTransaction {
            val tools = root.optJSONArray("tools") ?: JSONArray()
            for (i in 0 until tools.length()) {
                val o = tools.optJSONObject(i) ?: continue
                val k = toolKey(o.optString("name"))
                if (k.isEmpty()) continue
                val old = db.tools().byKey(k)
                val inc = toolFrom(o, k, old?.id ?: 0L)
                if (old == null) { db.tools().insert(inc); nTools++ }
                else if (inc.updatedAt > old.updatedAt) { db.tools().update(inc); nTools++ }
            }
            val prefs = root.optJSONArray("preferences") ?: JSONArray()
            for (i in 0 until prefs.length()) {
                val o = prefs.optJSONObject(i) ?: continue
                val k = o.optString("prefKey")
                if (k.isEmpty()) continue
                db.prefs().upsert(PreferenceEntity(k, o.optString("value"), o.optLong("updatedAt")))
                nPrefs++
            }
            val have = db.choices().all().map { Triple(it.createdAt, it.toolName, it.action) }.toHashSet()
            val choices = root.optJSONArray("choices") ?: JSONArray()
            for (i in 0 until choices.length()) {
                val o = choices.optJSONObject(i) ?: continue
                val c = choiceFrom(o)
                if (c.toolName.isEmpty() || !have.add(Triple(c.createdAt, c.toolName, c.action))) continue
                db.choices().insert(c)
                nChoices++
            }
            val haveS = db.searches().all().map { Pair(it.createdAt, it.task) }.toHashSet()
            val searches = root.optJSONArray("searchHistory") ?: JSONArray()
            for (i in 0 until searches.length()) {
                val o = searches.optJSONObject(i) ?: continue
                val s = SearchEntity(
                    0L, o.optString("task"), o.optString("resultJson"),
                    o.optInt("toolCount"), o.optLong("createdAt")
                )
                if (s.task.isEmpty() || !haveS.add(Pair(s.createdAt, s.task))) continue
                db.searches().insert(s)
                nSearches++
            }
        }
        return ImportSummary(nTools, nPrefs, nChoices, nSearches)
    }

    private fun toolJ(t: ToolEntity) = JSONObject().apply {
        put("name", t.name); put("type", t.type); put("pricing", t.pricing)
        put("reason", t.reason); put("limitation", t.limitation); put("url", t.url)
        put("details", t.details); put("verified", t.verified); put("unverified", t.unverified)
        put("sources", t.sources); put("verifiedAt", t.verifiedAt)
        put("createdAt", t.createdAt); put("updatedAt", t.updatedAt)
    }

    private fun toolFrom(o: JSONObject, key: String, id: Long) = ToolEntity(
        id = id, toolKey = key, name = o.optString("name"), type = o.optString("type"),
        pricing = o.optString("pricing", "UNKNOWN"), reason = o.optString("reason"),
        limitation = o.optString("limitation"), url = o.optString("url"),
        details = o.optString("details"), verified = o.optString("verified"),
        unverified = o.optString("unverified"), sources = o.optString("sources"),
        verifiedAt = o.optLong("verifiedAt"), createdAt = o.optLong("createdAt"),
        updatedAt = o.optLong("updatedAt")
    )

    private fun prefJ(p: PreferenceEntity) = JSONObject()
        .put("prefKey", p.prefKey).put("value", p.value).put("updatedAt", p.updatedAt)

    private fun choiceJ(c: ChoiceEntity) = JSONObject().apply {
        put("toolName", c.toolName); put("toolUrl", c.toolUrl); put("task", c.task)
        put("action", c.action); put("reason", c.reason); put("worked", c.worked)
        put("useAgain", c.useAgain); put("createdAt", c.createdAt)
    }

    private fun choiceFrom(o: JSONObject) = ChoiceEntity(
        id = 0L, toolName = o.optString("toolName"), toolUrl = o.optString("toolUrl"),
        task = o.optString("task"), action = o.optString("action", "CHOSE"),
        reason = o.optString("reason"), worked = o.optInt("worked", -1),
        useAgain = o.optInt("useAgain", -1), createdAt = o.optLong("createdAt")
    )

    private fun searchJ(s: SearchEntity) = JSONObject()
        .put("task", s.task).put("resultJson", s.resultJson)
        .put("toolCount", s.toolCount).put("createdAt", s.createdAt)
}
