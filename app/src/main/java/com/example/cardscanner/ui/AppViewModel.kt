package com.example.cardscanner.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import com.example.cardscanner.data.CardRepository
import com.example.cardscanner.ui.confirmation.ConfirmationViewModel
import com.example.cardscanner.ui.history.HistoryViewModel
import com.example.cardscanner.ui.scanner.ScannerViewModel
import kotlinx.coroutines.CoroutineScope

/**
 * Factory for the three screen ViewModels sharing one activity-scoped state
 * holder ([AppViewModel]) so a scan result can flow Scanner → Confirmation
 * without ever touching Intent extras or saved state.
 */
class AppViewModel(
    val repository: CardRepository,
    val scanner: ScannerViewModel,
    val confirmation: ConfirmationViewModel,
    val history: HistoryViewModel,
)

@Composable
fun rememberAppViewModel(repository: CardRepository): AppViewModel {
    val scope: CoroutineScope = rememberCoroutineScope()
    val appContext: Context = LocalContext.current.applicationContext
    return remember {
        val scannerVm = ScannerViewModel(repository)
        val confirmationVm = ConfirmationViewModel(repository, scannerVm, scope)
        val historyVm = HistoryViewModel(repository, appContext)
        AppViewModel(repository, scannerVm, confirmationVm, historyVm)
    }
}
