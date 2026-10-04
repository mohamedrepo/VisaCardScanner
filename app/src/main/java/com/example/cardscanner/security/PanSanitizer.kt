package com.example.cardscanner.security

/**
 * Turns a recognized PAN candidate into its only permitted persistent forms:
 * a masked string and the last four digits.
 *
 * The complete number never leaves this function's call site and the caller is
 * required to null out its reference immediately afterwards (see [TransientPan]).
 */
object PanSanitizer {

    /**
     * Masked representation, e.g. `**** **** **** 1234`.
     */
    fun mask(last4: String): String = "**** **** **** ${last4.takeLast(4)}"

    /**
     * Truncated, non-reversible representation kept in the record.
     */
    fun truncate(last4: String): String = last4.takeLast(4)

    /**
     * True when the text looks like a full PAN (13-19 digits).
     * Used by [com.example.cardscanner.security.LogSanitizer] and the export
     * validator as a defence-in-depth check.
     */
    fun looksLikeFullPan(text: String): Boolean {
        val digits = text.filter { it.isDigit() }
        if (digits.length !in 13..19) return false
        // A "PAN-like" sequence must be predominantly digits in the original text
        // so that ordinary numbers (dates, counts) are not falsely flagged.
        val digitRatio = digits.length.toDouble() / text.length.coerceAtLeast(1)
        return digitRatio >= 0.9
    }
}

/**
 * A container for the complete PAN that exists solely inside the scanner module.
 *
 * Lifecycle rules:
 *  1. Created only after Luhn + Visa validation succeeded.
 *  2. Read exactly once by [consume] to produce the masked representation.
 *  3. The internal array is zeroed on consumption; the reference must be dropped.
 *
 * This class never appears in UI, Room, export, or log paths.
 */
class TransientPan private constructor(digits: String) {

    private val digits: CharArray = digits.toCharArray()
    private var consumed = false

    val last4: String
        get() {
            check(!consumed) { "TransientPan already consumed" }
            return String(digits.takeLast(4).toCharArray())
        }

    val masked: String
        get() = PanSanitizer.mask(last4)

    /**
     * Returns the masked + last-4 pair and irreversibly destroys the stored digits.
     * A second call throws because the value no longer exists.
     */
    fun consume(): MaskedResult {
        check(!consumed) { "TransientPan already consumed" }
        consumed = true
        val result = MaskedResult(
            maskedPan = PanSanitizer.mask(String(digits.takeLast(4).toCharArray())),
            last4 = String(digits.takeLast(4).toCharArray()),
        )
        digits.fill('0')
        return result
    }

    data class MaskedResult(val maskedPan: String, val last4: String)

    companion object {
        /**
         * Single construction gate. `digits` must already have passed Luhn and
         * Visa-prefix validation upstream.
         */
        fun createValidated(digits: String): TransientPan = TransientPan(digits.filter { it.isDigit() })
    }
}
