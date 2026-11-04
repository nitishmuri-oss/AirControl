package com.aircontrol

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView

    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private var handLandmarker: HandLandmarker? = null
    private var lastTimestamp = 0L

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Camera permission required",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = FrameLayout(this)

        previewView = PreviewView(this)
        layout.addView(
            previewView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        statusText = TextView(this).apply {
            text = "Initializing hand detection..."
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(0x99000000.toInt())
            setPadding(24, 18, 24, 18)
            gravity = Gravity.CENTER
        }

        val textParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = 40
        }

        layout.addView(statusText, textParams)
        setContentView(layout)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun initializeHandLandmarker(): Boolean {
        return try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .build()

            val options =
                HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.VIDEO)
                    .setNumHands(2)
                    .setMinHandDetectionConfidence(0.5f)
                    .setMinHandPresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .build()

            handLandmarker =
                HandLandmarker.createFromOptions(this, options)

            true
        } catch (e: Exception) {
            updateStatus("Model error: ${e.message}")
            false
        }
    }

    private fun startCamera() {

        if (handLandmarker == null) {
            if (!initializeHandLandmarker()) return
        }

        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({

            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build()
            preview.surfaceProvider = previewView.surfaceProvider

            val analysis = ImageAnalysis.Builder()
                .setOutputImageFormat(
                    ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
                )
                .setBackpressureStrategy(
                    ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                )
                .build()

            analysis.setAnalyzer(cameraExecutor) { image ->
                analyzeImage(image)
            }

            try {
                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analysis
                )

                updateStatus("Show your hand to the camera")

            } catch (e: Exception) {
                updateStatus("Camera error: ${e.message}")
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzeImage(
        image: androidx.camera.core.ImageProxy
    ) {
        var bitmap: Bitmap? = null

        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val width = image.width
            val height = image.height

            val pixels = IntArray(width * height)

            for (y in 0 until height) {
                for (x in 0 until width) {
                    val offset =
                        y * plane.rowStride + x * plane.pixelStride

                    val r = buffer.get(offset).toInt() and 0xFF
                    val g = buffer.get(offset + 1).toInt() and 0xFF
                    val b = buffer.get(offset + 2).toInt() and 0xFF
                    val a = buffer.get(offset + 3).toInt() and 0xFF

                    pixels[y * width + x] =
                        (a shl 24) or
                        (r shl 16) or
                        (g shl 8) or b
                }
            }

            val rawBitmap =
                Bitmap.createBitmap(
                    width,
                    height,
                    Bitmap.Config.ARGB_8888
                )

            rawBitmap.setPixels(
                pixels, 0, width, 0, 0, width, height
            )

            val rotation = image.imageInfo.rotationDegrees

            bitmap = if (rotation != 0) {
                Bitmap.createBitmap(
                    rawBitmap,
                    0,
                    0,
                    width,
                    height,
                    Matrix().apply {
                        postRotate(rotation.toFloat())
                    },
                    true
                ).also {
                    rawBitmap.recycle()
                }
            } else {
                rawBitmap
            }

            val mpImage = BitmapImageBuilder(bitmap!!).build()

            var timestamp = SystemClock.elapsedRealtime()
            if (timestamp <= lastTimestamp) {
                timestamp = lastTimestamp + 1
            }
            lastTimestamp = timestamp

            val result =
                handLandmarker?.detectForVideo(mpImage, timestamp)

            val hands = result?.landmarks()?.size ?: 0

            updateStatus(
                if (hands > 0) {
                    "Hand Detected: $hands\n21 landmarks per hand"
                } else {
                    "No hand detected\nShow your hand"
                }
            )

        } catch (e: Exception) {
            updateStatus("Detection error: ${e.message}")
        } finally {
            bitmap?.recycle()
            image.close()
        }
    }

    private fun updateStatus(message: String) {
        runOnUiThread {
            statusText.text = message
        }
    }

    override fun onDestroy() {
        handLandmarker?.close()
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}
