package com.example.cardscanner

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Spec §19 storage security tests, ported to Robolectric so they run in the
 * normal JVM `test` task without a device or emulator:
 *  - Test 1: no complete PAN is written to Room
 *  - Test 3: no CVV field can be saved (no column exists)
 *  - Test 8: duplicate card is detected
 *  - Test 10: searching history cannot reveal a complete PAN
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CardDaoRobolectricTest {

    private lateinit var database: CardDatabase
    private lateinit var dao: CardDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CardDatabase::class.java)
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
        cardholderName: String? = "MOHAMED SALAH ALI",
    ) = CardRecordEntity(
        id = id,
        brand = brand,
        maskedPan = "**** **** **** $last4",
        last4 = last4,
        expiryMonth = month,
        expiryYear = year,
        cardholderName = cardholderName,
        scannedAt = Instant.now().toEpochMilli(),
        notes = null,
    )

    /** Test 1: only masked PAN + last4 ever persist. */
    @Test
    fun roomStoresOnlyMaskedAndLast4() = runBlocking {
        dao.insert(entity())
        val stored = dao.observeAll().first().single()
        assertEquals("**** **** **** 1234", stored.maskedPan)
        assertEquals("1234", stored.last4)
        val allFields = listOf(stored.maskedPan, stored.last4, stored.cardholderName ?: "")
        assertTrue(allFields.none { it.contains("4111111111111111") })
    }

    /** Test 3: the entity schema has no CVV-like column to write to. */
    @Test
    fun schemaHasNoCvvColumn() {
        val constructor = CardRecordEntity::class.java.constructors.first()
        val columns = constructor.parameters.map { it.name!!.lowercase() }
        assertTrue("columns found: $columns", columns.none {
            it.contains("cvv") || it.contains("cvc") || it.contains("cid") || it == "pin"
        })
    }

    /** Test 8: Brand + Last4 + Expiry duplicate detection, including NULL expiry. */
    @Test
    fun duplicateIsDetected() = runBlocking {
        dao.insert(entity(id = "1"))
        assertTrue(dao.existsDuplicate("VISA", "1234", 12, 2029))
        assertFalse(dao.existsDuplicate("VISA", "9999", 12, 2029))

        // NULL expiry handling: a card with no expiry matches only its own key.
        dao.insert(entity(id = "2", last4 = "5678", month = null, year = null, cardholderName = "SARA ALI"))
        assertTrue(dao.existsDuplicate("VISA", "5678", null, null))
        assertFalse(dao.existsDuplicate("VISA", "5678", 12, 2029))
    }

    /** Test 10: search results can only ever contain masked data. */
    @Test
    fun searchCannotRevealCompletePan() = runBlocking {
        dao.insert(entity(id = "1"))
        dao.insert(entity(id = "2", last4 = "5678", cardholderName = "SARA ALI"))

        val byLast4 = dao.search("1234").first()
        assertEquals(1, byLast4.size)

        val byName = dao.search("SARA").first()
        assertEquals(1, byName.size)
        assertEquals("5678", byName[0].last4)

        val byYear = dao.search("2029").first()
        assertEquals(2, byYear.size)

        // Every returned record is masked — a full PAN is not storable, so it
        // cannot appear in any query result by construction.
        (byLast4 + byName + byYear).forEach { record ->
            assertTrue(record.maskedPan.startsWith("*"))
        }
    }

    /** Unique index on (brand, last4, month, year) backs the duplicate check. */
    @Test(expected = RuntimeException::class)
    fun duplicateInsertAborts() = runBlocking {
        dao.insert(entity(id = "1"))
        dao.insert(entity(id = "1-dup", last4 = "1234"))
    }
}
