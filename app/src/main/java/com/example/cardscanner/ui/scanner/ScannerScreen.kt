package com.example.cardscanner.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.cardscanner.R
import com.example.cardscanner.camera.CardCameraManager
import com.example.cardscanner.camera.CardDetector
import com.example.cardscanner.camera.FrameQuality
import com.example.cardscanner.camera.ImageProcessor
import com.example.cardscanner.camera.StabilityTracker
import com.example.cardscanner.ocr.CardOcrEngine
import kotlinx.coroutines.delay

/**
 * Scanner screen: live camera, card-frame guidance, automatic and manual
 * capture, and hand-off to the confirmation screen.
 *
 * SECURITY: frames exist in RAM only. The captured bitmap is recycled right
 * after OCR and never written anywhere.
 */
@Composable
fun ScannerRoute(
    viewModel: ScannerViewModel,
    onOpenHistory: () -> Unit,
    onScanAgain: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state = viewModel.uiState

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val detector = remember { CardDetector() }
    val tracker = remember { StabilityTracker() }
    val ocrEngine = remember { CardOcrEngine() }
    val cameraManager = remember {
        CardCameraManager(context) { proxy ->
            // Live analysis path: feed the detector, then close the frame.
            analyzeProxy(proxy, detector) { detection ->
                tracker.onFrame(detection)
                viewModel.onDetection(detection)
                if (tracker.hasReachedStability()) viewModel.onStable()
            }
        }
    }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraBound by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        viewModel.ocrEngine = ocrEngine
        onDispose {
            ocrEngine.close()
            cameraManager.release()
        }
    }

    LaunchedEffect(previewView, hasPermission) {
        val view = previewView ?: return@LaunchedEffect
        if (!hasPermission) return@LaunchedEffect
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(view.surfaceProvider)
        }
        cameraManager.start(lifecycleOwner, preview) { bound -> cameraBound = bound }
    }

    if (!hasPermission) {
        PermissionRationale(onGrant = { permissionLauncher.launch(Manifest.permission.CAMERA) })
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    previewView = this
                }
            },
        )

        CardFrameOverlay(state = state)

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.scan_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Spacer(Modifier.height(12.dp))
            StatusChip(phase = state.phase, quality = state.confidence)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { viewModel.onTorchToggled(cameraManager.toggleTorch()) },
                    enabled = cameraBound && cameraManager.hasFlash(),
                ) {
                    Icon(
                        imageVector = if (state.torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                        contentDescription = stringResource(R.string.flash_toggle),
                        tint = Color.White,
                    )
                }

                Button(
                    onClick = { viewModel.onManualCaptureRequested() },
                    enabled = cameraBound &&
                        state.phase in listOf(ScanPhase.DETECTING, ScanPhase.HOLD_STILL),
                    modifier = Modifier.size(76.dp),
                    shape = RoundedCornerShape(50),
                ) {
                    Icon(
                        Icons.Filled.CameraAlt,
                        contentDescription = stringResource(R.string.manual_capture),
                    )
                }

                IconButton(onClick = onOpenHistory) {
                    Icon(
                        Icons.Filled.List,
                        contentDescription = stringResource(R.string.scan_history),
                        tint = Color.White,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            FilledTonalButton(onClick = onOpenHistory) {
                Text(stringResource(R.string.scan_history))
            }
        }

        AnimatedVisibility(
            visible = state.phase == ScanPhase.READING || state.phase == ScanPhase.PROCESSING,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0x99000000)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Text(text = stringResource(R.string.reading_card), color = Color.White)
                }
            }
        }

        if (state.showUnsupportedDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissUnsupportedDialog() },
                title = { Text(stringResource(R.string.unsupported_card_title)) },
                text = { Text(stringResource(R.string.unsupported_card_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.dismissUnsupportedDialog()
                        onScanAgain()
                    }) { Text(stringResource(R.string.retry)) }
                },
            )
        }

        val error = state.lastError
        if (error != null && !state.showUnsupportedDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.consumeError() },
                text = { Text(stringResource(errorRes(error))) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.consumeError()
                        onScanAgain()
                    }) { Text(stringResource(R.string.retry)) }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.consumeError() }) {
                        Text(stringResource(R.string.close))
                    }
                },
            )
        }
    }

    // Auto-capture: brief deliberate debounce after the card held stable.
    LaunchedEffect(state.phase) {
        if (state.phase != ScanPhase.HOLD_STILL) return@LaunchedEffect
        delay(350)
        if (viewModel.uiState.phase == ScanPhase.HOLD_STILL) {
            captureAndProcess(viewModel, cameraManager, detector)
        }
    }

    // Manual capture: user pressed the shutter while a card was in frame.
    LaunchedEffect(state.phase) {
        if (state.phase != ScanPhase.READING) return@LaunchedEffect
        if (viewModel.pendingResult != null) return@LaunchedEffect
        captureAndProcess(viewModel, cameraManager, detector)
    }
}

/** One in-memory still capture → crop/enhance → OCR. Bitmap recycled inside. */
private fun captureAndProcess(
    viewModel: ScannerViewModel,
    cameraManager: CardCameraManager,
    detector: CardDetector,
) {
    cameraManager.captureStill(
        rotationDegrees = 0,
        onCaptured = { raw ->
            val detection = viewModel.uiState.detection
            val bitmap = if (detection != null) {
                ImageProcessor.cropAndEnhance(raw, detection)
            } else {
                raw
            }
            viewModel.processCapturedBitmap(bitmap)
        },
        onError = { viewModel.onCaptureFailed() },
    )
}

/** Analysis-path frame handling: detect, report, then close the frame. */
private fun analyzeProxy(
    proxy: androidx.camera.core.ImageProxy,
    detector: CardDetector,
    onResult: (com.example.cardscanner.camera.CardDetection?) -> Unit,
) {
    try {
        val plane = proxy.planes[0]
        val buffer = plane.buffer
        val luma = ByteArray(buffer.remaining())
        buffer.get(luma)
        onResult(detector.detect(luma, proxy.width, proxy.height))
    } finally {
        proxy.close()
    }
}

private fun errorRes(key: String): Int = when (key) {
    "error_camera_unavailable" -> R.string.error_camera_unavailable
    "error_permission_denied" -> R.string.error_permission_denied
    "error_card_not_detected" -> R.string.error_card_not_detected
    "error_ocr_failure" -> R.string.error_ocr_failure
    "error_invalid_card" -> R.string.error_invalid_card
    "error_luhn_failure" -> R.string.error_luhn_failure
    "error_unsupported_card" -> R.string.error_unsupported_card
    "error_missing_expiry" -> R.string.error_missing_expiry
    "error_missing_cardholder" -> R.string.error_missing_cardholder
    "error_invalid_expiry" -> R.string.error_invalid_expiry
    "error_duplicate_record" -> R.string.error_duplicate_record
    else -> R.string.error_generic
}

@Composable
private fun PermissionRationale(onGrant: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.camera_permission_rationale),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onGrant) {
                Text(stringResource(R.string.grant_camera_permission))
            }
        }
    }
}

@Composable
private fun StatusChip(phase: ScanPhase, quality: FrameQuality) {
    val text = when (phase) {
        ScanPhase.READY, ScanPhase.DETECTING, ScanPhase.ERROR ->
            if (quality == FrameQuality.POOR) {
                stringResource(R.string.move_closer)
            } else {
                stringResource(R.string.place_card_in_frame)
            }
        ScanPhase.HOLD_STILL -> stringResource(R.string.card_detected)
        ScanPhase.READING, ScanPhase.PROCESSING -> stringResource(R.string.reading_card)
        ScanPhase.CONFIRM -> stringResource(R.string.card_detected)
    }
    val qualityLabel = when (quality) {
        FrameQuality.POOR -> stringResource(R.string.confidence_poor)
        FrameQuality.FAIR -> stringResource(R.string.confidence_fair)
        FrameQuality.GOOD -> stringResource(R.string.confidence_good)
        FrameQuality.EXCELLENT -> stringResource(R.string.confidence_excellent)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = Color(0xCC000000),
            shape = RoundedCornerShape(50),
        ) {
            Text(
                text = text,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = qualityLabel,
            color = when (quality) {
                FrameQuality.POOR -> Color(0xFFFF8A80)
                FrameQuality.FAIR -> Color(0xFFFFD54F)
                FrameQuality.GOOD -> Color(0xFFA5D6A7)
                FrameQuality.EXCELLENT -> Color(0xFF69F0AE)
            },
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.semantics {
                contentDescription = "Recognition quality: $qualityLabel"
            },
        )
    }
}

/** The rounded, card-shaped scanning frame with ID-1 aspect ratio (≈1.586). */
@Composable
private fun CardFrameOverlay(state: ScannerUiState) {
    val detected = state.detection != null && state.phase != ScanPhase.DETECTING
    val borderProgress by animateFloatAsState(
        targetValue = if (detected) 1f else 0f,
        animationSpec = tween(200),
        label = "cardFrameBorder",
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .widthIn(max = 520.dp)
                .aspectRatio(1.586f)
                .clip(RoundedCornerShape(20.dp))
                .border(
                    width = 3.dp,
                    color = lerpColor(Color(0x66FFFFFF), Color(0xFF69F0AE), borderProgress),
                    shape = RoundedCornerShape(20.dp),
                )
                .semantics { contentDescription = "Card scanning frame" },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.place_card_in_frame).uppercase(),
                color = Color(0xCCFFFFFF),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.alpha(if (detected) 0.35f else 0.9f),
            )
        }
    }
}

private fun lerpColor(start: Color, end: Color, fraction: Float): Color = Color(
    red = start.red + (end.red - start.red) * fraction,
    green = start.green + (end.green - start.green) * fraction,
    blue = start.blue + (end.blue - start.blue) * fraction,
    alpha = start.alpha + (end.alpha - start.alpha) * fraction,
)
