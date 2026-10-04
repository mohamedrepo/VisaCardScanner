package com.example.cardscanner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CardRecordEntity::class, FleetCardEntity::class, AuditEntry::class],
    version = 2,
    exportSchema = true,
)
abstract class CardDatabase : RoomDatabase() {

    abstract fun cardDao(): CardDao
    abstract fun fleetCardDao(): FleetCardDao
    abstract fun auditDao(): AuditDao

    companion object {
        @Volatile
        private var instance: CardDatabase? = null

        /** v1 -> v2: add fleet tables; legacy card_records preserved untouched. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS fleet_cards (
                        id TEXT NOT NULL PRIMARY KEY,
                        bin6 TEXT NOT NULL,
                        last4 TEXT NOT NULL,
                        maskedPan TEXT NOT NULL,
                        cardBrand TEXT NOT NULL,
                        cardholderName TEXT,
                        expiryMonth INTEGER,
                        expiryYear INTEGER,
                        vehicleNumber TEXT,
                        plateNumber TEXT,
                        driverName TEXT,
                        employeeId TEXT,
                        department TEXT,
                        fuelType TEXT,
                        provider TEXT,
                        status TEXT NOT NULL,
                        issueDate TEXT,
                        replacementDate TEXT,
                        replacesCardId TEXT,
                        notes TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_fleet_cards_bin6_last4_expiryMonth_expiryYear ON fleet_cards (bin6, last4, expiryMonth, expiryYear)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_fleet_cards_last4 ON fleet_cards (last4)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_fleet_cards_vehicleNumber ON fleet_cards (vehicleNumber)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_fleet_cards_plateNumber ON fleet_cards (plateNumber)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_fleet_cards_driverName ON fleet_cards (driverName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_fleet_cards_employeeId ON fleet_cards (employeeId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_fleet_cards_provider ON fleet_cards (provider)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_fleet_cards_status ON fleet_cards (status)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS audit_log (
                        seq INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        event TEXT NOT NULL,
                        cardId TEXT,
                        detail TEXT
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_log_timestamp ON audit_log (timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_log_cardId ON audit_log (cardId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_log_event ON audit_log (event)")
            }
        }

        fun get(context: Context): CardDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CardDatabase::class.java,
                    "card_records.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
