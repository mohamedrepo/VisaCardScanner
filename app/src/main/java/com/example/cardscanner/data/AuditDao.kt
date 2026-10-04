package com.example.cardscanner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Append-only audit DAO. No update or delete methods exist by design — the
 * audit log is immutable once written.
 */
@Dao
interface AuditDao {

    @Insert
    suspend fun insert(entry: AuditEntry)

    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    fun observeRecent(limit: Int = 200, offset: Int = 0): Flow<List<AuditEntry>>

    @Query("SELECT * FROM audit_log WHERE cardId = :cardId ORDER BY timestamp DESC")
    fun observeForCard(cardId: String): Flow<List<AuditEntry>>

    @Query("SELECT COUNT(*) FROM audit_log")
    suspend fun count(): Int
}
