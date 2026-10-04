package com.example.cardscanner.ocr

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reduces one OCR pass over the enhanced card bitmap into SAFE fields only.
 *
 * SECURITY CONTRACT (enforced by the return type):
 *   - the complete PAN never leaves this class;
 *   - [OcrResult] contains exclusively the masked PAN, last 4, expiry and name;
 *   - raw OCR text ([OcrLine]) is a module-private intermediate that is dropped
 *     when this method returns;
 *   - nothing here logs, stores, uploads, or copies to clipboard.
 */
class CardOcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Full pipeline for one captured frame:
     * bitmap → OCR → text segmentation → PAN candidate → Luhn → Visa →
     * masked/last-4 extraction → PAN discard → expiry → cardholder.
     */
    fun analyze(bitmap: android.graphics.Bitmap): OcrResult {
        val image = InputImage.fromBitmap(bitmap, 0)
        val visionText = kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withTimeout(10_000) {
                kotlinx.coroutines.suspendCancellableCoroutine<com.google.mlkit.vision.text.Text> { cont ->
                    recognizer.process(image)
                        .addOnSuccessListener { text ->
                            cont.resume(text) { _ -> if (!bitmap.isRecycled) bitmap.recycle() }
                        }
                        .addOnFailureListener { error ->
                            cont.resumeWithException(error)
                            if (!bitmap.isRecycled) bitmap.recycle()
                        }
                }
            }
        }

        val lines = visionText.textBlocks.flatMap { block ->
            block.lines.map { line ->
                OcrLine(
                    text = line.text,
                    confidence = line.confidence ?: 0.9f,
                )
            }
        }

        return analyzeLines(lines)
    }

    /**
     * Pure-logic core, separated from ML Kit so it is directly unit-testable
     * without an Android device.
     */
    fun analyzeLines(lines: List<OcrLine>): OcrResult {
        val panOutcome = PanDetector.detect(lines)

        val safe: SafeCardData? = when (panOutcome) {
            is PanResult.Valid -> {
                // Boundary: consume (mask + truncate) and destroy the PAN in the
                // same expression. `pan` itself is never captured anywhere.
                val consumed = panOutcome.pan.consume()
                SafeCardData(
                    maskedPan = consumed.maskedPan,
                    last4 = consumed.last4,
                    bin6 = consumed.bin6,
                )
            }
            else -> null
        }

        val expiry = ExpiryDetector.detect(lines)
        val nameOutcome = CardholderDetector.detect(lines)

        return OcrResult(
            safeData = safe,
            panFailure = (panOutcome as? PanResult.Invalid)?.reason,
            panFound = panOutcome !is PanResult.NotFound,
            expiryMonth = expiry?.month,
            expiryYear = expiry?.year,
            expiryLowConfidence = expiry != null && lines.any { it.confidence < 0.6f },
            cardholderName = nameOutcome?.first,
            cardholderLowConfidence = nameOutcome != null && !nameOutcome.second,
        )
    }

    fun close() {
        recognizer.close()
    }
}

/**
 * Only safe fields cross the scanner-module boundary toward UI/Room/export.
 */
data class SafeCardData(
    val maskedPan: String,
    val last4: String,
    val bin6: String,
)

/**
 * Result handed to the confirmation screen. There is deliberately no field here
 * — or in anything it references — that could carry a complete PAN.
 */
data class OcrResult(
    val safeData: SafeCardData?,
    val panFound: Boolean,
    val panFailure: PanResult.Reason?,
    val expiryMonth: Int?,
    val expiryYear: Int?,
    val expiryLowConfidence: Boolean,
    val cardholderName: String?,
    val cardholderLowConfidence: Boolean,
) {
    companion object {
        fun empty(): OcrResult = OcrResult(
            safeData = null,
            panFound = false,
            panFailure = null,
            expiryMonth = null,
            expiryYear = null,
            expiryLowConfidence = false,
            cardholderName = null,
            cardholderLowConfidence = false,
        )
    }
}
