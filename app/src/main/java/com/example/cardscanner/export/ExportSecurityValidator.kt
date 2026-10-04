package com.example.cardscanner.export

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Scans the generated workbook for actual PAN-like content before it is written
 * to user storage or shared.
 *
 * Design note (v1.0.1): the first implementation scanned the raw zip bytes and
 * rejected any 13-19 digit run. That caused false positives on harmless
 * workbook internals: ZIP headers/CRCs, DEFLATE-compressed binary noise and
 * Excel epoch serials all contain long digit sequences that have nothing to do
 * with card data. This version opens the archive and scans only the
 * meaningful, human-readable cell text — exactly what a real leak would use.
 *
 * A 13-19 digit run inside a cell's TEXT content is at best a transcription of
 * a card number and at worst the full PAN: either way it must not be exported,
 * so it is rejected. Masked card numbers like `**** **** **** 1234` contain
 * only a 4-digit tail and pass.
 */
class ExportSecurityValidator {

    sealed interface Verdict {
        data object Clean : Verdict
        data class Rejected(val reason: String) : Verdict
    }

    /**
     * Opens the workbook archive and scans every XML part's inline-string cell
     * text. Checks:
     *  1. every cell's TEXT content for 13-19 consecutive digit runs;
     *  2. every cell's TEXT content for grouped PAN shapes with separators;
     *  3. that all expected column titles are present (schema whitelist).
     *
     * Numeric `<v>` cells (last-4, expiry month/year, date serials) and ZIP/XML
     * internals are ignored — they cannot carry a full PAN by construction of
     * the writer.
     */
    fun validate(xlsxBytes: ByteArray, expectedColumns: List<String>): Verdict {
        val cells = mutableListOf<String>()

        ZipInputStream(ByteArrayInputStream(xlsxBytes)).use { zip ->
            var entry = zip.nextEntry ?: return Verdict.Rejected("Workbook has no entries")
            while (entry != null) {
                if (entry.name.endsWith(".xml") || entry.name.endsWith(".rels")) {
                    val xml = zip.readBytes().toString(Charsets.UTF_8)
                    cells += extractCellText(xml)
                }
                entry = zip.nextEntry
            }
        }

        // 1. A full PAN hidden as text: 13-19 consecutive digits.
        val digitRun = Regex("""\d{13,19}""")
        for (cell in cells) {
            if (digitRun.containsMatchIn(cell)) {
                return Verdict.Rejected("PAN-like digit sequence found in a cell")
            }
        }

        // 2. Grouped PAN shapes with separators: 4111 1111 1111 1111 / 4111-1111-1111-1111.
        val grouped = Regex("""(?:\d{4}[ \-]){3}\d{4,7}""")
        for (cell in cells) {
            if (grouped.containsMatchIn(cell)) {
                return Verdict.Rejected("Grouped PAN-like sequence found in a cell")
            }
        }

        // 3. Column whitelist: the workbook must contain every expected column title.
        for (column in expectedColumns) {
            if (cells.none { it == column }) {
                return Verdict.Rejected("Expected column missing: $column")
            }
        }

        return Verdict.Clean
    }

    /**
     * Extracts the human-readable text of every inline string cell
     * (`<is><t>value</t></is>`). Shared-string tables, if present, use the same
     * `<t>` element and are covered by the generic `<t>` fallback.
     */
    private fun extractCellText(xml: String): List<String> {
        // inline strings first; fall back to any <t>…</t> (covers sharedStrings).
        val inlineCell = Regex("""<is><t[^>]*>(.*?)</t></is>""", RegexOption.DOT_MATCHES_ALL)
        val inline = inlineCell.findAll(xml).map { it.groupValues[1] }
        val generic = Regex("""<t[^>]*>(.*?)</t>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml)
            .map { it.groupValues[1] }
        return (inline + generic).toList()
    }
}
