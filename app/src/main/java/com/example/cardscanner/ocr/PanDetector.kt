package com.example.cardscanner.ocr

import com.example.cardscanner.security.CardBrandRules
import com.example.cardscanner.security.LuhnValidator
import com.example.cardscanner.security.TransientPan

/**
 * Outcome of the PAN recognition stage.
 *
 * SECURITY: [Valid] wraps a [TransientPan] that lives only inside the scanner
 * module. The complete digits are destroyed by [TransientPan.consume] before a
 * confirmation can be shown, and no other member exposes them.
 */
sealed interface PanResult {
    /** A Luhn-valid, Visa-prefix-valid PAN candidate. Transient only. */
    data class Valid(val pan: TransientPan, val confidence: Float) : PanResult

    /** Digits were found but failed validation. The digits are discarded here. */
    data class Invalid(val reason: Reason) : PanResult

    /** No plausible PAN candidate was found in the OCR text. */
    data object NotFound : PanResult

    enum class Reason { LUHN_FAILED, NOT_VISA, BAD_LENGTH }
}

/**
 * Extracts PAN candidates from OCR lines and validates them with:
 * digit extraction -> whitespace removal -> length check -> Luhn -> Visa prefix.
 *
 * The complete number exists only as a local variable inside [detect]; it never
 * becomes a field, never enters a log, and is unreachable after this call.
 */
object PanDetector {

    // Matches embossed PAN groups like "4111 1111 1111 1111" or "4111-1111-1111-1111".
    private val groupedRun = Regex("""(?:\d[ -]?){13,23}""")

    fun detect(lines: List<OcrLine>): PanResult {
        var best: PanResult = PanResult.NotFound
        var bestConfidence = -1f

        for (line in lines) {
            // Take digit runs within a single line; the PAN is never split across
            // lines on an embossed card.
            for (region in groupedRun.findAll(line.text)) {
                val digits = region.value.filter { it.isDigit() }
                val candidate = evaluate(digits, line.confidence)
                val score = when (candidate) {
                    is PanResult.Valid -> candidate.confidence + 1f
                    is PanResult.Invalid -> 0.5f
                    PanResult.NotFound -> 0f
                }
                if (score > bestConfidence) {
                    bestConfidence = score
                    best = candidate
                }
                // Local `digits` goes out of scope here; nothing retains it.
            }
        }
        return best
    }

    private fun evaluate(digits: String, ocrConfidence: Float): PanResult {
        if (digits.length !in 13..19) return PanResult.Invalid(PanResult.Reason.BAD_LENGTH)
        if (!LuhnValidator.isValid(digits)) return PanResult.Invalid(PanResult.Reason.LUHN_FAILED)
        if (!CardBrandRules.visaPrefixValid(digits)) return PanResult.Invalid(PanResult.Reason.NOT_VISA)
        return PanResult.Valid(TransientPan.createValidated(digits), ocrConfidence)
    }
}
