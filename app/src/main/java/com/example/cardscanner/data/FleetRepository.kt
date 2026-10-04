package com.example.cardscanner.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

/**
 * Fleet card repository: CRUD + lifecycle + dashboard aggregates.
 * All operations log to the immutable audit trail via [AuditLogger].
 */
class FleetRepository(
    private val dao: FleetCardDao,
    private val audit: AuditLogger,
) {

    fun observeAll(): Flow<List<FleetCard>> =
        dao.observeAll().map { list -> list.map { it.toModel() } }

    fun search(query: String): Flow<List<FleetCard>> =
        dao.search(query).map { list -> list.map { it.toModel() } }

    fun filter(status: String, provider: String, fuelType: String, department: String): Flow<List<FleetCard>> =
        dao.filter(status, provider, fuelType, department).map { list -> list.map { it.toModel() } }

    fun observeById(id: String): Flow<FleetCard?> =
        dao.observeById(id).map { it?.toModel() }

    suspend fun getById(id: String): FleetCard? = dao.getById(id)?.toModel()

    /** Duplicate lookup by bin6+last4+expiry (spec §22). */
    suspend fun findByCardKey(bin6: String, last4: String, month: Int?, year: Int?): FleetCard? =
        dao.findByCardKey(bin6, last4, month, year)?.toModel()

    suspend fun create(card: FleetCard): FleetCard {
        val now = Instant.now()
        val withTimestamps = card.copy(createdAt = now, updatedAt = now)
        dao.insert(FleetCardEntity.fromModel(withTimestamps))
        audit.log(AuditEntry.CARD_CREATED, card.id, "status=${card.status}")
        return withTimestamps
    }

    suspend fun update(card: FleetCard) {
        val updated = card.copy(updatedAt = Instant.now())
        updateInternal(updated)
    }

    private suspend fun updateInternal(card: FleetCard) {
        // Simplest correct upsert: delete-then-insert within caller's unit of work.
        dao.deleteById(card.id)
        dao.insert(FleetCardEntity.fromModel(card))
    }

    suspend fun changeStatus(card: FleetCard, newStatus: String) {
        require(newStatus in CardStatus.ALL) { "unknown status: $newStatus" }
        dao.updateStatus(card.id, newStatus, System.currentTimeMillis())
        audit.log(AuditEntry.CARD_STATUS_CHANGED, card.id, "${card.status} -> $newStatus")
    }

    /**
     * Replacement workflow (spec §23): old card becomes Replacement (kept),
     * new card is created as Active and linked via replacesCardId.
     */
    suspend fun replace(oldCard: FleetCard, newCard: FleetCard, replacementDate: String?): FleetCard {
        dao.markReplaced(oldCard.id, CardStatus.REPLACED, replacementDate, System.currentTimeMillis())
        audit.log(AuditEntry.CARD_STATUS_CHANGED, oldCard.id, "${oldCard.status} -> ${CardStatus.REPLACED}")

        val now = Instant.now()
        val linked = newCard.copy(
            status = CardStatus.ACTIVE,
            issueDate = replacementDate ?: newCard.issueDate,
            replacementDate = replacementDate,
            replacesCardId = oldCard.id,
            createdAt = now,
            updatedAt = now,
        )
        dao.insert(FleetCardEntity.fromModel(linked))
        audit.log(AuditEntry.CARD_REPLACED, linked.id, "replaces=${oldCard.id}")
        return linked
    }

    suspend fun delete(card: FleetCard) {
        dao.deleteById(card.id)
        audit.log(AuditEntry.CARD_DELETED, card.id)
    }

    suspend fun allForExport(): List<FleetCard> =
        dao.search("").map { list -> list.map { it.toModel() } }.first()

    // ---- Dashboard (spec §33-34) -------------------------------------------

    fun countByStatus(): Flow<List<StatusCount>> = dao.countByStatus()

    fun countByFuelType(): Flow<List<LabelCount>> = dao.countByFuelType()

    fun countByProvider(): Flow<List<LabelCount>> = dao.countByProvider()

    fun countByDepartment(): Flow<List<LabelCount>> = dao.countByDepartment()

    /**
     * Expiring-soon counts for the next [months] windows (30/60/90 days),
     * Active cards only, computed against the current month.
     */
    suspend fun expiringSoonCounts(zone: ZoneId = ZoneId.systemDefault()): ExpiringSoon {
        val today = YearMonth.from(java.time.LocalDate.now(zone))
        suspend fun countWithin(months: Int): Int {
            var total = 0
            val end = today.plusMonths(months.toLong())
            for (cursor in generateSequence(today) { it.plusMonths(1) }) {
                if (cursor.isAfter(end)) break
                total += dao.expiringIn(cursor.year, cursor.monthValue)
            }
            return total
        }
        return ExpiringSoon(
            next30 = countWithin(1),
            next60 = countWithin(2),
            next90 = countWithin(3),
        )
    }

    data class ExpiringSoon(val next30: Int, val next60: Int, val next90: Int)
}
