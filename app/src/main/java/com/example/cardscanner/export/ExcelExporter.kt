package com.example.cardscanner.export

import com.example.cardscanner.data.CardRecord
import com.example.cardscanner.data.FleetCard
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Builds the fleet `Card Records` workbook from local (masked) records and
 * writes it to a user-chosen location via the Storage Access Framework.
 *
 * SECURITY PIPELINE:
 *   records -> rows (BIN6/last-4 only) -> XlsxWriter bytes ->
 *   ExportSecurityValidator -> optional EncryptedExport wrap -> SAF target.
 *
 * v1.1: fleet columns added (vehicle, driver, department, status, ...).
 * There is still no code path that could place a complete PAN into the
 * workbook — the data model has no such field.
 */
class ExcelExporter(
    private val context: android.content.Context?,
    private val validator: ExportSecurityValidator = ExportSecurityValidator(),
) {

    data class Column(val title: String, val width: Double, val kind: ColumnKind) {
        fun toXlsx() = XlsxColumn(title, width, kind)
    }

    companion object {
        val SHEET_NAME = "Card Records"

        // v1.1 fleet export schema: BIN + last4 instead of a single masked column.
        val APPROVED_COLUMNS = listOf(
            Column("Record ID", 34.0, ColumnKind.IDENTIFIER),
            Column("Card Provider", 16.0, ColumnKind.TEXT),
            Column("Card Brand", 11.0, ColumnKind.TEXT),
            Column("Card BIN (First 6)", 16.0, ColumnKind.TEXT),
            Column("Last 4 Digits", 13.0, ColumnKind.NUMBER_INT),
            Column("Card Number (Display)", 18.0, ColumnKind.TEXT),
            Column("Cardholder Name", 26.0, ColumnKind.TEXT),
            Column("Expiration Month", 16.0, ColumnKind.NUMBER_INT),
            Column("Expiration Year", 15.0, ColumnKind.NUMBER_INT),
            Column("Vehicle Number", 15.0, ColumnKind.TEXT),
            Column("Plate Number", 14.0, ColumnKind.TEXT),
            Column("Driver Name", 20.0, ColumnKind.TEXT),
            Column("Employee ID", 14.0, ColumnKind.TEXT),
            Column("Department", 16.0, ColumnKind.TEXT),
            Column("Fuel Type", 14.0, ColumnKind.TEXT),
            Column("Card Status", 13.0, ColumnKind.TEXT),
            Column("Issue Date", 13.0, ColumnKind.TEXT),
            Column("Replacement Date", 17.0, ColumnKind.TEXT),
            Column("Replaces Card ID", 34.0, ColumnKind.IDENTIFIER),
            Column("Scanned Date", 20.0, ColumnKind.DATETIME),
            Column("Notes", 30.0, ColumnKind.TEXT),
        )

        private val fileNameFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")

        fun defaultFileName(now: LocalDateTime = LocalDateTime.now()): String =
            "Fleet_Fuel_Cards_${now.format(fileNameFormatter)}.xlsx"

        /** Context-free workbook builder + security scan (JVM-testable). */
        fun buildWorkbookPure(
            records: List<FleetCard>,
            validator: ExportSecurityValidator = ExportSecurityValidator(),
        ): Result {
            val columns = APPROVED_COLUMNS.map { it.toXlsx() }
            val writer = XlsxWriter(SHEET_NAME, columns)

            for (record in records) {
                writer.addRow(
                    listOf(
                        CellValue.Text(record.id),
                        CellValue.Text(record.provider ?: ""),
                        CellValue.Text(record.cardBrand),
                        CellValue.Text(record.bin6),
                        CellValue.Int32(record.last4.toInt()),
                        CellValue.Text(record.binDisplay),
                        CellValue.Text(record.cardholderName ?: ""),
                        CellValue.Int32(record.expiryMonth ?: -1),
                        CellValue.Int32(record.expiryYear ?: -1),
                        CellValue.Text(record.vehicleNumber ?: ""),
                        CellValue.Text(record.plateNumber ?: ""),
                        CellValue.Text(record.driverName ?: ""),
                        CellValue.Text(record.employeeId ?: ""),
                        CellValue.Text(record.department ?: ""),
                        CellValue.Text(record.fuelType ?: ""),
                        CellValue.Text(record.status),
                        CellValue.Text(record.issueDate ?: ""),
                        CellValue.Text(record.replacementDate ?: ""),
                        CellValue.Text(record.replacesCardId ?: ""),
                        CellValue.DateTime(record.updatedAt),
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

    sealed interface Result {
        data class Success(val bytes: ByteArray) : Result
        data class Rejected(val reason: String) : Result
    }

    /** Instance-level convenience delegating to the pure builder. */
    fun buildWorkbook(records: List<FleetCard>): Result =
        buildWorkbookPure(records, validator)

    /** Legacy v1.0 records (masked card inventory) export. */
    fun buildLegacyWorkbook(records: List<CardRecord>): Result =
        buildWorkbookPure(records.map { legacyToFleet(it) }, validator)

    private fun legacyToFleet(record: CardRecord): FleetCard = FleetCard(
        id = record.id,
        // Legacy v1.0 records carry no BIN: a zero placeholder keeps the
        // schema valid without inventing card data.
        bin6 = "000000",
        last4 = record.last4,
        maskedPan = record.maskedPan,
        cardBrand = record.brand,
        cardholderName = record.cardholderName,
        expiryMonth = record.expiryMonth,
        expiryYear = record.expiryYear,
        status = com.example.cardscanner.data.CardStatus.ACTIVE,
        notes = record.notes,
        createdAt = record.scannedAt,
        updatedAt = record.scannedAt,
    )

    /**
     * Streams previously validated bytes into the user-selected SAF target.
     */
    @Throws(IOException::class)
    fun writeToTarget(target: android.net.Uri, validatedBytes: ByteArray) {
        context?.contentResolver?.openOutputStream(target, "wt")?.use { stream ->
            stream.write(validatedBytes)
        } ?: throw IOException("Could not open export target")
    }
}
