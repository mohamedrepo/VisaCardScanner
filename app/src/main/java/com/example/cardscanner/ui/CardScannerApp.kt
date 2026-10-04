package com.example.cardscanner.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.cardscanner.data.CardRepository
import com.example.cardscanner.ui.confirmation.ConfirmationRoute
import com.example.cardscanner.ui.history.HistoryRoute
import com.example.cardscanner.ui.scanner.ScanPhase
import com.example.cardscanner.ui.scanner.ScannerRoute

/** App destinations. */
object Destinations {
    const val SCANNER = "scanner"
    const val CONFIRMATION = "confirmation"
    const val HISTORY = "history"
}

/**
 * Navigation host.
 *
 * The pending scan result lives in the shared, activity-scoped ViewModels — it
 * is never passed through navigation arguments or Intent extras, so card-derived
 * data cannot leak into saved instance state or system UI.
 */
@Composable
fun CardScannerApp(repository: CardRepository) {
    val navController = rememberNavController()
    val viewModel = rememberAppViewModel(repository)

    NavHost(navController = navController, startDestination = Destinations.SCANNER) {
        composable(Destinations.SCANNER) {
            ScannerRoute(
                viewModel = viewModel.scanner,
                onOpenHistory = { navController.navigate(Destinations.HISTORY) },
                onScanAgain = { viewModel.scanner.beginScan() },
            )
        }
        composable(Destinations.CONFIRMATION) {
            ConfirmationRoute(
                viewModel = viewModel,
                onSaved = {
                    navController.navigate(Destinations.HISTORY) {
                        popUpTo(Destinations.SCANNER) { inclusive = false }
                    }
                },
            )
        }
        composable(Destinations.HISTORY) {
            HistoryRoute(
                viewModel = viewModel.history,
                onBack = { navController.popBackStack() },
            )
        }
    }

    // Result available → move to the confirmation screen.
    val phase = viewModel.scanner.uiState.phase
    LaunchedEffect(phase) {
        if (phase == ScanPhase.CONFIRM && navController.currentDestination?.route != Destinations.CONFIRMATION) {
            navController.navigate(Destinations.CONFIRMATION)
        }
    }
}
