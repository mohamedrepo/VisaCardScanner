package com.example.cardscanner.security

/**
 * Scrubs payment-card data out of any text that could reach a log, a crash
 * report, or the clipboard.
 *
 * The OCR text itself is never logged anywhere in this application; this class
 * additionally guards the few diagnostic messages the app does emit.
 */
object LogSanitizer {

    // 13-19 digit runs are replaced group-wise: every digit becomes '*'.
    private val panRun = Regex("""\d{13,19}""")

    // Common embossed formats: groups of 4 digits separated by spaces/dashes.
    private val groupedPan = Regex("""(?:\d{4}[ -]){3}\d{1,7}""")

    fun sanitize(message: String): String {
        var out = groupedPan.replace(message) { match ->
            "*".repeat(match.value.filter { it.isDigit() }.length)
        }
        out = panRun.replace(out) { match ->
            // Keep clearly non-PAN long digit runs (timestamps etc.) intact only if
            // they cannot be a Luhn-valid PAN; when in doubt, mask.
            if (LuhnValidator.isValid(match.value)) {
                "*".repeat(match.value.length)
            } else {
                "*".repeat(match.value.length)
            }
        }
        return out
    }
}
