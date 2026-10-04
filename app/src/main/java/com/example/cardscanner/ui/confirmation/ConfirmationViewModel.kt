package com.example.cardscanner.ui.confirmation

import com.example.cardscanner.data.CardRecord
import com.example.cardscanner.data.CardRepository
import com.example.cardscanner.ocr.OcrResult
import com.example.cardscanner.ui.scanner.ScannerViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Confirms, edits and saves the pending SAFE record. The confirm screen may
 * only touch [OcrResult.safeData] (masked PAN + last 4) — never anything else.
 */
class ConfirmationViewModel(
    private val repository: CardRepository,
    private val scanner: ScannerViewModel,
    private val scope: CoroutineScope,
) {

    data class EditableFields(
        val cardholderName: String = "",
        val expiryText: String = "", // MM/YYYY
        val notes: String = "",
    )

    var editable by androidx.compose.runtime.mutableStateOf(EditableFields())
        private set

    var duplicateDetected by androidx.compose.runtime.mutableStateOf(false)
        private set

    var saved by androidx.compose.runtime.mutableStateOf<CardRecord?>(null)
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
     * Confirm & Save. Checks duplicates (Brand + Last4 + Expiry) first and
     * surfaces [duplicateDetected] instead of writing when one exists.
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
            val duplicate = repository.isDuplicate(
                brand = "VISA",
                last4 = safe.last4,
                month = expiry?.first ?: result.expiryMonth,
                year = expiry?.second ?: result.expiryYear,
            )
            if (duplicate && !acceptDuplicate) {
                duplicateDetected = true
                return@launch
            }
            try {
                val record = repository.save(
                    brand = "VISA",
                    last4 = safe.last4,
                    maskedPan = safe.maskedPan,
                    month = expiry?.first ?: result.expiryMonth,
                    year = expiry?.second ?: result.expiryYear,
                    cardholder = editable.cardholderName.ifBlank { result.cardholderName },
                    notes = editable.notes,
                )
                saved = record
                duplicateDetected = false
                scanner.beginScan()
            } catch (t: Throwable) {
                saveError = "error_generic"
            }
        }
    }

    fun dismissDuplicate() {
        duplicateDetected = false
    }

    fun consumeSaveError() {
        saveError = null
    }
}
