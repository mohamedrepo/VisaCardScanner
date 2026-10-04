package com.example.cardscanner.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Immutable audit log entry (spec §24).
 *
 * SECURITY: card numbers (full, masked, or otherwise) are never written to the
 * log — entries reference the card by [cardId] only. The log is append-only:
 * the DAO exposes insert and query, never update or delete.
 */
@Entity(
    tableName = "audit_log",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["cardId"]),
        Index(value = ["event"]),
    ],
)
data class AuditEntry(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "event") val event: String,
    @ColumnInfo(name = "cardId") val cardId: String? = null,
    @ColumnInfo(name = "detail") val detail: String? = null,
) {
    companion object {
        // Event vocabulary (spec §24)
        const val LOGIN = "Login"
        const val CARD_SCANNED = "Card scanned"
        const val CARD_CREATED = "Card created"
        const val CARD_EDITED = "Card edited"
        const val PAN_REVEALED = "Full PAN revealed" // reserved: never emitted in masked edition
        const val CARD_DELETED = "Card deleted"
        const val EXCEL_EXPORTED = "Excel exported"
        const val CSV_EXPORTED = "CSV exported"
        const val CARD_REPLACED = "Card replaced"
        const val CARD_STATUS_CHANGED = "Card status changed"
        const val DATABASE_BACKUP = "Database backup"
        const val DATABASE_RESTORE = "Database restore"
    }
}
