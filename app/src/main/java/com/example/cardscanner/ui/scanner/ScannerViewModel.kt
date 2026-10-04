package com.example.cardscanner.ui.scanner

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardscanner.camera.CardDetection
import com.example.cardscanner.camera.FrameQuality
import com.example.cardscanner.data.CardRepository
import com.example.cardscanner.ocr.CardOcrEngine
import com.example.cardscanner.ocr.OcrResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Scanner state machine:
 * Ready → Detecting → Stable → Scanning → Processing → Confirm/Saved.
 */
enum class ScanPhase {
    READY, // camera starting
    DETECTING, // looking for a card
    HOLD_STILL, // card found, waiting for stability
    READING, // auto/manual capture started
    PROCESSING, // OCR running
    CONFIRM, // result available
    ERROR, // terminal error for this pass
}

data class ScannerUiState(
    val phase: ScanPhase = ScanPhase.READY,
    val detection: CardDetection? = null,
    val lastError: String? = null,
    val torchOn: Boolean = false,
    val showUnsupportedDialog: Boolean = false,
) {
    val confidence: FrameQuality
        get() = detection?.quality ?: FrameQuality.POOR
}

/**
 * Holds the scan pipeline state. The OCR result ([OcrResult]) contains SAFE
 * fields only — the complete PAN was destroyed inside [CardOcrEngine.analyze]
 * before this class ever saw the result.
 */
class ScannerViewModel(
    private val repository: CardRepository,
) : ViewModel() {

    var uiState by mutableStateOf(ScannerUiState())
        private set

    var pendingResult by mutableStateOf<OcrResult?>(null)
        private set

    /** Set by the scanner screen when the OCR engine finished. */
    var ocrEngine: CardOcrEngine? = null

    private var processingJob: Job? = null

    fun onDetection(detection: CardDetection?) {
        val phase = uiState.phase
        if (phase == ScanPhase.READING || phase == ScanPhase.PROCESSING || phase == ScanPhase.CONFIRM) {
            return
        }
        val nextPhase = when {
            detection == null -> ScanPhase.DETECTING
            detection.quality == FrameQuality.POOR -> ScanPhase.DETECTING
            else -> ScanPhase.HOLD_STILL
        }
        uiState = uiState.copy(detection = detection, phase = nextPhase)
    }

    /** Called when the StabilityTracker declared the card stable. */
    fun onStable() {
        if (uiState.phase == ScanPhase.HOLD_STILL) {
            uiState = uiState.copy(phase = ScanPhase.READING)
        }
    }

    fun onManualCaptureRequested() {
        if (uiState.phase in listOf(ScanPhase.DETECTING, ScanPhase.HOLD_STILL, ScanPhase.READY)) {
            uiState = uiState.copy(phase = ScanPhase.READING)
        }
    }

    fun onTorchToggled(on: Boolean) {
        uiState = uiState.copy(torchOn = on)
    }

    /**
     * Runs OCR on the captured bitmap. The bitmap is recycled immediately after
     * recognition; nothing writes it to disk first.
     */
    fun processCapturedBitmap(bitmap: Bitmap) {
        processingJob?.cancel()
        uiState = uiState.copy(phase = ScanPhase.PROCESSING)
        processingJob = viewModelScope.launch {
            val engine = ocrEngine ?: return@launch
            val result = withContext(Dispatchers.Default) {
                try {
                    engine.analyze(bitmap)
                } finally {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            }
            handleOcrResult(result)
        }
    }

    /** Capture itself failed (camera busy, frame dropped): back to guiding. */
    fun onCaptureFailed() {
        uiState = uiState.copy(phase = ScanPhase.DETECTING, lastError = null)
    }

    private fun handleOcrResult(result: OcrResult) {
        when {
            !result.panFound -> {
                pendingResult = null
                uiState = uiState.copy(
                    phase = ScanPhase.ERROR,
                    lastError = "error_card_not_detected",
                )
            }

            result.safeData == null -> {
                val unsupported = result.panFailure == com.example.cardscanner.ocr.PanResult.Reason.NOT_VISA
                pendingResult = null
                if (unsupported) {
                    uiState = uiState.copy(
                        phase = ScanPhase.ERROR,
                        showUnsupportedDialog = true,
                        lastError = "error_unsupported_card",
                    )
                } else {
                    uiState = uiState.copy(
                        phase = ScanPhase.ERROR,
                        lastError = if (result.panFailure == com.example.cardscanner.ocr.PanResult.Reason.LUHN_FAILED) {
                            "error_luhn_failure"
                        } else {
                            "error_invalid_card"
                        },
                    )
                }
            }

            else -> {
                pendingResult = result
                uiState = uiState.copy(phase = ScanPhase.CONFIRM, lastError = null)
            }
        }
    }

    /** User chose Rescan, or dismissed the error: go back to detecting. */
    fun beginScan() {
        pendingResult = null
        uiState = ScannerUiState(
            phase = ScanPhase.DETECTING,
            torchOn = uiState.torchOn,
        )
    }

    fun dismissUnsupportedDialog() {
        uiState = uiState.copy(showUnsupportedDialog = false)
    }

    fun consumeError() {
        uiState = uiState.copy(lastError = null)
    }
}
