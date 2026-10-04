package com.example.cardscanner

import android.app.Application
import com.example.cardscanner.data.CardDatabase
import com.example.cardscanner.data.CardRepository

/**
 * Application-scoped singletons. Everything is local and offline; there is no
 * network client, no analytics, no crash reporter and no ad SDK anywhere in
 * this process.
 */
class CardScannerApplication : Application() {

    lateinit var repository: CardRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val database = CardDatabase.get(this)
        repository = CardRepository(database.cardDao())
    }
}
