package com.example.cardscanner.ui.confirmation

import com.example.cardscanner.data.AuditEntry
import com.example.cardscanner.data.CardStatus
import com.example.cardscanner.data.FleetCard
import com.example.cardscanner.data.FleetCardFactory
import com.example.cardscanner.data.FleetRepository
import com.example.cardscanner.data.AuditLogger
import com.example.cardscanner.ocr.OcrResult
import com.example.cardscanner.ui.scanner.ScannerViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Confirms, edits and saves the pending scan into the fleet database.
 *
 * v1.1.1 fix: the save path previously wrote to the legacy v1.0
 * `card_records` table while history read from `fleet_cards`, so scans
 * silently disappeared. Saving now creates a FleetCard via
 * [FleetCardFactory] (masked + BIN6 + last4) in the same table the
 * history screen reads.
 */
class ConfirmationViewModel(
    private val fleetRepository: FleetRepository,
    private val auditLogger: AuditLogger,
    private val scanner: ScannerViewModel,
    private val scope: CoroutineScope,
) {

    data class EditableFields(
        val cardholderName: String = "",
        val expiryText: String = "", // MM/YYYY
        val vehicleNumber: String = "",
        val plateNumber: String = "",
        val driverName: String = "",
        val department: String = "",
        val provider: String = "",
        val fuelType: String = "",
        val notes: String = "",
    )

    var editable by androidx.compose.runtime.mutableStateOf(EditableFields())
        private set

    var duplicateDetected by androidx.compose.runtime.mutableStateOf<FleetCard?>(null)
        private set

    var saved by androidx.compose.runtime.mutableStateOf<FleetCard?>(null)
        private set

    var saveError by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set

    fun beginEdit() {
        val result = scanner.pendingResult ?: return
        editable = EditableFields(
            cardholderName = result.cardholderName ?: "",
            expiryText = result.expiryMonth?.let { m ->
                result.expiryYear?.let { y ->
                    "%02d/%04d".format(m, y)
                }
            } ?: "",
        )
        saved = null
        saveError = null
    }

    fun onCardholderChanged(value: String) {
        editable = editable.copy(cardholderName = value.take(40))
    }

    fun onExpiryChanged(value: String) {
        editable = editable.copy(expiryText = value.take(7))
    }

    fun onVehicleChanged(value: String) {
        editable = editable.copy(vehicleNumber = value.take(24))
    }

    fun onPlateChanged(value: String) {
        editable = editable.copy(plateNumber = value.take(16))
    }

    fun onDriverChanged(value: String) {
        editable = editable.copy(driverName = value.take(40))
    }

    fun onDepartmentChanged(value: String) {
        editable = editable.copy(department = value.take(40))
    }

    fun onProviderChanged(value: String) {
        editable = editable.copy(provider = value.take(40))
    }

    fun onFuelTypeChanged(value: String) {
        editable = editable.copy(fuelType = value.take(20))
    }

    fun onNotesChanged(value: String) {
        editable = editable.copy(notes = value.take(200))
    }

    /** Parses MM/YYYY or MM/YY; returns null when invalid. */
    private fun parseExpiry(text: String): Pair<Int, Int>? {
        val trimmed = text.trim()
        val regex = Regex("""^(0[1-9]|1[0-2])[/\-](\d{2}|\d{4})$""")
        val match = regex.find(trimmed) ?: return null
        val month = match.groupValues[1].toInt()
        var year = match.groupValues[2].toInt()
        if (year < 100) year += 2000
        if (year !in 2020..2055) return null
        return month to year
    }

    fun isExpiryValid(text: String = editable.expiryText): Boolean =
        text.isBlank() || parseExpiry(text) != null

    /**
     * Confirm & Save into the fleet table. Duplicate check (BIN6 + last4 +
     * expiry) surfaces the existing record instead of writing when found.
     */
    fun confirmAndSave(acceptDuplicate: Boolean = false) {
        val result = scanner.pendingResult ?: return
        val safe = result.safeData ?: return

        val expiry = parseExpiry(editable.expiryText)
        if (!editable.expiryText.isBlank() && expiry == null) {
            saveError = "error_invalid_expiry"
            return
        }

        scope.launch {
            val duplicate = fleetRepository.findByCardKey(
                bin6 = safe.bin6,
                last4 = safe.last4,
                month = expiry?.first ?: result.expiryMonth,
                year = expiry?.second ?: result.expiryYear,
            )
            if (duplicate != null && !acceptDuplicate) {
                duplicateDetected = duplicate
                return@launch
            }
            try {
                val record: FleetCard = FleetCardFactory.create(
                    scan = FleetCardFactory.ScanInput(
                        maskedPan = safe.maskedPan,
                        last4 = safe.last4,
                        bin6 = safe.bin6,
                        cardholderName = editable.cardholderName.ifBlank { result.cardholderName },
                        expiryMonth = expiry?.first ?: result.expiryMonth,
                        expiryYear = expiry?.second ?: result.expiryYear,
                    ),
                    fleet = FleetCardFactory.FleetFields(
                        vehicleNumber = editable.vehicleNumber,
                        plateNumber = editable.plateNumber,
                        driverName = editable.driverName,
                        department = editable.department,
                        fuelType = editable.fuelType.ifEmpty { null },
                        provider = editable.provider,
                        status = CardStatus.ACTIVE,
                        notes = editable.notes,
                    ),
                )
                val stored = fleetRepository.create(record)
                saved = stored
                duplicateDetected = null
                scanner.beginScan()
            } catch (t: Throwable) {
                saveError = "error_generic"
            }
        }
    }

    fun dismissDuplicate() {
        duplicateDetected = null
    }

    fun consumeSaveError() {
        saveError = null
    }
}
