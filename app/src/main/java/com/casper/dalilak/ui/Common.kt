package com.casper.dalilak.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.casper.dalilak.ai.AiResult
import com.casper.dalilak.ai.AiTool
import com.casper.dalilak.data.ChoiceEntity
import com.casper.dalilak.tr
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DalilakTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Color(0xFF5EEAD4), onPrimary = Color(0xFF003731))
    } else {
        lightColorScheme(primary = Color(0xFF0F766E))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("رجوع"))
                        }
                    }
                },
                actions = actions
            )
        }
    ) { inner ->
        Box(
            Modifier
                .padding(inner)
                .consumeWindowInsets(inner)
                .imePadding()
                .fillMaxSize()
        ) { content() }
    }
}

fun fmtDate(ms: Long): String =
    if (ms <= 0) "-" else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ms))

fun pricingLabel(p: String): String = when (p) {
    "FREE" -> tr("مجاني")
    "FREEMIUM" -> tr("مجاني بحدود")
    "PAID" -> tr("مدفوع")
    else -> tr("السعر غير مؤكد")
}

fun Context.copyText(text: String) {
    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("dalilak", text))
    Toast.makeText(this, tr("اتنسخ"), Toast.LENGTH_SHORT).show()
}

fun Context.shareText(text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(send, null))
}

fun Context.openUrl(url: String) {
    if (!(url.startsWith("https://") || url.startsWith("http://"))) return
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        Toast.makeText(this, tr("مفيش برنامج يفتح الرابط"), Toast.LENGTH_SHORT).show()
    }
}

fun AiTool.asText(verifiedAt: Long): String = buildString {
    appendLine(name)
    appendLine("$type — ${pricingLabel(pricing)}")
    if (reason.isNotBlank()) appendLine("${tr("ليه")}: $reason")
    if (limitation.isNotBlank()) appendLine("${tr("القيد")}: $limitation")
    if (details.isNotBlank()) appendLine(details)
    if (verified.isNotEmpty()) appendLine("${tr("اتحقق منه")} (${fmtDate(verifiedAt)}): ${verified.joinToString("، ")}")
    if (unverified.isNotEmpty()) appendLine("${tr("غير مؤكد")}: ${unverified.joinToString("، ")}")
    if (url.isNotBlank()) appendLine(url)
}.trim()

fun AiResult.asText(task: String): String = buildString {
    appendLine(task)
    if (summary.isNotBlank()) appendLine(summary)
    appendLine()
    tools.forEach { appendLine(it.asText(verifiedAt)); appendLine() }
}.trim()

fun ChoiceEntity.asText(): String = buildString {
    appendLine("$toolName — ${if (action == "CHOSE") tr("اخترتها") else tr("رفضتها")}")
    appendLine("${tr("المهمة")}: $task")
    if (reason.isNotBlank()) appendLine("${tr("السبب")}: $reason")
    if (toolUrl.isNotBlank()) appendLine(toolUrl)
}.trim()

