package com.example.cardscanner

import com.example.cardscanner.data.AuditEntry
import com.example.cardscanner.data.CardStatus
import com.example.cardscanner.data.FleetCard
import com.example.cardscanner.data.FleetCardFactory
import com.example.cardscanner.export.CellValue
import com.example.cardscanner.export.ExcelExporter
import com.example.cardscanner.export.EncryptedExport
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
 * v1.1 fleet feature tests: fleet fields, BIN display, lifecycle,
 * encrypted export round-trip, audit immutability contract.
 */
class FleetFeatureTest {

    private val validator = ExportSecurityValidator()

    private fun card(
        id: String = "f-1",
        last4: String = "1234",
        bin6: String = "401288",
        status: String = CardStatus.ACTIVE,
        fuelType: String? = "Diesel",
        provider: String? = "QNB",
        vehicle: String? = "VH-104",
        driver: String? = "AHMED ALI",
    ): FleetCard = FleetCard(
        id = id,
        bin6 = bin6,
        last4 = last4,
        maskedPan = "**** **** **** $last4",
        cardBrand = "VISA",
        cardholderName = "MOHAMED SALAH ALI",
        expiryMonth = 12,
        expiryYear = 2029,
        vehicleNumber = vehicle,
        plateNumber = "ABC-1234",
        driverName = driver,
        employeeId = "EMP-001",
        department = "Operations",
        fuelType = fuelType,
        provider = provider,
        status = status,
        issueDate = "01/10/2026",
        replacementDate = null,
        replacesCardId = null,
        notes = null,
        createdAt = Instant.parse("2026-10-04T16:30:22Z"),
        updatedAt = Instant.parse("2026-10-04T16:30:22Z"),
    )

    /** BIN display form is the fleet-standard masked identity. */
    @Test
    fun binDisplayFormat() {
        assertEquals("401288 •••• 1234", card().binDisplay)
    }

    /** Fleet fields flow through the factory into the entity intact. */
    @Test
    fun factoryCreatesFleetCardWithAllFields() {
        val created = FleetCardFactory.create(
            scan = FleetCardFactory.ScanInput(
                maskedPan = "**** **** **** 1234",
                last4 = "1234",
                bin6 = "401288",
                cardholderName = "MOHAMED SALAH ALI",
                expiryMonth = 12,
                expiryYear = 2029,
            ),
            fleet = FleetCardFactory.FleetFields(
                vehicleNumber = "VH-104",
                plateNumber = "ABC-1234",
                driverName = "AHMED ALI",
                fuelType = "Diesel",
                provider = "QNB",
            ),
        )
        assertEquals("VH-104", created.vehicleNumber)
        assertEquals("Diesel", created.fuelType)
        assertEquals(CardStatus.ACTIVE, created.status)
        assertEquals("401288", created.bin6)
    }

    /** Factory rejects malformed identifiers. */
    @Test(expected = IllegalArgumentException::class)
    fun factoryRejectsBadBin() {
        FleetCardFactory.create(
            FleetCardFactory.ScanInput("**** **** **** 1234", "1234", "40128", null, 12, 2029),
        )
    }

    /** Fleet export contains the new columns and fleet values. */
    @Test
    fun fleetExportContainsFleetColumnsAndValues() {
        val result = ExcelExporter.buildWorkbookPure(listOf(card()), validator)
        assertTrue(result is ExcelExporter.Result.Success)
        val sheet = readSheet((result as ExcelExporter.Result.Success).bytes)
        listOf(
            "Card BIN (First 6)", "Vehicle Number", "Plate Number", "Driver Name",
            "Employee ID", "Department", "Fuel Type", "Card Status",
        ).forEach { column ->
            assertTrue("missing column $column", sheet.contains(">$column</t>"))
        }
        assertTrue(sheet.contains("401288"))
        assertTrue(sheet.contains("VH-104"))
        assertTrue(sheet.contains("Diesel"))
        assertTrue(sheet.contains("Active"))
    }

    /** Encrypted export round-trip: decrypt returns the original xlsx. */
    @Test
    fun encryptedExportRoundTrip() {
        val xlsx = (ExcelExporter.buildWorkbookPure(listOf(card()), validator) as ExcelExporter.Result.Success).bytes
        val passphrase = "fleet-secret-42".toCharArray()
        val container = EncryptedExport.encrypt(xlsx, passphrase)
        passphrase.fill('0')

        assertFalse(container.contentEquals(xlsx))
        val decrypted = EncryptedExport.decrypt(container, "fleet-secret-42".toCharArray())
        assertTrue(decrypted.contentEquals(xlsx))
    }

    /** Wrong passphrase fails cleanly (GCM auth), never returns garbage. */
    @Test(expected = Exception::class)
    fun wrongPassphraseRejected() {
        val xlsx = (ExcelExporter.buildWorkbookPure(listOf(card()), validator) as ExcelExporter.Result.Success).bytes
        val container = EncryptedExport.encrypt(xlsx, "correct".toCharArray())
        EncryptedExport.decrypt(container, "wrong".toCharArray())
    }

    /** The audit log DAO contract: no update/delete methods exist. */
    @Test
    fun auditDaoIsAppendOnly() {
        val methods = AuditEntry::class.java.let {
            com.example.cardscanner.data.AuditDao::class.java.methods.map { m -> m.name }
        }
        assertTrue(methods.contains("insert"))
        assertFalse(methods.any { it.startsWith("update") || it.startsWith("delete") })
    }

    /** Statuses cover the spec vocabulary. */
    @Test
    fun allStatusesPresent() {
        assertEquals(
            listOf("Active", "Suspended", "Lost", "Stolen", "Expired", "Replacement", "Cancelled"),
            CardStatus.ALL,
        )
    }

    private fun readSheet(xlsxBytes: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(xlsxBytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "xl/worksheets/sheet1.xml") {
                    return zip.readBytes().toString(Charsets.UTF_8)
                }
                entry = zip.nextEntry
            }
        }
        throw AssertionError("sheet1.xml not found")
    }
}
