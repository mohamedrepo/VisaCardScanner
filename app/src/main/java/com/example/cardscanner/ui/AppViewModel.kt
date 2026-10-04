package com.example.cardscanner.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.example.cardscanner.data.CardRepository
import com.example.cardscanner.data.FleetRepository
import com.example.cardscanner.ui.confirmation.ConfirmationViewModel
import com.example.cardscanner.ui.history.HistoryViewModel
import com.example.cardscanner.ui.scanner.ScannerViewModel
import kotlinx.coroutines.CoroutineScope

/**
 * Activity-scoped holder for all screen ViewModels. Fleet screens share
 * [fleetRepository]; scan flows still use the v1.0 masked [repository].
 */
class AppViewModel(
    val repository: CardRepository,
    val fleetRepository: FleetRepository,
    val scanner: ScannerViewModel,
    val confirmation: ConfirmationViewModel,
    val history: HistoryViewModel,
)

@Composable
fun rememberAppViewModel(
    repository: CardRepository,
    fleetRepository: FleetRepository,
): AppViewModel {
    val scope: CoroutineScope = rememberCoroutineScope()
    val appContext: Context = LocalContext.current.applicationContext
    return remember(repository, fleetRepository) {
        val scannerVm = ScannerViewModel(repository)
        val confirmationVm = ConfirmationViewModel(repository, scannerVm, scope)
        val historyVm = HistoryViewModel(fleetRepository, appContext)
        AppViewModel(repository, fleetRepository, scannerVm, confirmationVm, historyVm)
    }
}
