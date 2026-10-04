package com.example.cardscanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.cardscanner.security.SensitiveDataGuard
import com.example.cardscanner.ui.CardScannerApp

/**
 * Single-activity host. FLAG_SECURE is applied globally: every screen in this
 * app deals with card-derived data, so screenshots and screen recording are
 * blocked app-wide.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SensitiveDataGuard.applySecureFlag(window)
        enableEdgeToEdge()

        val repository = (application as CardScannerApplication).repository
        val fleetRepository = (application as CardScannerApplication).fleetRepository

        setContent {
            CardScannerApp(repository = repository, fleetRepository = fleetRepository)
        }
    }
}
