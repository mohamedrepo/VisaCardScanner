package com.example.cardscanner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Fleet card DAO. All queries operate on masked data + fleet fields only.
 */
@Dao
interface FleetCardDao {

    @Query("SELECT * FROM fleet_cards ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<FleetCardEntity>>

    @Query("SELECT * FROM fleet_cards WHERE id = :id")
    suspend fun getById(id: String): FleetCardEntity?

    @Query("SELECT * FROM fleet_cards WHERE id = :id")
    fun observeById(id: String): Flow<FleetCardEntity?>

    /**
     * Search across identification and fleet fields. The `query` never matches
     * on anything beyond bin6/last4 because no other card-number column exists.
     */
    @Query(
        """
        SELECT * FROM fleet_cards
        WHERE (:q = ''
           OR last4 LIKE '%' || :q || '%'
           OR bin6 LIKE '%' || :q || '%'
           OR UPPER(cardholderName) LIKE '%' || UPPER(:q) || '%'
           OR UPPER(vehicleNumber) LIKE '%' || UPPER(:q) || '%'
           OR UPPER(plateNumber) LIKE '%' || UPPER(:q) || '%'
           OR UPPER(driverName) LIKE '%' || UPPER(:q) || '%'
           OR UPPER(employeeId) LIKE '%' || UPPER(:q) || '%'
           OR UPPER(department) LIKE '%' || UPPER(:q) || '%'
           OR UPPER(provider) LIKE '%' || UPPER(:q) || '%'
           OR UPPER(fuelType) LIKE '%' || UPPER(:q) || '%'
           OR UPPER(status) LIKE '%' || UPPER(:q) || '%')
        ORDER BY updatedAt DESC
        """
    )
    fun search(q: String): Flow<List<FleetCardEntity>>

    @Query(
        """
        SELECT * FROM fleet_cards
        WHERE (:status = '' OR status = :status)
          AND (:provider = '' OR provider = :provider)
          AND (:fuelType = '' OR fuelType = :fuelType)
          AND (:department = '' OR department = :department)
        ORDER BY updatedAt DESC
        """
    )
    fun filter(status: String, provider: String, fuelType: String, department: String): Flow<List<FleetCardEntity>>

    /** Duplicate key: bin6 + last4 + expiry. */
    @Query(
        """
        SELECT * FROM fleet_cards
        WHERE bin6 = :bin6 AND last4 = :last4
          AND ((expiryMonth IS NULL AND :month IS NULL) OR expiryMonth = :month)
          AND ((expiryYear IS NULL AND :year IS NULL) OR expiryYear = :year)
        LIMIT 1
        """
    )
    suspend fun findByCardKey(bin6: String, last4: String, month: Int?, year: Int?): FleetCardEntity?

    @Query("SELECT COUNT(*) FROM fleet_cards")
    suspend fun count(): Int

    @Query("SELECT status AS status, COUNT(*) AS n FROM fleet_cards GROUP BY status")
    fun countByStatus(): Flow<List<StatusCount>>

    @Query("SELECT fuelType AS label, COUNT(*) AS n FROM fleet_cards WHERE fuelType IS NOT NULL GROUP BY fuelType")
    fun countByFuelType(): Flow<List<LabelCount>>

    @Query("SELECT provider AS label, COUNT(*) AS n FROM fleet_cards WHERE provider IS NOT NULL GROUP BY provider")
    fun countByProvider(): Flow<List<LabelCount>>

    @Query("SELECT department AS label, COUNT(*) AS n FROM fleet_cards WHERE department IS NOT NULL GROUP BY department")
    fun countByDepartment(): Flow<List<LabelCount>>

    @Query(
        """
        SELECT COUNT(*) FROM fleet_cards
        WHERE expiryYear = :year AND expiryMonth = :month AND status = 'Active'
        """
    )
    suspend fun expiringIn(year: Int, month: Int): Int

    @Insert
    suspend fun insert(card: FleetCardEntity)

    @Query("UPDATE fleet_cards SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long)

    @Query("UPDATE fleet_cards SET status = :status, replacementDate = :replacementDate, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markReplaced(id: String, status: String, replacementDate: String?, updatedAt: Long)

    @Query("DELETE FROM fleet_cards WHERE id = :id")
    suspend fun deleteById(id: String)
}
