package com.example.cardscanner.data

import java.time.Instant

/**
 * The only persistent representation of a scanned card.
 *
 * SECURITY INVARIANT: this class has no field capable of holding a complete PAN.
 * `maskedPan` and `last4` are derived inside the scanner module and the complete
 * number is destroyed before an instance of this class can be created.
 */
data class CardRecord(
    val id: String,
    val brand: String,
    val maskedPan: String,
    val last4: String,
    val expiryMonth: Int?,
    val expiryYear: Int?,
    val cardholderName: String?,
    val scannedAt: Instant,
    val notes: String? = null,
)
