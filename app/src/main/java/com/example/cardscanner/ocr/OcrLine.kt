package com.example.cardscanner.ocr

/**
 * Raw text lines emitted by the on-device OCR, with per-line confidence.
 *
 * SECURITY: instances of this class hold raw recognized text which may contain
 * a complete PAN. It must stay inside the scanner/OCR module and must never be
 * logged, stored, or passed to UI. [CardOcrEngine.analyze] reduces it to safe
 * fields before any result leaves the module.
 */
data class OcrLine(
    val text: String,
    val confidence: Float,
)
