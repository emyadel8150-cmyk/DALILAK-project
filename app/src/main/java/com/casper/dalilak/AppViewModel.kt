package com.casper.dalilak

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.casper.dalilak.ai.AiException
import com.casper.dalilak.ai.AiResult
import com.casper.dalilak.ai.AiTool
import com.casper.dalilak.ai.GeminiClient
import com.casper.dalilak.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class Screen { Home, Results, Details, Prefs, History, Settings }

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val repo = Repository(db)
    val settings = AppSettings(app)

    var screen by mutableStateOf(Screen.Home)
    var task by mutableStateOf("")
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var info by mutableStateOf<String?>(null)

    var result by mutableStateOf<AiResult?>(null)
    var resultTask by mutableStateOf("")
    var detailTool by mutableStateOf<AiTool?>(null)

    var hasKey by mutableStateOf(settings.hasKey())
    var model by mutableStateOf(settings.model)
    var staleDays by mutableIntStateOf(settings.staleDays)

    val prefs: StateFlow<Map<String, String>> = db.prefs().observeAll()
        .map { list -> list.associate { it.prefKey to it.value } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val history: StateFlow<List<SearchEntity>> = db.searches().observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val choices: StateFlow<List<ChoiceEntity>> = db.choices().observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun isStale(at: Long): Boolean =
        at > 0 && System.currentTimeMillis() - at > staleDays * 86_400_000L

    fun back() {
        error = null
        screen = when (screen) {
            Screen.Details -> Screen.Results
            else -> Screen.Home
        }
    }

    fun open(s: Screen) {
        error = null
        info = null
        screen = s
    }

    // ---------- البحث ----------
    fun search() {
        val t = task.trim()
        if (t.isEmpty() || loading) return
        val key = settings.loadKey()
        if (key.isNullOrBlank()) {
            error = tr("ضيف مفتاح Gemini API من الإعدادات الأول")
            return
        }
        loading = true
        error = null
        viewModelScope.launch {
            try {
                val ctx = buildContext()
                val res = withContext(Dispatchers.IO) { GeminiClient.search(key, settings.model, t, ctx) }
                repo.saveResult(t, res)
                result = res
                resultTask = t
                screen = Screen.Results
            } catch (e: AiException) {
                error = e.message
            } catch (e: Exception) {
                error = tr("حصلت مشكلة في الاتصال") + ": " + (e.message ?: "")
            } finally {
                loading = false
            }
        }
    }

    /** اللي بيتبعت للـ AI: ملخص تفضيلات + أسماء أدوات بس. مفيش أي بيانات شخصية تانية. */
    private suspend fun buildContext(): String {
        val p = db.prefs().all().associate { it.prefKey to it.value }
        val ch = db.choices().all()
        val rejected = ch.filter { it.action == "REJECTED" }.map { it.toolName }.distinct().take(20)
        val worked = ch.filter { it.action == "CHOSE" && it.worked == 1 }.map { it.toolName }.distinct().take(20)
        val known = db.tools().recent(15).map { it.name }
        val na = "not specified"
        return buildString {
            appendLine("User preferences (summary):")
            appendLine("- budget: ${p["budget"].orEmpty().ifBlank { na }}")
            appendLine("- devices: ${p["devices"].orEmpty().ifBlank { na }}")
            appendLine("- experience: ${p["experience"].orEmpty().ifBlank { na }}")
            appendLine("- prefers free options when sufficient: ${if (p["prefer_free"] == "0") "no" else "yes"}")
            if (rejected.isNotEmpty()) appendLine("Previously rejected tools: ${rejected.joinToString(", ")}")
            if (worked.isNotEmpty()) appendLine("Tools that worked for the user before: ${worked.joinToString(", ")}")
            if (known.isNotEmpty()) appendLine("Tools already in local database (re-check if still best): ${known.joinToString(", ")}")
        }
    }

    fun openHistory(h: SearchEntity) {
        try {
            result = AiResult.fromStored(JSONObject(h.resultJson))
            resultTask = h.task
            error = null
            screen = Screen.Results
        } catch (e: Exception) {
            error = tr("مقدرتش أفتح النتيجة دي")
        }
    }

    fun openDetails(t: AiTool) {
        detailTool = t
        screen = Screen.Details
    }

    // ---------- الاختيارات والسجل ----------
    fun saveChoice(c: ChoiceEntity) {
        viewModelScope.launch { if (c.id == 0L) db.choices().insert(c) else db.choices().update(c) }
    }

    fun deleteChoice(c: ChoiceEntity) {
        viewModelScope.launch { db.choices().delete(c) }
    }

    fun deleteSearch(s: SearchEntity) {
        viewModelScope.launch { db.searches().delete(s) }
    }

    // ---------- التفضيلات ----------
    fun savePrefs(values: Map<String, String>) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            values.forEach { (k, v) -> db.prefs().upsert(PreferenceEntity(k, v.trim(), now)) }
            info = tr("اتحفظت")
        }
    }

    fun clearPrefs() {
        viewModelScope.launch {
            db.prefs().deleteAll()
            info = tr("اتمسحت")
        }
    }

    // ---------- الإعدادات ----------
    fun saveKey(k: String) {
        try {
            settings.saveKey(k)
            hasKey = settings.hasKey()
            info = tr("المفتاح اتحفظ مشفّر")
        } catch (e: Exception) {
            info = tr("فشل حفظ المفتاح")
        }
    }

    fun clearKey() {
        settings.clearKey()
        hasKey = false
        info = tr("المفتاح اتمسح")
    }

    fun saveModel(m: String) {
        val v = m.trim()
        if (v.isEmpty()) return
        settings.model = v
        model = v
        info = tr("اتحفظ")
    }

    fun saveStaleDays(d: Int) {
        val v = d.coerceIn(1, 365)
        settings.staleDays = v
        staleDays = v
        info = tr("اتحفظ")
    }

    fun setLang(l: String) {
        settings.lang = l
        I18n.lang = l
    }

    // ---------- الباكاب ----------
    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            info = try {
                withContext(Dispatchers.IO) {
                    val json = Backup.export(db)
                    val out = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                        ?: throw IllegalStateException("no stream")
                    out.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                }
                tr("اتصدّر الباكاب")
            } catch (e: Exception) {
                tr("فشل التصدير") + ": " + (e.message ?: "")
            }
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            info = try {
                val text = withContext(Dispatchers.IO) {
                    val input = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("no stream")
                    input.use { String(it.readBytes(), Charsets.UTF_8) }
                }
                tr("اتستورد") + ": " + Backup.import(db, text).text()
            } catch (e: Exception) {
                tr("فشل الاستيراد") + ": " + (e.message ?: "")
            }
        }
    }
}
