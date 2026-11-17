package com.casper.dalilak.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.casper.dalilak.AppViewModel
import com.casper.dalilak.I18n
import com.casper.dalilak.Screen
import com.casper.dalilak.ai.AiTool
import com.casper.dalilak.data.ChoiceEntity
import com.casper.dalilak.tr

@Composable
fun DalilakApp(vm: AppViewModel) {
    val dir = if (I18n.lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
    DalilakTheme {
        CompositionLocalProvider(LocalLayoutDirection provides dir) {
            BackHandler(enabled = vm.screen != Screen.Home) { vm.back() }
            when (vm.screen) {
                Screen.Home -> HomeScreen(vm)
                Screen.Results -> ResultsScreen(vm)
                Screen.Details -> DetailsScreen(vm)
                Screen.Prefs -> PrefsScreen(vm)
                Screen.History -> HistoryScreen(vm)
                Screen.Settings -> SettingsScreen(vm)
            }
        }
    }
}

// ============ الرئيسية ============
@Composable
fun HomeScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val history by vm.history.collectAsState()
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val text = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (r.resultCode == Activity.RESULT_OK && !text.isNullOrBlank()) {
            vm.task = (vm.task + " " + text).trim()
        }
    }
    AppScaffold(
        title = tr("دليلك"),
        onBack = null,
        actions = {
            IconButton(onClick = { vm.open(Screen.Settings) }) {
                Icon(Icons.Default.Settings, contentDescription = tr("الإعدادات"))
            }
        }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(tr("عاوز تعمل إيه؟"), style = MaterialTheme.typography.headlineSmall)
            if (!vm.hasKey) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tr("لازم تضيف مفتاح Gemini API الأول"))
                        Button(onClick = { vm.open(Screen.Settings) }) { Text(tr("افتح الإعدادات")) }
                    }
                }
            }
            OutlinedTextField(
                value = vm.task,
                onValueChange = { vm.task = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                label = { Text(tr("اكتب المهمة")) },
                placeholder = { Text(tr("مثال: عاوز أحول صور المنتجات لفيديو")) }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { vm.search() },
                    enabled = vm.task.isNotBlank() && !vm.loading,
                    modifier = Modifier.weight(1f)
                ) { Text(tr("دوّر")) }
                OutlinedButton(onClick = {
                    val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (I18n.lang == "ar") "ar-EG" else "en-US")
                    try {
                        mic.launch(i)
                    } catch (e: ActivityNotFoundException) {
                        vm.error = tr("مفيش خدمة تعرّف على الصوت على الموبايل")
                    }
                }) { Text("🎤") }
            }
            if (vm.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(tr("بدوّر على النت وبتحقق من المعلومات..."))
            }
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.open(Screen.Prefs) }) { Text(tr("تفضيلاتي")) }
                OutlinedButton(onClick = { vm.open(Screen.History) }) { Text(tr("السجل")) }
            }
            if (history.isNotEmpty()) {
                Text(tr("آخر عمليات البحث"), style = MaterialTheme.typography.titleMedium)
                history.take(5).forEach { h ->
                    Card(Modifier.fillMaxWidth().clickable { vm.openHistory(h) }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(h.task, maxLines = 2)
                            Text(
                                fmtDate(h.createdAt) + " • " + h.toolCount + " " + tr("نتيجة"),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============ كارت أداة ============
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolCard(
    vm: AppViewModel,
    tool: AiTool,
    preferred: Boolean,
    verifiedAt: Long,
    showDetailsButton: Boolean,
    onChoice: (AiTool) -> Unit
) {
    val ctx = LocalContext.current
    val stale = vm.isStale(verifiedAt)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tool.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (preferred) Text("⭐ " + tr("المفضلة"), color = MaterialTheme.colorScheme.primary)
            }
            Text(
                (if (tool.type.isNotBlank()) tool.type + " • " else "") + pricingLabel(tool.pricing),
                style = MaterialTheme.typography.bodyMedium
            )
            if (tool.reason.isNotBlank()) Text(tr("ليه") + ": " + tool.reason)
            if (tool.limitation.isNotBlank()) Text(tr("القيد") + ": " + tool.limitation)
            if (tool.verified.isNotEmpty()) {
                Text(
                    "✓ " + tr("اتحقق منه") + ": " + fmtDate(verifiedAt) + if (stale) " (" + tr("قديمة") + ")" else "",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (tool.unverified.isNotEmpty()) {
                Text(
                    "⚠ " + tr("غير مؤكد") + ": " + tool.unverified.joinToString("، "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (tool.url.isNotBlank()) TextButton(onClick = { ctx.openUrl(tool.url) }) { Text(tr("افتح الرابط")) }
                if (showDetailsButton) TextButton(onClick = { vm.openDetails(tool) }) { Text(tr("التفاصيل")) }
                TextButton(onClick = { onChoice(tool) }) { Text(tr("اخترت / رفضت")) }
                TextButton(onClick = { ctx.copyText(tool.asText(verifiedAt)) }) { Text(tr("نسخ")) }
                TextButton(onClick = { ctx.shareText(tool.asText(verifiedAt)) }) { Text(tr("شير")) }
            }
        }
    }
}

private fun newChoice(vm: AppViewModel, t: AiTool) = ChoiceEntity(
    toolName = t.name, toolUrl = t.url, task = vm.resultTask, action = "CHOSE",
    reason = "", worked = -1, useAgain = -1, createdAt = System.currentTimeMillis()
)

// ============ النتايج ============
@Composable
fun ResultsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val res = vm.result
    var dialog by remember { mutableStateOf<ChoiceEntity?>(null) }
    var showSources by remember { mutableStateOf(false) }
    AppScaffold(
        title = tr("النتايج"),
        onBack = { vm.back() },
        actions = {
            if (res != null) {
                TextButton(onClick = { ctx.copyText(res.asText(vm.resultTask)) }) { Text(tr("نسخ الكل")) }
                TextButton(onClick = { ctx.shareText(res.asText(vm.resultTask)) }) { Text(tr("شير")) }
            }
        }
    ) {
        if (res == null) {
            Text(tr("مفيش نتيجة"), Modifier.padding(16.dp))
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Text(vm.resultTask, style = MaterialTheme.typography.titleMedium) }
                if (vm.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                vm.error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error) } }
                if (vm.isStale(res.verifiedAt)) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(tr("النتيجة دي قديمة، والأسعار والحدود ممكن تكون اتغيرت") + " (" + fmtDate(res.verifiedAt) + ")")
                                Button(
                                    onClick = { vm.task = vm.resultTask; vm.search() },
                                    enabled = !vm.loading
                                ) { Text(tr("حدّث البحث")) }
                            }
                        }
                    }
                }
                if (res.summary.isNotBlank()) item { Text(res.summary) }
                if (res.tools.isEmpty()) item { Text(tr("ملقيتش أداة مناسبة ومؤكدة")) }
                items(res.tools) { t ->
                    ToolCard(
                        vm, t,
                        preferred = t.name.equals(res.preferred, ignoreCase = true),
                        verifiedAt = if (t.verified.isNotEmpty()) res.verifiedAt else 0L,
                        showDetailsButton = true,
                        onChoice = { dialog = newChoice(vm, it) }
                    )
                }
                if (res.note.isNotBlank()) item { Text(res.note, style = MaterialTheme.typography.bodySmall) }
                if (res.searchQueries.isNotEmpty()) {
                    item {
                        Text(
                            tr("اتبحث عن") + ": " + res.searchQueries.joinToString("، "),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (res.sources.isNotEmpty()) {
                    item {
                        TextButton(onClick = { showSources = !showSources }) {
                            Text(tr("المصادر") + " (" + res.sources.size + ")")
                        }
                    }
                    if (showSources) {
                        items(res.sources) { s ->
                            Text(
                                s.title,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { ctx.openUrl(s.uri) }
                            )
                        }
                    }
                }
            }
        }
    }
    dialog?.let { d ->
        ChoiceDialog(d, onDismiss = { dialog = null }, onSave = { vm.saveChoice(it); dialog = null })
    }
}

// ============ التفاصيل ============
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val tool = vm.detailTool
    val choices by vm.choices.collectAsState()
    var dialog by remember { mutableStateOf<ChoiceEntity?>(null) }
    AppScaffold(title = tool?.name ?: tr("التفاصيل"), onBack = { vm.back() }) {
        if (tool == null) {
            Text(tr("مفيش نتيجة"), Modifier.padding(16.dp))
        } else {
            val verifiedAt = if (tool.verified.isNotEmpty()) (vm.result?.verifiedAt ?: 0L) else 0L
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    (if (tool.type.isNotBlank()) tool.type + " • " else "") + pricingLabel(tool.pricing),
                    style = MaterialTheme.typography.titleMedium
                )
                if (tool.reason.isNotBlank()) Text(tr("ليه") + ": " + tool.reason)
                if (tool.limitation.isNotBlank()) Text(tr("القيد") + ": " + tool.limitation)
                if (tool.details.isNotBlank()) Text(tool.details)
                if (tool.verified.isNotEmpty()) {
                    Text(
                        tr("اتحقق منه") + " (" + fmtDate(verifiedAt) + if (vm.isStale(verifiedAt)) " - " + tr("قديمة") + "):" else "):",
                        fontWeight = FontWeight.Bold
                    )
                    tool.verified.forEach { Text("✓ $it") }
                }
                if (tool.unverified.isNotEmpty()) {
                    Text(tr("غير مؤكد") + ":", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    tool.unverified.forEach { Text("⚠ $it") }
                }
                if (tool.url.isNotBlank()) {
                    Text(tool.url, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { ctx.openUrl(tool.url) })
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (tool.url.isNotBlank()) Button(onClick = { ctx.openUrl(tool.url) }) { Text(tr("افتح الرابط")) }
                    OutlinedButton(onClick = { dialog = newChoice(vm, tool) }) { Text(tr("اخترت / رفضت")) }
                    OutlinedButton(onClick = { ctx.copyText(tool.asText(verifiedAt)) }) { Text(tr("نسخ")) }
                    OutlinedButton(onClick = { ctx.shareText(tool.asText(verifiedAt)) }) { Text(tr("شير")) }
                }
                val mine = choices.filter { it.toolName.equals(tool.name, ignoreCase = true) }
                if (mine.isNotEmpty()) {
                    HorizontalDivider()
                    Text(tr("اختياراتك السابقة"), style = MaterialTheme.typography.titleSmall)
                    mine.forEach { c ->
                        Text("• " + (if (c.action == "CHOSE") tr("اخترتها") else tr("رفضتها")) + " (" + fmtDate(c.createdAt) + ")" +
                            if (c.reason.isNotBlank()) ": " + c.reason else "")
                    }
                }
            }
        }
    }
    dialog?.let { d ->
        ChoiceDialog(d, onDismiss = { dialog = null }, onSave = { vm.saveChoice(it); dialog = null })
    }
}

// ============ نافذة اخترت / رفضت ============
@Composable
fun ChoiceDialog(initial: ChoiceEntity, onDismiss: () -> Unit, onSave: (ChoiceEntity) -> Unit) {
    var action by remember { mutableStateOf(initial.action) }
    var reason by remember { mutableStateOf(initial.reason) }
    var worked by remember { mutableIntStateOf(initial.worked) }
    var again by remember { mutableIntStateOf(initial.useAgain) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(initial.toolName) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = action == "CHOSE", onClick = { action = "CHOSE" }, label = { Text(tr("اخترتها")) })
                    FilterChip(selected = action == "REJECTED", onClick = { action = "REJECTED" }, label = { Text(tr("رفضتها")) })
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text(tr("السبب")) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(tr("نجحت معاك؟"))
                TriChips(worked) { worked = it }
                Text(tr("تستخدمها تاني؟"))
                TriChips(again) { again = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(initial.copy(action = action, reason = reason.trim(), worked = worked, useAgain = again))
            }) { Text(tr("حفظ")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("إلغاء")) } }
    )
}

@Composable
fun TriChips(value: Int, onChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1 to tr("آه"), 0 to tr("لأ"), -1 to tr("لسه")).forEach { (k, label) ->
            FilterChip(selected = value == k, onClick = { onChange(k) }, label = { Text(label) })
        }
    }
}

@Composable
fun ConfirmDialog(text: String, onYes: () -> Unit, onNo: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNo,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onYes) { Text(tr("تمام")) } },
        dismissButton = { TextButton(onClick = onNo) { Text(tr("إلغاء")) } }
    )
}

// ============ تفضيلاتي ============
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrefsScreen(vm: AppViewModel) {
    val prefs by vm.prefs.collectAsState()
    var budget by remember(prefs) { mutableStateOf(prefs["budget"] ?: "") }
    var devices by remember(prefs) { mutableStateOf(prefs["devices"] ?: "") }
    var exp by remember(prefs) { mutableStateOf(prefs["experience"] ?: "") }
    var free by remember(prefs) { mutableStateOf(prefs["prefer_free"] != "0") }
    var confirm by remember { mutableStateOf(false) }
    AppScaffold(title = tr("تفضيلاتي"), onBack = { vm.back() }) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                tr("الميزانية والأجهزة والخبرة بتتبعت مع كل بحث عشان الترشيح يناسبك"),
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = budget, onValueChange = { budget = it },
                label = { Text(tr("الميزانية")) },
                placeholder = { Text(tr("مثال: مجاني أو لحد 10 دولار في الشهر")) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = devices, onValueChange = { devices = it },
                label = { Text(tr("الأجهزة والمنصات")) },
                placeholder = { Text(tr("مثال: موبايل أندرويد + لاب ويندوز")) },
                modifier = Modifier.fillMaxWidth()
            )
            Text(tr("مستوى الخبرة"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("beginner" to tr("مبتدئ"), "intermediate" to tr("متوسط"), "expert" to tr("خبير")).forEach { (k, l) ->
                    FilterChip(selected = exp == k, onClick = { exp = if (exp == k) "" else k }, label = { Text(l) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("أفضّل المجاني لما يكفي"), Modifier.weight(1f))
                Switch(checked = free, onCheckedChange = { free = it })
            }
            Button(
                onClick = {
                    vm.savePrefs(mapOf("budget" to budget, "devices" to devices, "experience" to exp, "prefer_free" to if (free) "1" else "0"))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(tr("حفظ")) }
            OutlinedButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) {
                Text(tr("مسح كل التفضيلات"))
            }
            vm.info?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
    if (confirm) {
        ConfirmDialog(tr("تمسح كل التفضيلات؟"), onYes = { vm.clearPrefs(); confirm = false }, onNo = { confirm = false })
    }
}

// ============ السجل ============
@Composable
fun HistoryScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val history by vm.history.collectAsState()
    val choices by vm.choices.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var q by remember { mutableStateOf("") }
    var edit by remember { mutableStateOf<ChoiceEntity?>(null) }
    var confirm by remember { mutableStateOf<(() -> Unit)?>(null) }
    AppScaffold(title = tr("السجل"), onBack = { vm.back() }) {
        Column(Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(tr("عمليات البحث")) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(tr("اختياراتي")) })
            }
            OutlinedTextField(
                value = q, onValueChange = { q = it },
                label = { Text(tr("بحث")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            )
            if (tab == 0) {
                val list = history.filter { q.isBlank() || it.task.contains(q, ignoreCase = true) }
                if (list.isEmpty()) Text(tr("مفيش حاجة هنا لسه"), Modifier.padding(16.dp))
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(list, key = { it.id }) { h ->
                        Card(Modifier.fillMaxWidth().clickable { vm.openHistory(h) }) {
                            Column(Modifier.padding(12.dp)) {
                                Text(h.task, fontWeight = FontWeight.Bold)
                                Text(fmtDate(h.createdAt) + " • " + h.toolCount + " " + tr("نتيجة"), style = MaterialTheme.typography.bodySmall)
                                Row {
                                    TextButton(onClick = { ctx.copyText(h.task) }) { Text(tr("نسخ")) }
                                    TextButton(onClick = { ctx.shareText(h.task) }) { Text(tr("شير")) }
                                    Spacer(Modifier.weight(1f))
                                    IconButton(onClick = { confirm = { vm.deleteSearch(h) } }) {
                                        Icon(Icons.Default.Delete, contentDescription = tr("مسح"))
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val list = choices.filter {
                    q.isBlank() || it.toolName.contains(q, true) || it.task.contains(q, true) || it.reason.contains(q, true)
                }
                if (list.isEmpty()) Text(tr("مفيش حاجة هنا لسه"), Modifier.padding(16.dp))
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(list, key = { it.id }) { c ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    c.toolName + " — " + (if (c.action == "CHOSE") tr("اخترتها") else tr("رفضتها")),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(tr("المهمة") + ": " + c.task, style = MaterialTheme.typography.bodySmall)
                                if (c.reason.isNotBlank()) Text(tr("السبب") + ": " + c.reason)
                                Text(
                                    tr("نجحت معاك؟") + " " + triLabel(c.worked) + " • " + tr("تستخدمها تاني؟") + " " + triLabel(c.useAgain),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(fmtDate(c.createdAt), style = MaterialTheme.typography.bodySmall)
                                Row {
                                    TextButton(onClick = { edit = c }) { Text(tr("تعديل")) }
                                    TextButton(onClick = { ctx.copyText(c.asText()) }) { Text(tr("نسخ")) }
                                    TextButton(onClick = { ctx.shareText(c.asText()) }) { Text(tr("شير")) }
                                    Spacer(Modifier.weight(1f))
                                    IconButton(onClick = { confirm = { vm.deleteChoice(c) } }) {
                                        Icon(Icons.Default.Delete, contentDescription = tr("مسح"))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    edit?.let { e -> ChoiceDialog(e, onDismiss = { edit = null }, onSave = { vm.saveChoice(it); edit = null }) }
    confirm?.let { act ->
        ConfirmDialog(tr("تمسح العنصر ده؟"), onYes = { act(); confirm = null }, onNo = { confirm = null })
    }
}

private fun triLabel(v: Int): String = when (v) {
    1 -> tr("آه")
    0 -> tr("لأ")
    else -> tr("لسه")
}

// ============ الإعدادات ============
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var key by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(vm.model) }
    var stale by remember { mutableStateOf(vm.staleDays.toString()) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportTo(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFrom(uri)
    }
    AppScaffold(title = tr("الإعدادات"), onBack = { vm.back() }) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(tr("مفتاح Gemini API"), style = MaterialTheme.typography.titleMedium)
            Text(
                if (vm.hasKey) "✓ " + tr("المفتاح محفوظ ومشفّر على الجهاز") else tr("مفيش مفتاح محفوظ"),
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = key, onValueChange = { key = it },
                label = { Text(tr("الصق المفتاح هنا")) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.saveKey(key); key = "" }, enabled = key.isNotBlank()) { Text(tr("حفظ المفتاح")) }
                OutlinedButton(onClick = { vm.clearKey() }, enabled = vm.hasKey) { Text(tr("مسح المفتاح")) }
                TextButton(onClick = { ctx.openUrl("https://aistudio.google.com/apikey") }) { Text(tr("هات مفتاح")) }
            }
            Text(
                tr("المفتاح بيفضل على موبايلك بس، وما بيتحطش في الباكاب. المستوى المجاني ممكن جوجل تستخدم محتواه، فمتكتبش بيانات شخصية في المهام."),
                style = MaterialTheme.typography.bodySmall
            )
            HorizontalDivider()
            OutlinedTextField(
                value = model, onValueChange = { model = it },
                label = { Text(tr("اسم الموديل")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { vm.saveModel(model) }) { Text(tr("حفظ الموديل")) }
            OutlinedTextField(
                value = stale, onValueChange = { stale = it.filter { c -> c.isDigit() }.take(3) },
                label = { Text(tr("المعلومة تعتبر قديمة بعد (أيام)")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { vm.saveStaleDays(stale.toIntOrNull() ?: 30) }) { Text(tr("حفظ")) }
            HorizontalDivider()
            Text(tr("اللغة"), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = I18n.lang == "ar", onClick = { vm.setLang("ar") }, label = { Text("العربية") })
                FilterChip(selected = I18n.lang == "en", onClick = { vm.setLang("en") }, label = { Text("English") })
            }
            HorizontalDivider()
            Text(tr("الباكاب"), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exportLauncher.launch("dalilak-backup.json") }) { Text(tr("تصدير")) }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text(tr("استيراد")) }
            }
            vm.info?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Text(tr("دليلك") + " v1.0", style = MaterialTheme.typography.bodySmall)
        }
    }
}
