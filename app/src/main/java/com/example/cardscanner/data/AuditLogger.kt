package com.example.cardscanner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.cardscanner.security.LogSanitizer

/**
 * Append-only audit trail writer. Runs on its own; every repository mutation
 * and security-relevant action funnels through [log].
 *
 * Never logs card numbers — callers pass card IDs and status text only, and
 * detail strings pass through [LogSanitizer] as defense in depth.
 */
class AuditLogger(context: Context, private val dao: AuditDao) {

    private val appContext = context.applicationContext

    suspend fun log(event: String, cardId: String? = null, detail: String? = null) {
        dao.insert(
            AuditEntry(
                timestamp = System.currentTimeMillis(),
                event = event,
                cardId = cardId,
                detail = detail?.let { LogSanitizer.sanitize(it).take(200) },
            ),
        )
    }

    suspend fun logLogin() = log(AuditEntry.LOGIN)

    fun observeRecent(limit: Int = 200): kotlinx.coroutines.flow.Flow<List<AuditEntry>> =
        dao.observeRecent(limit)
}
