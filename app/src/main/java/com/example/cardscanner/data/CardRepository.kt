package com.example.cardscanner.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID

/**
 * Local repository. Exclusively handles SAFE card records (masked PAN + last 4).
 * There is no method on this class that could accept a complete PAN.
 */
class CardRepository(private val dao: CardDao) {

    fun observeAll(): Flow<List<CardRecord>> = dao.observeAll().mapSafe()

    fun search(query: String): Flow<List<CardRecord>> = dao.search(query).mapSafe()

    private fun Flow<List<CardRecordEntity>>.mapSafe(): Flow<List<CardRecord>> =
        map { list -> list.map { it.toModel() } }

    /** Returns true when Brand + Last4 + Expiry already exists. */
    suspend fun isDuplicate(brand: String, last4: String, month: Int?, year: Int?): Boolean =
        dao.existsDuplicate(brand, last4, month, year)

    /**
     * Persists a new record. `brand` must be a validated brand constant and
     * `last4` exactly the four digits produced by the scanner pipeline.
     */
    suspend fun save(
        brand: String,
        last4: String,
        maskedPan: String,
        month: Int?,
        year: Int?,
        cardholder: String?,
        notes: String? = null,
    ): CardRecord {
        require(last4.length == 4 && last4.all { it.isDigit() }) {
            "last4 must be exactly 4 digits"
        }
        val record = CardRecord(
            id = UUID.randomUUID().toString(),
            brand = brand,
            maskedPan = maskedPan,
            last4 = last4,
            expiryMonth = month,
            expiryYear = year,
            cardholderName = cardholder?.trim()?.takeIf { it.isNotEmpty() },
            scannedAt = Instant.now(),
            notes = notes?.trim()?.takeIf { it.isNotEmpty() },
        )
        dao.insert(CardRecordEntity.fromModel(record))
        return record
    }

    suspend fun update(model: CardRecord) {
        dao.update(CardRecordEntity.fromModel(model))
    }

    suspend fun delete(model: CardRecord) {
        dao.delete(CardRecordEntity.fromModel(model))
    }

    suspend fun allForExport(): List<CardRecord> =
        dao.getAllOnce().map { it.toModel() }
}
