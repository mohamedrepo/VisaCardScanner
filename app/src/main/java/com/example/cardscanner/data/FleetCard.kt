package com.example.cardscanner.data

import java.time.Instant

/**
 * Domain model for a fleet fuel card (masked edition).
 *
 * SECURITY INVARIANT: no field can hold a complete PAN. `bin6` (first six
 * digits — the open network identifier) plus `last4` identify the card.
 */
data class FleetCard(
    val id: String,
    val bin6: String,
    val last4: String,
    val maskedPan: String,
    val cardBrand: String,
    val cardholderName: String?,
    val expiryMonth: Int?,
    val expiryYear: Int?,
    val vehicleNumber: String? = null,
    val plateNumber: String? = null,
    val driverName: String? = null,
    val employeeId: String? = null,
    val department: String? = null,
    val fuelType: String? = null,
    val provider: String? = null,
    val status: String = CardStatus.ACTIVE,
    val issueDate: String? = null,
    val replacementDate: String? = null,
    val replacesCardId: String? = null,
    val notes: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /** Fleet-standard display form: BIN + last 4, e.g. `401288 •••• 1234`. */
    val binDisplay: String
        get() = "$bin6 •••• $last4"
}
