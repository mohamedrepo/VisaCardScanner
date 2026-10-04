package com.example.cardscanner.camera

import android.graphics.Bitmap
import android.graphics.Matrix

/**
 * In-memory image enhancement performed on the single capture frame before OCR.
 *
 * SECURITY: every Bitmap handed to this class lives only in RAM. Nothing here
 * writes to disk, a content provider, or a cache directory.
 */
object ImageProcessor {

    /**
     * Crops the detected (normalized) card rectangle from the frame and scales it
     * up to a comfortable OCR width, applying perspective-friendly rotation when
     * the analysis frame was in landscape while the sensor is portrait.
     */
    fun cropAndEnhance(
        bitmap: Bitmap,
        detection: CardDetection,
        targetWidth: Int = 1280,
    ): Bitmap {
        val l = (detection.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 2)
        val t = (detection.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 2)
        val r = (detection.right * bitmap.width).toInt().coerceIn(l + 1, bitmap.width)
        val b = (detection.bottom * bitmap.height).toInt().coerceIn(t + 1, bitmap.height)
        val w = r - l
        val h = b - t

        val cropped = Bitmap.createBitmap(bitmap, l, t, w, h)

        val scale = (targetWidth.toFloat() / w).coerceIn(1f, 4f)
        val scaled = Bitmap.createScaledBitmap(
            cropped,
            (w * scale).toInt().coerceAtMost(4096),
            (h * scale).toInt().coerceAtMost(4096),
            true,
        )
        if (scaled != cropped) cropped.recycle()

        val enhanced = boostContrast(scaled)
        if (enhanced != scaled) scaled.recycle()
        return enhanced
    }

    /**
     * Lightweight contrast stretch based on the luminance histogram (1st-99th
     * percentile), which helps embossed OCR without any cloud processing.
     */
    private fun boostContrast(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val hist = IntArray(256)
        for (p in pixels) {
            val lum = (((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000)
            hist[lum]++
        }
        val total = w * h
        var low = 0
        var high = 255
        var acc = 0
        val lowCut = (total * 0.01).toInt()
        val highCut = (total * 0.99).toInt()
        for (i in 0..255) {
            acc += hist[i]
            if (acc >= lowCut) { low = i; break }
        }
        acc = 0
        for (i in 0..255) {
            acc += hist[i]
            if (acc >= highCut) { high = i; break }
        }
        if (high - low < 24) return src

        val lut = IntArray(256) { i ->
            ((i - low) * 255 / (high - low)).coerceIn(0, 255)
        }
        for (idx in pixels.indices) {
            val p = pixels[idx]
            val r = lut[p shr 16 and 0xFF]
            val g = lut[p shr 8 and 0xFF]
            val b = lut[p and 0xFF]
            pixels[idx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Rotates a bitmap; used to align the card strip horizontally for OCR. */
    fun rotate(src: Bitmap, degrees: Float): Bitmap {
        if (degrees % 360f == 0f) return src
        val m = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }
}
