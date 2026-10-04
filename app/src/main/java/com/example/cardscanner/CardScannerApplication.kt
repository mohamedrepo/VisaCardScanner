package com.example.cardscanner

import android.app.Application
import com.example.cardscanner.data.AuditLogger
import com.example.cardscanner.data.CardDatabase
import com.example.cardscanner.data.CardRepository
import com.example.cardscanner.data.FleetRepository

/**
 * Application-scoped singletons. Everything is local and offline; there is no
 * network client, no analytics, no crash reporter and no ad SDK anywhere in
 * this process.
 */
class CardScannerApplication : Application() {

    lateinit var repository: CardRepository
        private set

    lateinit var fleetRepository: FleetRepository
        private set

    lateinit var auditLogger: AuditLogger
        private set

    override fun onCreate() {
        super.onCreate()
        val database = CardDatabase.get(this)
        repository = CardRepository(database.cardDao())
        auditLogger = AuditLogger(this, database.auditDao())
        fleetRepository = FleetRepository(database.fleetCardDao(), auditLogger)
    }
}
