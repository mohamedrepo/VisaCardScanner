package com.example.cardscanner.ocr

/**
 * Cardholder-name recognition: the cardholder line is printed (not embossed in
 * the same way), typically ALL CAPS, positioned below the PAN. Heuristics:
 *  - letters, spaces, hyphens, apostrophes, periods
 *  - 2..5 words
 *  - not a keyword line (VISA, VALID THRU, CARDHOLDER, etc.)
 */
object CardholderDetector {

    private val keywordLine = Regex(
        """(?i)(VISA|MASTERCARD|AMEX|VALID|THRU|FROM|UNTIL|CARD\s*HOLDER|MEMBER|SINCE|BANK|UNIONPAY|DEBIT|CREDIT|ELECTRON|PLATINUM|GOLD|CLASSIC|WORLD|SIGNIA|INFINITE|BUSINESS)"""
    )

    private val namePattern = Regex("""^[A-Z][A-Z'\-\. ]{2,39}$""")

    fun detect(lines: List<OcrLine>): Pair<String, Boolean>? {
        var best: String? = null
        var bestScore = -1f

        for (line in lines) {
            val text = line.text.trim().replace(Regex("""\s+"""), " ")
            if (text.length < 3 || text.length > 40) continue
            if (keywordLine.containsMatchIn(text)) continue
            if (text.any { it.isDigit() }) continue
            if (!namePattern.matches(text)) continue

            val words = text.split(' ')
            if (words.size !in 2..5) continue

            // Prefer lines with typical name-letter density.
            val letters = text.count { it.isLetter() }
            val score = line.confidence * (letters.toFloat() / text.length)
            if (score > bestScore) {
                bestScore = score
                best = text
            }
        }

        val name = best ?: return null
        // Confidence below 0.75 → ask the user to verify (see confirmation screen).
        val confident = bestScore >= 0.75f
        return name to confident
    }
}
