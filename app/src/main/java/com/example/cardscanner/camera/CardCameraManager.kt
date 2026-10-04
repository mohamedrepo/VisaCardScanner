package com.example.cardscanner.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Owns the CameraX stack. Frames are analyzed in RAM only: the analysis pipeline
 * and the single still capture never touch MediaStore, app files, or caches.
 */
class CardCameraManager(
    private val context: Context,
    private val onFrame: (ImageProxy) -> Unit,
) {

    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var torchEnabled = false

    /** Binds preview + analysis + still-capture to the lifecycle. */
    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    fun start(
        lifecycleOwner: LifecycleOwner,
        preview: Preview,
        onBound: (Boolean) -> Unit,
    ) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(analysisExecutor) { proxy ->
                            onFrame(proxy)
                        }
                    }

                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                provider.unbindAll()
                camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                    capture,
                )
                imageCapture = capture
                onBound(true)
            } catch (t: Throwable) {
                onBound(false)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        try {
            ProcessCameraProvider.getInstance(context).get().unbindAll()
        } catch (_: Throwable) {
        }
        camera = null
        imageCapture = null
    }

    fun release() {
        stop()
        analysisExecutor.shutdown()
    }

    fun toggleTorch(): Boolean {
        val cam = camera ?: return torchEnabled
        torchEnabled = !torchEnabled
        cam.cameraControl.enableTorch(torchEnabled)
        return torchEnabled
    }

    fun isTorchOn(): Boolean = camera?.cameraInfo?.torchState?.value == TorchState.ON

    fun hasFlash(): Boolean = camera?.cameraInfo?.hasFlashUnit() == true

    /**
     * Captures one still frame fully in memory (no File output, no MediaStore),
     * converts it to a Bitmap, and hands it to [onCaptured]. On failure
     * [onError] receives a user-facing category.
     */
    @OptIn(ExperimentalGetImage::class)
    fun captureStill(
        rotationDegrees: Int,
        onCaptured: (Bitmap) -> Unit,
        onError: (String) -> Unit,
    ) {
        val capture = imageCapture
        if (capture == null) {
            onError("camera_unavailable")
            return
        }
        capture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val bitmap = image.toBitmapRotated(rotationDegrees)
                        onCaptured(bitmap)
                    } catch (_: Throwable) {
                        onError("capture_failed")
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    onError("capture_failed")
                }
            },
        )
    }

    private fun ImageProxy.toBitmapRotated(rotationDegrees: Int): Bitmap {
        val bitmap = toBitmap()
        if (rotationDegrees == 0) return bitmap
        val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Converts an ImageProxy to Bitmap via NV21 for YUV_420_888 planes
     * (toBitmap covers most cases on modern CameraX; retained as fallback).
     */
    @Suppress("unused")
    private fun ImageProxy.toBitmapNv21(): Bitmap {
        val nv21 = yuv420ToNv21()
        val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuv.compressToJpeg(Rect(0, 0, width, height), 95, out)
        val bytes = out.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun ImageProxy.yuv420ToNv21(): ByteArray {
        val ySize = width * height
        val nv21 = ByteArray(ySize + 2 * (ySize / 4))
        val yPlane = planes[0].buffer
        yPlane.get(nv21, 0, ySize)
        var offset = ySize
        for (p in 1..2) {
            val plane = planes[p]
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val cw = width / 2
            val ch = height / 2
            val buf = plane.buffer
            buf.rewind()
            for (row in 0 until ch) {
                for (col in 0 until cw) {
                    nv21[offset++] = buf.get(row * rowStride + col * pixelStride)
                }
            }
        }
        return nv21
    }
}
