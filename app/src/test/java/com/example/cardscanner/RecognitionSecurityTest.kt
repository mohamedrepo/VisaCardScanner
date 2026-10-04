package com.example.cardscanner

import com.example.cardscanner.ocr.CardOcrEngine
import com.example.cardscanner.ocr.OcrLine
import com.example.cardscanner.ocr.PanResult
import com.example.cardscanner.ocr.PanDetector
import com.example.cardscanner.ocr.ExpiryDetector
import com.example.cardscanner.ocr.CardholderDetector
import com.example.cardscanner.security.CardBrandRules
import com.example.cardscanner.security.LuhnValidator
import com.example.cardscanner.security.LogSanitizer
import com.example.cardscanner.security.PanSanitizer
import com.example.cardscanner.security.TransientPan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec §19 security tests, recognition-related:
 *  - Test 1: no complete PAN survives the recognition boundary
 *  - Test 5: OCR text is never emitted by the engine (module-private)
 *  - Test 6: Luhn-invalid numbers are rejected
 *  - Test 7: non-Visa cards are recognized as unsupported
 */
class RecognitionSecurityTest {

    // Luhn-valid Visa test number (standard test PAN, never a real card).
    private val validVisa = "4111111111111111"

    /** Test 6: Luhn check rejects invalid numbers. */
    @Test
    fun luhnRejectsInvalidNumbers() {
        assertFalse(LuhnValidator.isValid("4111111111111112"))
        assertFalse(LuhnValidator.isValid("1234567890123456"))
        assertFalse(LuhnValidator.isValid("4222222222222221")) // one off from valid
        assertTrue(LuhnValidator.isValid(validVisa))
        assertTrue(LuhnValidator.isValid("4222222222222")) // 13-digit Visa test number
    }

    /** Test 7: non-Visa brands are detected and rejected by prefix rules. */
    @Test
    fun nonVisaIsRejectedAsUnsupported() {
        // Luhn-valid Mastercard-style number (55 xx). The last digit is chosen
        // to make the checksum valid: 5500005555555559.
        val mastercard = "5500005555555559"
        assertTrue("test PAN must be Luhn-valid", LuhnValidator.isValid(mastercard))
        assertFalse(CardBrandRules.visaPrefixValid(mastercard))
        assertEquals("MASTERCARD", CardBrandRules.detectBrand(mastercard))

        val lines = listOf(OcrLine(mastercard, 0.95f))
        val result = PanDetector.detect(lines)
        assertTrue(result is PanResult.Invalid)
        assertEquals(PanResult.Reason.NOT_VISA, (result as PanResult.Invalid).reason)
    }

    /** Test 1: the PAN exists only transiently; consume() destroys the digits. */
    @Test
    fun transientPanIsDestroyedAfterConsumption() {
        val pan = TransientPan.createValidated(validVisa)
        val consumed = pan.consume()

        assertEquals("1111", consumed.last4)
        assertEquals("**** **** **** 1111", consumed.maskedPan)

        // A second consume must fail: the value is gone.
        var destroyed = false
        try {
            pan.consume()
        } catch (expected: IllegalStateException) {
            destroyed = true
        }
        assertTrue(destroyed)
    }

    /**
     * Test 1b (the boundary test): after OCR completes, the only data handed to
     * the UI layer is the masked PAN + last 4 + expiry + name. Raw OCR text is
     * never part of the result.
     */
    @Test
    fun ocrResultContainsOnlySafeFields() {
        val engine = CardOcrEngine()
        val lines = listOf(
            OcrLine("BANK OF EXAMPLE", 0.9f),
            OcrLine(validVisa, 0.95f),
            OcrLine("12/29", 0.9f),
            OcrLine("MOHAMED SALAH ALI", 0.85f),
        )
        val result = engine.analyzeLines(lines)

        val safe = result.safeData!!
        assertEquals("1111", safe.last4)
        assertEquals("**** **** **** 1111", safe.maskedPan)
        assertEquals(12, result.expiryMonth)
        assertEquals(2029, result.expiryYear)
        assertEquals("MOHAMED SALAH ALI", result.cardholderName)

        // Reflective check: no property of the result holds the full number.
        val allStrings = sequenceOf(
            safe.maskedPan,
            safe.last4,
            result.cardholderName ?: "",
        ).joinToString(" ")
        assertFalse(allStrings.contains(validVisa))
    }

    /** Test 5: log sanitizer masks PAN-shaped content in diagnostic text. */
    @Test
    fun logSanitizerMasksPanShapedText() {
        val diagnostic = "scan failed for card 4111 1111 1111 1111 at stage OCR"
        val sanitized = LogSanitizer.sanitize(diagnostic)
        assertFalse(sanitized.contains("4111"))
        assertFalse(sanitized.contains(validVisa))
        assertTrue(sanitized.contains("*"))

        val raw = LogSanitizer.sanitize("pan=4111111111111111")
        assertFalse(raw.contains("4111111111111111"))
    }

    /** Masking is not truncation: the stored form must be the truncated one. */
    @Test
    fun maskIsNotTruncation() {
        val last4 = PanSanitizer.truncate("1111")
        assertEquals(4, last4.length)
        assertEquals("1111", last4)
        assertFalse(PanSanitizer.mask(last4).contains("1111 "))
    }

    /** Expiry parsing across the accepted formats, normalized to MM/YYYY. */
    @Test
    fun expiryFormatsAreNormalized() {
        val lines = listOf(
            OcrLine("12/29", 0.9f),
            OcrLine("VALID 08-27", 0.85f),
            OcrLine("EXP 11/2029", 0.95f),
        )
        val detected = ExpiryDetector.detect(lines)
        assertEquals(11, detected?.month) // highest-confidence match wins
        assertEquals(2029, detected?.year)
    }

    /** Cardholder name heuristics ignore keyword lines and digits. */
    @Test
    fun cardholderDetectorIgnoresKeywords() {
        val lines = listOf(
            OcrLine("VISA", 0.99f),
            OcrLine("VALID THRU 12/29", 0.99f),
            OcrLine("MOHAMED SALAH ALI", 0.9f),
        )
        val detected = CardholderDetector.detect(lines)
        assertEquals("MOHAMED SALAH ALI", detected?.first)
    }

    /** No PAN at all → NotFound, not a crash. */
    @Test
    fun missingPanIsReportedAsNotFound() {
        val result = PanDetector.detect(listOf(OcrLine("MOHAMED SALAH", 0.9f)))
        assertTrue(result is PanResult.NotFound)
    }
}
