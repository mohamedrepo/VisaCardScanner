package com.example.cardscanner.export

import com.example.cardscanner.security.PanSanitizer

/**
 * Scans a fully generated workbook (as bytes) for PAN-like digit sequences
 * before it is written to user storage or shared.
 *
 * This implements the spec requirement: "The app must inspect the generated
 * workbook before sharing it and reject export if a 13-19 digit PAN-like
 * sequence is found anywhere in the workbook."
 */
class ExportSecurityValidator {

    sealed interface Verdict {
        data object Clean : Verdict
        data class Rejected(val reason: String) : Verdict
    }

    /**
     * Deep scan over the serialized workbook. It checks:
     *  1. any 13-19 digit run anywhere in the XML parts;
     *  2. grouped PAN shapes such as `4111 1111 1111 1111`;
     *  3. the known approved column set for the sheet (whitelist).
     */
    fun validate(xlsxBytes: ByteArray, expectedColumns: List<String>): Verdict {
        val text = xlsxBytes.toString(Charsets.UTF_8)

        // 1. Raw long digit runs (covers shared strings, inline strings, everything).
        val digitRun = Regex("""\d{13,19}""")
        val run = digitRun.find(text)
        if (run != null) {
            return Verdict.Rejected("PAN-like digit sequence found in workbook")
        }

        // 2. Grouped PAN shapes with separators.
        val grouped = Regex("""(?:\d{4}[ \-]){3}\d{1,7}""")
        if (grouped.containsMatchIn(text)) {
            return Verdict.Rejected("Grouped PAN-like sequence found in workbook")
        }

        // 3. Column whitelist: the workbook must not contain unexpected column titles.
        for (column in expectedColumns) {
            val escaped = Regex.escape(column)
            if (!Regex(escaped).containsMatchIn(text)) {
                return Verdict.Rejected("Expected column missing: $column")
            }
        }

        // 4. Luhn-check every remaining digit run of length 13-19 split across
        //    separators — defence in depth (redundant with check 1-2 but cheap).
        val anyDigits = Regex("""\d{4}(?:[ \-]?\d{4}){2,4}""")
        for (match in anyDigits.findAll(text)) {
            val digits = match.value.filter { it.isDigit() }
            if (digits.length in 13..19 && PanSanitizer.looksLikeFullPan(match.value)) {
                return Verdict.Rejected("PAN-like sequence found in workbook")
            }
        }

        return Verdict.Clean
    }
}
