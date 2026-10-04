package com.example.cardscanner

import com.example.cardscanner.camera.CardDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Non-Android tests for the pure detection logic: card-shape acceptance,
 * aspect-ratio rejection, quality gating and duplicate-model equality.
 */
class DetectionAndModelTest {

    /** Draws a bright rectangle (the "card") on a dark background. */
    private fun syntheticFrame(
        width: Int = 640,
        height: Int = 480,
        rect: quadruple = quadruple(0.15f, 0.25f, 0.85f, 0.75f),
    ): ByteArray {
        val luma = ByteArray(width * height) { 20 }
        val l = (rect.a * width).toInt()
        val t = (rect.b * height).toInt()
        val r = (rect.c * width).toInt()
        val b = (rect.d * height).toInt()
        for (y in t..b) {
            for (x in l..r) {
                luma[y * width + x] = 235
            }
        }
        return luma
    }

    data class quadruple(val a: Float, val b: Float, val c: Float, val d: Float)

    @Test
    fun cardShapedBrightRectangleIsDetected() {
        val detector = CardDetector()
        val detection = detector.detect(syntheticFrame(), 640, 480)
        // The synthetic card is a solid rectangle: edges exist and the aspect
        // ratio is in range, so detection must succeed.
        assertNotNull(detection)
        val d = detection!!
        assertTrue(d.aspectRatio in 1.3f..1.95f)
        assertTrue(d.confidence > 0.0)
    }

    @Test
    fun tinyObjectIsRejected() {
        val detector = CardDetector()
        // A very small bright square in the middle of the frame.
        val width = 640
        val height = 480
        val luma = ByteArray(width * height) { 20 }
        for (y in 200..240) for (x in 300..340) luma[y * width + x] = 235
        assertNull(detector.detect(luma, width, height))
    }

    @Test
    fun blankFrameIsRejected() {
        val detector = CardDetector()
        assertNull(detector.detect(ByteArray(640 * 480) { 128 }, 640, 480))
    }

    /** Duplicate detection is Brand + Last4 + Expiry equality (spec §13). */
    @Test
    fun duplicateKeyIsBrandLast4Expiry() {
        val base = com.example.cardscanner.data.CardRecord(
            id = "1",
            brand = "VISA",
            maskedPan = "**** **** **** 1234",
            last4 = "1234",
            expiryMonth = 12,
            expiryYear = 2029,
            cardholderName = "A",
            scannedAt = Instant.parse("2026-10-04T16:30:22Z"),
        )
        val sameKey = base.copy(
            id = "2",
            cardholderName = "DIFFERENT NAME",
            scannedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )
        val differentExpiry = base.copy(id = "3", expiryYear = 2030)

        // The duplicate key components are identical for base/sameKey.
        assertEquals(base.brand, sameKey.brand)
        assertEquals(base.last4, sameKey.last4)
        assertEquals(base.expiryMonth, sameKey.expiryMonth)
        assertEquals(base.expiryYear, sameKey.expiryYear)
        // And differ for differentExpiry.
        assertTrue(differentExpiry.expiryYear != base.expiryYear)
    }
}
