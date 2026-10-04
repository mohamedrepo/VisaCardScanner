package com.example.cardscanner.camera

/**
 * Visual/technical quality of the current preview frame, used to decide when an
 * automatic capture is allowed and to drive the on-screen confidence indicator.
 */
enum class FrameQuality {
    POOR, FAIR, GOOD, EXCELLENT;

    companion object {
        fun from(sharpness: Double, brightness: Double): FrameQuality {
            // sharpness: normalized variance-of-Laplacian style score (0..1)
            // brightness: mean luminance (0..1)
            val sharpOk = sharpness >= 0.15
            val lightOk = brightness in 0.18..0.92
            return when {
                !sharpOk || !lightOk -> POOR
                sharpness < 0.30 || brightness < 0.30 || brightness > 0.85 -> FAIR
                sharpness < 0.55 -> GOOD
                else -> EXCELLENT
            }
        }
    }
}

/**
 * Result of analyzing one preview frame for an ID-1 card shape.
 * All rectangle coordinates are normalized (0..1) relative to the frame.
 */
data class CardDetection(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val aspectRatio: Float,
    val sharpness: Double,
    val brightness: Double,
    val quality: FrameQuality,
    val confidence: Double,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val center: Pair<Float, Float> get() = ((left + right) / 2f) to ((top + bottom) / 2f)

    fun intersectionOverArea(other: CardDetection): Double {
        val ix = (right.coerceAtMost(other.right) - left.coerceAtLeast(other.left))
        val iy = (bottom.coerceAtMost(other.bottom) - top.coerceAtLeast(other.top))
        if (ix <= 0f || iy <= 0f) return 0.0
        val inter = (ix * iy).toDouble()
        val area = (width * height).toDouble().coerceAtLeast(1e-6)
        return inter / area
    }
}

/**
 * Detects an ISO/IEC 7810 ID-1 card silhouette (aspect ratio ≈ 1.586:1) in a
 * grayscale camera frame using gradient profiles. Pure CPU, offline, and it
 * never retains the analyzed frame beyond this call.
 */
class CardDetector {

    data class Config(
        val sampleWidth: Int = 160,
        val minAspect: Float = 1.30f,
        val maxAspect: Float = 1.95f,
        val minCoverage: Float = 0.18f,
        val edgeThreshold: Double = 0.12,
    )

    private val config = Config()

    /**
     * @param luma packed luminance rows, row-major, `width * height` values 0..255.
     */
    fun detect(luma: ByteArray, width: Int, height: Int): CardDetection? {
        if (width < 32 || height < 24) return null

        val sw = config.sampleWidth
        val sh = (sw * height / width).coerceIn(24, 200)
        val grid = downsample(luma, width, height, sw, sh)

        var brightnessSum = 0.0
        for (v in grid) brightnessSum += v
        val brightness = brightnessSum / grid.size / 255.0

        // Sobel gradient magnitudes on the downsampled grid.
        val gx = DoubleArray(sw * sh)
        val gy = DoubleArray(sw * sh)
        var maxGrad = 1.0
        for (y in 1 until sh - 1) {
            for (x in 1 until sw - 1) {
                val i = y * sw + x
                val a = grid[i - sw - 1].toDouble(); val b = grid[i - sw].toDouble(); val c = grid[i - sw + 1].toDouble()
                val d = grid[i - 1].toDouble(); val f = grid[i + 1].toDouble()
                val g = grid[i + sw - 1].toDouble(); val h = grid[i + sw].toDouble(); val k = grid[i + sw + 1].toDouble()
                val sx = (c + 2 * f + k) - (a + 2 * d + g)
                val sy = (g + 2 * h + k) - (a + 2 * b + c)
                val mag = kotlin.math.sqrt(sx * sx + sy * sy)
                gx[i] = sx; gy[i] = sy
                if (mag > maxGrad) maxGrad = mag
            }
        }

        // Edge strength profiles per column and row.
        val colProfile = DoubleArray(sw)
        val rowProfile = DoubleArray(sh)
        for (y in 1 until sh - 1) {
            for (x in 1 until sw - 1) {
                val mag = kotlin.math.sqrt(gx[y * sw + x] * gx[y * sw + x] + gy[y * sw + x] * gy[y * sw + x]) / maxGrad
                if (mag > config.edgeThreshold) {
                    colProfile[x] += mag
                    rowProfile[y] += mag
                }
            }
        }

        val left = firstStrongIndex(colProfile) ?: return null
        val right = lastStrongIndex(colProfile) ?: return null
        val top = firstStrongIndex(rowProfile) ?: return null
        val bottom = lastStrongIndex(rowProfile) ?: return null
        if (right - left < 4 || bottom - top < 3) return null

        val rectW = (right - left + 1).toFloat() / sw
        val rectH = (bottom - top + 1).toFloat() / sh
        val coverage = rectW * rectH
        if (coverage < config.minCoverage) return null

        val aspect = rectW / rectH.coerceAtLeast(1e-3f)
        if (aspect !in config.minAspect..config.maxAspect) return null

        val sharpness = sharpnessScore(grid, sw, sh, maxGrad)

        return CardDetection(
            left = left.toFloat() / sw,
            top = top.toFloat() / sh,
            right = (right + 1).toFloat() / sw,
            bottom = (bottom + 1).toFloat() / sh,
            aspectRatio = aspect,
            sharpness = sharpness,
            brightness = brightness,
            quality = FrameQuality.from(sharpness, brightness),
            confidence = (coverage.toDouble() * 0.4) + (sharpness * 0.4) + (lightingScore(brightness) * 0.2),
        )
    }

    /** Heuristic sharpness: fraction of strong gradients inside the detected box. */
    private fun sharpnessScore(grid: IntArray, sw: Int, sh: Int, maxGrad: Double): Double {
        var strong = 0
        var total = 0
        for (y in 1 until sh - 1) {
            for (x in 1 until sw - 1) {
                val i = y * sw + x
                val a = grid[i - sw - 1]; val c = grid[i - sw + 1]
                val g = grid[i + sw - 1]; val k = grid[i + sw + 1]
                val lap = (a + c + g + k - 4 * grid[i]).toDouble()
                if (kotlin.math.abs(lap) / maxGrad > 0.08) strong++
                total++
            }
        }
        return (strong.toDouble() / total.coerceAtLeast(1)).coerceIn(0.0, 1.0)
    }

    private fun lightingScore(brightness: Double): Double = when {
        brightness in 0.30..0.85 -> 1.0
        brightness in 0.20..0.92 -> 0.6
        else -> 0.1
    }

    private fun firstStrongIndex(profile: DoubleArray): Int? {
        val peak = profile.max()
        if (peak <= 0.0) return null
        val threshold = peak * 0.45
        for (i in profile.indices) if (profile[i] >= threshold) return i
        return null
    }

    private fun lastStrongIndex(profile: DoubleArray): Int? {
        val peak = profile.max()
        if (peak <= 0.0) return null
        val threshold = peak * 0.45
        for (i in profile.indices.reversed()) if (profile[i] >= threshold) return i
        return null
    }

    private fun downsample(luma: ByteArray, width: Int, height: Int, sw: Int, sh: Int): IntArray {
        val out = IntArray(sw * sh)
        for (gy in 0 until sh) {
            val y0 = gy * height / sh
            val y1 = ((gy + 1) * height / sh).coerceAtLeast(y0 + 1)
            for (gx in 0 until sw) {
                val x0 = gx * width / sw
                val x1 = ((gx + 1) * width / sw).coerceAtLeast(x0 + 1)
                var sum = 0
                var count = 0
                var y = y0
                while (y < y1 && y < height) {
                    var x = x0
                    while (x < x1 && x < width) {
                        sum += luma[y * width + x].toInt() and 0xFF
                        count++
                        x++
                    }
                    y++
                }
                out[gy * sw + gx] = if (count > 0) sum / count else 0
            }
        }
        return out
    }
}

/**
 * Tracks detections across frames to decide whether the card has remained
 * stable long enough for an automatic capture.
 */
class StabilityTracker(
    private val requiredStableFrames: Int = 6,
    private val iouThreshold: Double = 0.80,
) {
    private var last: CardDetection? = null
    private var stableCount = 0

    fun onFrame(detection: CardDetection?): Boolean {
        val prev = last
        last = detection
        if (detection == null || prev == null) {
            stableCount = 0
            return false
        }
        val iou = prev.intersectionOverArea(detection)
        stableCount = if (iou >= iouThreshold) stableCount + 1 else 0
        return stableCount >= requiredStableFrames
    }

    /** True once the required run of stable frames has been reached. */
    fun hasReachedStability(): Boolean = stableCount >= requiredStableFrames

    fun reset() {
        last = null
        stableCount = 0
    }
}
