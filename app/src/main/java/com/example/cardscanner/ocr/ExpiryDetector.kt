package com.example.cardscanner.ocr

/**
 * Expiry-date recognition: accepts MM/YY, MM-YY, MM/YYYY, MM-YYYY and converts
 * internally to (month, year) with a four-digit year.
 */
object ExpiryDetector {

    private val pattern = Regex("""\b(0[1-9]|1[0-2])\s*[/\-]\s*(\d{2}|\d{4})\b""")

    data class Expiry(val month: Int, val year: Int)

    fun detect(lines: List<OcrLine>): Expiry? {
        var best: Expiry? = null
        var bestConf = -1f
        for (line in lines) {
            for (m in pattern.findAll(line.text)) {
                val month = m.groupValues[1].toInt()
                val rawYear = m.groupValues[2].toInt()
                val year = if (rawYear < 100) 2000 + rawYear else rawYear
                if (year !in 2020..2055) continue
                val conf = line.confidence
                if (conf > bestConf) {
                    bestConf = conf
                    best = Expiry(month, year)
                }
            }
        }
        return best
    }
}
