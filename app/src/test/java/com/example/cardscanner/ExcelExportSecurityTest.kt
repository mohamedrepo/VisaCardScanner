package com.example.cardscanner

import com.example.cardscanner.data.CardRecord
import com.example.cardscanner.export.CellValue
import com.example.cardscanner.export.ExcelExporter
import com.example.cardscanner.export.ExportSecurityValidator
import com.example.cardscanner.export.XlsxColumn
import com.example.cardscanner.export.XlsxWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.zip.ZipInputStream

/**
 * Spec §19 security tests, export-related:
 *  - Test 2: no complete PAN is included in Excel
 *  - Test 9: Excel export contains only approved columns
 *  - Regression (v1.0.1): legitimate exports must never be falsely rejected
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

    private fun assertClean(result: ExcelExporter.Result): ExcelExporter.Result.Success {
        assertTrue("expected export to succeed but got: $result", result is ExcelExporter.Result.Success)
        return result as ExcelExporter.Result.Success
    }

    /** Test 2 + regression: a normal, legitimate export must pass the scan. */
    @Test
    fun legitimateExportPassesSecurityScan() {
        val verdict = validator.validate(
            assertClean(build(listOf(record()))).bytes,
            ExcelExporter.APPROVED_COLUMNS.map { it.title },
        )
        assertTrue(verdict is ExportSecurityValidator.Verdict.Clean)
    }

    /** Regression: many records with realistic timestamps must all pass. */
    @Test
    fun bulkRealisticExportPassesSecurityScan() {
        val records = (1..50).map { i ->
            record(last4 = "%04d".format(i % 10000)).copy(
                id = "rec-$i",
                scannedAt = Instant.parse("2026-10-04T16:30:22Z").plusSeconds(i * 3600L),
                notes = if (i % 3 == 0) "Card #5321 issued 2021-2026" else null,
            )
        }
        val verdict = validator.validate(
            assertClean(build(records)).bytes,
            ExcelExporter.APPROVED_COLUMNS.map { it.title },
        )
        assertTrue(verdict is ExportSecurityValidator.Verdict.Clean)
    }

    /** Test 2b: a full PAN typed as text in a cell must be rejected. */
    @Test
    fun workbookContainingFullPanAsTextIsRejected() {
        val verdict = validator.validate(
            workbookWithText("4111111111111111"),
            ExcelExporter.APPROVED_COLUMNS.map { it.title },
        )
        assertTrue(verdict is ExportSecurityValidator.Verdict.Rejected)
    }

    /** Test 2c: a grouped PAN with separators must be rejected. */
    @Test
    fun workbookContainingGroupedPanIsRejected() {
        val verdict = validator.validate(
            workbookWithText("4111 1111 1111 1111"),
            ExcelExporter.APPROVED_COLUMNS.map { it.title },
        )
        assertTrue(verdict is ExportSecurityValidator.Verdict.Rejected)
    }

    /** Test 9: workbook columns are exactly the approved set. */
    @Test
    fun excelExportContainsOnlyApprovedColumns() {
        val bytes = assertClean(build(listOf(record()))).bytes

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
        val bytes = assertClean(build(listOf(record()))).bytes
        val sheetText = readSheetText(bytes)
        assertTrue(sheetText.contains("**** **** **** 1234"))
    }

    /** Reads the sheet1 XML out of the workbook archive (it is deflate-compressed). */
    private fun readSheetText(xlsxBytes: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(xlsxBytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "xl/worksheets/sheet1.xml") {
                    return zip.readBytes().toString(Charsets.UTF_8)
                }
                entry = zip.nextEntry
            }
        }
        throw AssertionError("sheet1.xml not found in workbook")
    }

    /** Helper: build a valid workbook whose Card Number cell carries arbitrary text. */
    private fun workbookWithText(cardNumberText: String): ByteArray {
        val columns = ExcelExporter.APPROVED_COLUMNS.map {
            XlsxColumn(it.title, it.width, it.kind)
        }
        val writer = XlsxWriter("Card Records", columns)
        writer.addRow(
            listOf(
                CellValue.Text("001"),
                CellValue.Text("VISA"),
                CellValue.Text(cardNumberText),
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
        return buffer.toByteArray()
    }
}
