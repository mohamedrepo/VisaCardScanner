package com.example.cardscanner.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Room entity mirroring [CardRecord].
 *
 * `scannedAt` is stored as epoch-millis: Room entities deliberately avoid
 * java.time types (the KSP resolver cannot see java.time on some toolchains),
 * and the conversion to [java.time.Instant] happens at the entity/model seam.
 *
 * SECURITY INVARIANT: there is deliberately no column capable of storing a
 * complete PAN. The unique index on (brand, last4, expiry) powers duplicate
 * detection without ever comparing full numbers.
 */
@Entity(
    tableName = "card_records",
    indices = [
        Index(value = ["brand", "last4", "expiryMonth", "expiryYear"], unique = true),
    ],
)
data class CardRecordEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "brand") val brand: String,
    @ColumnInfo(name = "maskedPan") val maskedPan: String,
    @ColumnInfo(name = "last4") val last4: String,
    @ColumnInfo(name = "expiryMonth") val expiryMonth: Int?,
    @ColumnInfo(name = "expiryYear") val expiryYear: Int?,
    @ColumnInfo(name = "cardholderName") val cardholderName: String?,
    @ColumnInfo(name = "scannedAt") val scannedAt: Long,
    @ColumnInfo(name = "notes") val notes: String? = null,
) {
    fun toModel(): CardRecord = CardRecord(
        id = id,
        brand = brand,
        maskedPan = maskedPan,
        last4 = last4,
        expiryMonth = expiryMonth,
        expiryYear = expiryYear,
        cardholderName = cardholderName,
        scannedAt = Instant.ofEpochMilli(scannedAt),
        notes = notes,
    )

    companion object {
        fun fromModel(model: CardRecord): CardRecordEntity = CardRecordEntity(
            id = model.id,
            brand = model.brand,
            maskedPan = model.maskedPan,
            last4 = model.last4,
            expiryMonth = model.expiryMonth,
            expiryYear = model.expiryYear,
            cardholderName = model.cardholderName,
            scannedAt = model.scannedAt.toEpochMilli(),
            notes = model.notes,
        )
    }
}
