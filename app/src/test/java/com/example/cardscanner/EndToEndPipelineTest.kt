package com.example.cardscanner

import com.example.cardscanner.data.CardRecord
import com.example.cardscanner.export.CellValue
import com.example.cardscanner.export.ExcelExporter
import com.example.cardscanner.export.ExportSecurityValidator
import com.example.cardscanner.export.XlsxColumn
import com.example.cardscanner.export.XlsxWriter
import com.example.cardscanner.ocr.CardOcrEngine
import com.example.cardscanner.ocr.OcrLine
import com.example.cardscanner.security.TransientPan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.zip.ZipInputStream

/**
 * End-to-end security pipeline test, JVM-only (spec §7 flow):
 *
 *   OCR lines → PAN validation → transient PAN consumed (destroyed) →
 *   SAFE record → workbook bytes → security scan → archive re-opened and
 *   verified to contain only masked data.
 *
 * This is the acceptance test for the whole "full PAN never survives" chain.
 */
class EndToEndPipelineTest {

    // Luhn-valid Visa test PAN (standard test number, not a real card).
    private val validVisa = "4111111111111111"

    private val validator = ExportSecurityValidator()

    @Test
    fun fullPipelineOcrToValidatedWorkbook() {
        // ---- 1. OCR stage (same as CardOcrEngine.analyzeLines) -------------
        val engine = CardOcrEngine()
        val ocrLines = listOf(
            OcrLine("BANK OF EXAMPLE", 0.9f),
            OcrLine(validVisa, 0.95f),
            OcrLine("12/29", 0.9f),
            OcrLine("MOHAMED SALAH ALI", 0.85f),
        )
        val result = engine.analyzeLines(ocrLines)

        val safe = result.safeData
        assertNotNull("OCR must produce safe data for a valid Visa PAN", safe)
        val safeData = safe!!
        assertEquals("1111", safeData.last4)
        assertEquals("**** **** **** 1111", safeData.maskedPan)

        // ---- 2. Persistence stage (same fields Room would store) ----------
        val record = CardRecord(
            id = "e2e-001",
            brand = "VISA",
            maskedPan = safeData.maskedPan,
            last4 = safeData.last4,
            expiryMonth = result.expiryMonth,
            expiryYear = result.expiryYear,
            cardholderName = result.cardholderName,
            scannedAt = Instant.parse("2026-10-04T16:30:22Z"),
            notes = null,
        )
        assertEquals(12, record.expiryMonth)
        assertEquals(2029, record.expiryYear)

        // ---- 3. Export stage: build workbook + security scan --------------
        val export = ExcelExporter.buildWorkbookPure(listOf(record), validator)
        assertTrue("export must pass the security scan", export is ExcelExporter.Result.Success)
        val bytes = (export as ExcelExporter.Result.Success).bytes

        // ---- 4. Independent verification: re-open the archive -------------
        val sheetXml = readEntry(bytes, "xl/worksheets/sheet1.xml")
        assertTrue(sheetXml.contains("**** **** **** 1111"))
        assertTrue(sheetXml.contains("MOHAMED SALAH ALI"))
        assertTrue(sheetXml.contains("12"))
        assertTrue(sheetXml.contains("2029"))
        // The full PAN must not appear anywhere in the sheet — not in text,
        // not as a numeric cell value, not in metadata.
        assertTrue(!sheetXml.contains(validVisa))
        assertTrue(!sheetXml.contains("4111 1111"))
        assertTrue(!Regex("""\d{13,19}""").containsMatchIn(sheetXml))

        // ---- 5. The transient PAN itself is dead ---------------------------
        val pan = TransientPan.createValidated(validVisa)
        pan.consume()
        var destroyed = false
        try {
            pan.consume()
        } catch (expected: IllegalStateException) {
            destroyed = true
        }
        assertTrue(destroyed)
    }

    /** A full PAN sneaking into ANY cell (even notes) must block the export. */
    @Test
    fun panInNotesCellBlocksExport() {
        val columns = ExcelExporter.APPROVED_COLUMNS.map { XlsxColumn(it.title, it.width, it.kind) }
        val writer = XlsxWriter("Card Records", columns)
        writer.addRow(
            listOf(
                CellValue.Text("001"),
                CellValue.Text("VISA"),
                CellValue.Text("**** **** **** 1234"),
                CellValue.Int32(1234),
                CellValue.Int32(12),
                CellValue.Int32(2029),
                CellValue.Text("MOHAMED SALAH ALI"),
                CellValue.DateTime(Instant.parse("2026-10-04T16:30:22Z")),
                // Attack payload hidden in a free-text field:
                CellValue.Text("reissued from 4012888888881881"),
            ),
        )
        val buffer = java.io.ByteArrayOutputStream()
        writer.writeTo(buffer)

        val verdict = validator.validate(
            buffer.toByteArray(),
            ExcelExporter.APPROVED_COLUMNS.map { it.title },
        )
        assertTrue(
            "PAN hidden in notes must be rejected",
            verdict is ExportSecurityValidator.Verdict.Rejected,
        )
    }

    private fun readEntry(xlsxBytes: ByteArray, entryName: String): String {
        ZipInputStream(ByteArrayInputStream(xlsxBytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == entryName) {
                    return zip.readBytes().toString(Charsets.UTF_8)
                }
                entry = zip.nextEntry
            }
        }
        throw AssertionError("entry not found: $entryName")
    }
}
