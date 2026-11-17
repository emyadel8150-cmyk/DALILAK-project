package com.casper.dalilak.data

import androidx.room.withTransaction
import com.casper.dalilak.ai.AiResult
import java.util.Locale

fun toolKey(name: String): String = name.trim().lowercase(Locale.ROOT)

class Repository(private val db: AppDatabase) {

    /** بيحفظ الأدوات في القاعدة الداخلية (upsert) ونتيجة البحث في السجل. */
    suspend fun saveResult(task: String, res: AiResult) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            for (t in res.tools) {
                val k = toolKey(t.name)
                if (k.isEmpty()) continue
                val old = db.tools().byKey(k)
                // تاريخ التحقق بيتسجل بس لو في معلومات اتأكدت فعلًا في البحث ده
                val verifiedAt = if (t.verified.isNotEmpty()) res.verifiedAt else (old?.verifiedAt ?: 0L)
                val e = ToolEntity(
                    id = old?.id ?: 0L,
                    toolKey = k,
                    name = t.name,
                    type = t.type,
                    pricing = t.pricing,
                    reason = t.reason,
                    limitation = t.limitation,
                    url = t.url,
                    details = t.details,
                    verified = t.verified.joinToString("\n"),
                    unverified = t.unverified.joinToString("\n"),
                    sources = res.sources.joinToString("\n") { it.uri },
                    verifiedAt = verifiedAt,
                    createdAt = old?.createdAt ?: now,
                    updatedAt = now
                )
                if (old == null) db.tools().insert(e) else db.tools().update(e)
            }
            db.searches().insert(
                SearchEntity(
                    task = task,
                    resultJson = res.toJson().toString(),
                    toolCount = res.tools.size,
                    createdAt = now
                )
            )
        }
    }
}
