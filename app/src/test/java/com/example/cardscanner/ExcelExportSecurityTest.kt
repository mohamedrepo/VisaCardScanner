package com.example.cardscanner

import com.example.cardscanner.data.CardRecord
import com.example.cardscanner.export.CellValue
import com.example.cardscanner.export.ExcelExporter
import com.example.cardscanner.export.ExportSecurityValidator
import com.example.cardscanner.export.XlsxColumn
import com.example.cardscanner.export.XlsxWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.zip.ZipInputStream

/**
 * Spec §19 security tests, export-related:
 *  - Test 2: no complete PAN is included in Excel
 *  - Test 9: Excel export contains only approved columns
 */
class ExcelExportSecurityTest {

    private val validator = ExportSecurityValidator()

    private fun record(last4: String = "1234"): CardRecord = CardRecord(
        id = "001",
        brand = "VISA",
        maskedPan = "**** **** **** $last4",
        last4 = last4,
        expiryMonth = 12,
        expiryYear = 2029,
        cardholderName = "MOHAMED SALAH ALI",
        scannedAt = Instant.parse("2026-10-04T16:30:22Z"),
        notes = null,
    )

    private fun build(records: List<CardRecord>): ExcelExporter.Result =
        ExcelExporter.buildWorkbookPure(records, validator)

    /** Test 2: generated workbook must not contain a 13-19 digit PAN-like run. */
    @Test
    fun noCompletePanIsWrittenToExcel() {
        val result = build(listOf(record()))
        assertTrue(result is ExcelExporter.Result.Success)
        val bytes = (result as ExcelExporter.Result.Success).bytes

        val text = bytes.toString(Charsets.UTF_8)
        assertFalse(
            "workbook contains a PAN-like digit run",
            Regex("""\d{13,19}""").containsMatchIn(text),
        )

        // And the validator agrees the file is clean.
        val verdict = validator.validate(bytes, ExcelExporter.APPROVED_COLUMNS.map { it.title })
        assertTrue(verdict is ExportSecurityValidator.Verdict.Clean)
    }

    /** Test 2b: a workbook that somehow carries a full PAN must be rejected. */
    @Test
    fun workbookContainingPanLikeSequenceIsRejected() {
        val columns = ExcelExporter.APPROVED_COLUMNS.map {
            XlsxColumn(it.title, it.width, it.kind)
        }
        val writer = XlsxWriter("Card Records", columns)
        writer.addRow(
            listOf(
                CellValue.Text("001"),
                CellValue.Text("VISA"),
                CellValue.Text("4111111111111111"), // attack payload
                CellValue.Int32(1234),
                CellValue.Int32(12),
                CellValue.Int32(2029),
                CellValue.Text("ATTACKER"),
                CellValue.DateTime(Instant.parse("2026-10-04T16:30:22Z")),
                CellValue.Text(""),
            ),
        )
        val buffer = java.io.ByteArrayOutputStream()
        writer.writeTo(buffer)

        val verdict = validator.validate(
            buffer.toByteArray(),
            ExcelExporter.APPROVED_COLUMNS.map { it.title },
        )
        assertTrue(verdict is ExportSecurityValidator.Verdict.Rejected)
    }

    /** Test 9: workbook columns are exactly the approved set, in order. */
    @Test
    fun excelExportContainsOnlyApprovedColumns() {
        val result = build(listOf(record()))
        val bytes = (result as ExcelExporter.Result.Success).bytes

        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            var sheetFound = false
            while (entry != null) {
                if (entry.name == "xl/worksheets/sheet1.xml") {
                    sheetFound = true
                    val xml = zip.readBytes().toString(Charsets.UTF_8)
                    ExcelExporter.APPROVED_COLUMNS.forEach { column ->
                        assertTrue(
                            "missing column ${column.title}",
                            xml.contains(">${column.title}</t>"),
                        )
                    }
                    val colCount = Regex("<col ").findAll(xml).count()
                    assertEquals(ExcelExporter.APPROVED_COLUMNS.size, colCount)
                }
                entry = zip.nextEntry
            }
            assertTrue(sheetFound)
        }
    }

    /** Test 9b: the masked form, not digits, is what lands in the sheet. */
    @Test
    fun maskedPanAppearsInWorkbook() {
        val result = build(listOf(record()))
        val bytes = (result as ExcelExporter.Result.Success).bytes
        val text = bytes.toString(Charsets.UTF_8)
        assertTrue(text.contains("**** **** **** 1234"))
    }
}
