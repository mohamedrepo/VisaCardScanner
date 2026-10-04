package com.example.cardscanner.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Fleet-specific card status values (spec §12).
 */
object CardStatus {
    const val ACTIVE = "Active"
    const val SUSPENDED = "Suspended"
    const val LOST = "Lost"
    const val STOLEN = "Stolen"
    const val EXPIRED = "Expired"
    const val REPLACED = "Replacement"
    const val CANCELLED = "Cancelled"

    val ALL = listOf(ACTIVE, SUSPENDED, LOST, STOLEN, EXPIRED, REPLACED, CANCELLED)
}

/**
 * Fleet fuel type values (spec §12).
 */
object FuelType {
    const val GASOLINE_95 = "Gasoline 95"
    const val GASOLINE_92 = "Gasoline 92"
    const val DIESEL = "Diesel"
    const val MIXED = "Mixed"

    val ALL = listOf(GASOLINE_95, GASOLINE_92, DIESEL, MIXED)
}

/**
 * Room entity for a fleet fuel card record (masked edition).
 *
 * The card is identified by bin6 (first six digits — the open card-network
 * identifier used by fleet programs for routing) plus last4. The complete PAN
 * is never stored, displayed, or exported; there is deliberately no column that
 * could hold it.
 *
 * Indexes (spec §31) keep search fast at several-thousand-card scale.
 */
@Entity(
    tableName = "fleet_cards",
    indices = [
        Index(value = ["bin6", "last4", "expiryMonth", "expiryYear"], unique = true),
        Index(value = ["last4"]),
        Index(value = ["vehicleNumber"]),
        Index(value = ["plateNumber"]),
        Index(value = ["driverName"]),
        Index(value = ["employeeId"]),
        Index(value = ["provider"]),
        Index(value = ["status"]),
    ],
)
data class FleetCardEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "bin6") val bin6: String,
    @ColumnInfo(name = "last4") val last4: String,
    @ColumnInfo(name = "maskedPan") val maskedPan: String,
    @ColumnInfo(name = "cardBrand") val cardBrand: String,
    @ColumnInfo(name = "cardholderName") val cardholderName: String?,
    @ColumnInfo(name = "expiryMonth") val expiryMonth: Int?,
    @ColumnInfo(name = "expiryYear") val expiryYear: Int?,
    // Fleet fields (spec §12)
    @ColumnInfo(name = "vehicleNumber") val vehicleNumber: String? = null,
    @ColumnInfo(name = "plateNumber") val plateNumber: String? = null,
    @ColumnInfo(name = "driverName") val driverName: String? = null,
    @ColumnInfo(name = "employeeId") val employeeId: String? = null,
    @ColumnInfo(name = "department") val department: String? = null,
    @ColumnInfo(name = "fuelType") val fuelType: String? = null,
    @ColumnInfo(name = "provider") val provider: String? = null,
    @ColumnInfo(name = "status") val status: String = CardStatus.ACTIVE,
    @ColumnInfo(name = "issueDate") val issueDate: String? = null,
    @ColumnInfo(name = "replacementDate") val replacementDate: String? = null,
    @ColumnInfo(name = "replacesCardId") val replacesCardId: String? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "createdAt") val createdAt: Long,
    @ColumnInfo(name = "updatedAt") val updatedAt: Long,
) {
    companion object {
        fun fromModel(m: FleetCard): FleetCardEntity = FleetCardEntity(
            id = m.id,
            bin6 = m.bin6,
            last4 = m.last4,
            maskedPan = m.maskedPan,
            cardBrand = m.cardBrand,
            cardholderName = m.cardholderName,
            expiryMonth = m.expiryMonth,
            expiryYear = m.expiryYear,
            vehicleNumber = m.vehicleNumber,
            plateNumber = m.plateNumber,
            driverName = m.driverName,
            employeeId = m.employeeId,
            department = m.department,
            fuelType = m.fuelType,
            provider = m.provider,
            status = m.status,
            issueDate = m.issueDate,
            replacementDate = m.replacementDate,
            replacesCardId = m.replacesCardId,
            notes = m.notes,
            createdAt = m.createdAt.toEpochMilli(),
            updatedAt = m.updatedAt.toEpochMilli(),
        )
    }

    fun toModel(): FleetCard = FleetCard(
        id = id,
        bin6 = bin6,
        last4 = last4,
        maskedPan = maskedPan,
        cardBrand = cardBrand,
        cardholderName = cardholderName,
        expiryMonth = expiryMonth,
        expiryYear = expiryYear,
        vehicleNumber = vehicleNumber,
        plateNumber = plateNumber,
        driverName = driverName,
        employeeId = employeeId,
        department = department,
        fuelType = fuelType,
        provider = provider,
        status = status,
        issueDate = issueDate,
        replacementDate = replacementDate,
        replacesCardId = replacesCardId,
        notes = notes,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
    )
}
