package com.example.cardscanner.data

import com.example.cardscanner.security.PanSanitizer
import java.time.Instant
import java.util.UUID

/**
 * Builds a [FleetCard] from validated scanner output plus fleet fields.
 *
 * SECURITY: `TransientPan.consume()` yields masked + last4 only. `bin6` comes
 * from the OCR line prefix captured at validation time inside the scanner
 * module — the first six digits are the open network identifier (BIN) and are
 * safe to persist; the full number never reaches this factory.
 */
object FleetCardFactory {

    data class ScanInput(
        val maskedPan: String,
        val last4: String,
        val bin6: String,
        val cardholderName: String?,
        val expiryMonth: Int?,
        val expiryYear: Int?,
    )

    fun create(
        scan: ScanInput,
        fleet: FleetFields = FleetFields(),
    ): FleetCard {
        require(scan.last4.length == 4 && scan.last4.all { it.isDigit() })
        require(scan.bin6.length == 6 && scan.bin6.all { it.isDigit() })
        require(scan.maskedPan == PanSanitizer.mask(scan.last4))
        val now = Instant.now()
        return FleetCard(
            id = UUID.randomUUID().toString(),
            bin6 = scan.bin6,
            last4 = scan.last4,
            maskedPan = scan.maskedPan,
            cardBrand = "VISA",
            cardholderName = scan.cardholderName?.trim()?.ifEmpty { null },
            expiryMonth = scan.expiryMonth,
            expiryYear = scan.expiryYear,
            vehicleNumber = fleet.vehicleNumber?.trim()?.ifEmpty { null },
            plateNumber = fleet.plateNumber?.trim()?.ifEmpty { null },
            driverName = fleet.driverName?.trim()?.ifEmpty { null },
            employeeId = fleet.employeeId?.trim()?.ifEmpty { null },
            department = fleet.department?.trim()?.ifEmpty { null },
            fuelType = fleet.fuelType,
            provider = fleet.provider?.trim()?.ifEmpty { null },
            status = fleet.status,
            issueDate = fleet.issueDate?.trim()?.ifEmpty { null },
            replacementDate = fleet.replacementDate?.trim()?.ifEmpty { null },
            replacesCardId = fleet.replacesCardId,
            notes = fleet.notes?.trim()?.ifEmpty { null },
            createdAt = now,
            updatedAt = now,
        )
    }

    data class FleetFields(
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
    )
}
