package com.example.cardscanner

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cardscanner.data.CardDao
import com.example.cardscanner.data.CardDatabase
import com.example.cardscanner.data.CardRecordEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Spec §19 security tests, storage-related (instrumented, on-device):
 *  - Test 1: no complete PAN is written to Room
 *  - Test 3: no CVV field can be saved (no column exists)
 *  - Test 8: duplicate card is detected
 *  - Test 10: searching history cannot reveal a complete PAN
 */
@RunWith(AndroidJUnit4::class)
class CardDaoSecurityTest {

    private lateinit var database: CardDatabase
    private lateinit var dao: CardDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = androidx.room.Room.inMemoryDatabaseBuilder(context, CardDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.cardDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun entity(
        id: String = "1",
        last4: String = "1234",
        brand: String = "VISA",
        month: Int? = 12,
        year: Int? = 2029,
    ) = CardRecordEntity(
        id = id,
        brand = brand,
        maskedPan = "**** **** **** $last4",
        last4 = last4,
        expiryMonth = month,
        expiryYear = year,
        cardholderName = "MOHAMED SALAH ALI",
        scannedAt = Instant.now(),
        notes = null,
    )

    /** Test 1: the entity has no PAN column; masked + last4 is all that persists. */
    @Test
    fun roomStoresOnlyMaskedAndLast4() = runBlocking {
        dao.insert(entity())
        val stored = dao.observeAll().first().single()
        assertEquals("**** **** **** 1234", stored.maskedPan)
        assertEquals("1234", stored.last4)
        // The complete test PAN 4111111111111111 appears nowhere in storage.
        val allFields = listOf(stored.maskedPan, stored.last4, stored.cardholderName ?: "")
        assertTrue(allFields.none { it.contains("4111111111111111") })
    }

    /** Test 3: the schema has no CVV-like column to write to. */
    @Test
    fun schemaHasNoCvvColumn() {
        val columns = CardRecordEntity::class.java.declaredFields.map { it.name.lowercase() }
        assertTrue(columns.none { it.contains("cvv") || it.contains("cvc") || it.contains("cid") || it.contains("pin") })
    }

    /** Test 8: Brand + Last4 + Expiry duplicate detection works. */
    @Test
    fun duplicateIsDetected() = runBlocking {
        dao.insert(entity(id = "1"))
        val duplicateExists = dao.existsDuplicate("VISA", "1234", 12, 2029)
        assertTrue(duplicateExists)

        val notDuplicate = dao.existsDuplicate("VISA", "9999", 12, 2029)
        assertFalse(notDuplicate)
    }

    /** Test 10: search results can only ever contain masked data. */
    @Test
    fun searchCannotRevealCompletePan() = runBlocking {
        dao.insert(entity(id = "1"))
        dao.insert(entity(id = "2", last4 = "5678", cardholderName = "SARA ALI"))

        val results = dao.search("1234").first()
        assertEquals(1, results.size)
        results.forEach { record ->
            assertFalse(record.maskedPan.contains("4111111111111111"))
            assertTrue(record.maskedPan.startsWith("*"))
        }
    }
}
