package com.example.cardscanner.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {

    @Query("SELECT * FROM card_records ORDER BY scannedAt DESC")
    fun observeAll(): Flow<List<CardRecordEntity>>

    @Query(
        """
        SELECT * FROM card_records
        WHERE (:query = ''
               OR last4 LIKE '%' || :query || '%'
               OR UPPER(cardholderName) LIKE '%' || UPPER(:query) || '%'
               OR CAST(expiryYear AS TEXT) = :query
               OR UPPER(brand) LIKE '%' || UPPER(:query) || '%')
        ORDER BY scannedAt DESC
        """
    )
    fun search(query: String): Flow<List<CardRecordEntity>>

    /** Duplicate detection: Brand + Last4 + Expiry. */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM card_records
            WHERE brand = :brand
              AND last4 = :last4
              AND ((expiryMonth IS NULL AND :expiryMonth IS NULL) OR expiryMonth = :expiryMonth)
              AND ((expiryYear IS NULL AND :expiryYear IS NULL) OR expiryYear = :expiryYear)
        )
        """
    )
    suspend fun existsDuplicate(
        brand: String,
        last4: String,
        expiryMonth: Int?,
        expiryYear: Int?,
    ): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: CardRecordEntity)

    @Update
    suspend fun update(record: CardRecordEntity)

    @Delete
    suspend fun delete(record: CardRecordEntity)

    @Query("DELETE FROM card_records")
    suspend fun deleteAll()

    @Query("SELECT * FROM card_records ORDER BY scannedAt DESC")
    suspend fun getAllOnce(): List<CardRecordEntity>

    @Query("SELECT COUNT(*) FROM card_records")
    suspend fun count(): Int
}
