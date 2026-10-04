package com.example.cardscanner.export

import android.content.Context
import android.net.Uri
import com.example.cardscanner.data.CardRecord
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Builds the `Card Records` workbook from local (safe) records and writes it to
 * a user-chosen location via the Storage Access Framework.
 *
 * SECURITY PIPELINE:
 *   records → rows (masked/last-4 only) → XlsxWriter bytes →
 *   ExportSecurityValidator → only then is anything written to disk.
 *
 * There is no code path here — or anywhere in the app — that could place a
 * complete PAN into the workbook.
 */
class ExcelExporter(
    private val context: Context,
    private val validator: ExportSecurityValidator = ExportSecurityValidator(),
) {

    data class Column(val title: String, val width: Double, val kind: ColumnKind) {
        fun toXlsx() = XlsxColumn(title, width, kind)
    }

    sealed interface Result {
        data class Success(val bytes: ByteArray) : Result
        data class Rejected(val reason: String) : Result
    }

    /**
     * Serializes records and runs the security scan. The bytes are kept in RAM
     * only and returned so the caller can stream them to the SAF target.
     *
     * Pure function: no Android dependency, directly unit-testable on the JVM.
     */
    fun buildWorkbook(records: List<CardRecord>): Result =
        buildWorkbookPure(records, validator)

    /**
     * Streams previously validated bytes into the user-selected SAF target.
     */
    @Throws(IOException::class)
    fun writeToTarget(target: Uri, validatedBytes: ByteArray) {
        context.contentResolver.openOutputStream(target, "wt")?.use { stream ->
            stream.write(validatedBytes)
        } ?: throw IOException("Could not open export target")
    }

    companion object {
        val SHEET_NAME = "Card Records"

        // The single source of truth for the approved export schema.
        val APPROVED_COLUMNS = listOf(
            Column("Record ID", 34.0, ColumnKind.IDENTIFIER),
            Column("Card Brand", 12.0, ColumnKind.TEXT),
            Column("Masked Card Number", 22.0, ColumnKind.TEXT),
            Column("Last 4 Digits", 13.0, ColumnKind.NUMBER_INT),
            Column("Expiration Month", 17.0, ColumnKind.NUMBER_INT),
            Column("Expiration Year", 16.0, ColumnKind.NUMBER_INT),
            Column("Cardholder Name", 26.0, ColumnKind.TEXT),
            Column("Scanned Date", 20.0, ColumnKind.DATETIME),
            Column("Notes", 30.0, ColumnKind.TEXT),
        )

        private val fileNameFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")

        fun defaultFileName(now: LocalDateTime = LocalDateTime.now()): String =
            "Card_Records_${now.format(fileNameFormatter)}.xlsx"

        /** Context-free workbook builder + security scan (JVM-testable). */
        fun buildWorkbookPure(
            records: List<CardRecord>,
            validator: ExportSecurityValidator = ExportSecurityValidator(),
        ): Result {
            val columns = APPROVED_COLUMNS.map { it.toXlsx() }
            val writer = XlsxWriter(SHEET_NAME, columns)

            for (record in records) {
                writer.addRow(
                    listOf(
                        CellValue.Text(record.id),
                        CellValue.Text(record.brand),
                        CellValue.Text(record.maskedPan),
                        CellValue.Int32(record.last4.toInt()),
                        CellValue.Int32(record.expiryMonth ?: -1),
                        CellValue.Int32(record.expiryYear ?: -1),
                        CellValue.Text(record.cardholderName ?: ""),
                        CellValue.DateTime(record.scannedAt),
                        CellValue.Text(record.notes ?: ""),
                    ),
                )
            }

            val buffer = java.io.ByteArrayOutputStream()
            writer.writeTo(buffer)
            val bytes = buffer.toByteArray()

            return when (val verdict = validator.validate(bytes, APPROVED_COLUMNS.map { it.title })) {
                is ExportSecurityValidator.Verdict.Clean -> Result.Success(bytes)
                is ExportSecurityValidator.Verdict.Rejected -> Result.Rejected(verdict.reason)
            }
        }
    }
}
