package com.aircontrol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.util.concurrent.Executors

class AirControlCameraService : LifecycleService() {

    companion object {
        const val ACTION_STOP = "com.aircontrol.STOP_CAMERA"
        private const val CHANNEL_ID = "aircontrol_camera"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "AirControlService"
    }

    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private var handLandmarker: HandLandmarker? = null
    private var lastTimestamp = 0L

    private var lastGesture = "NONE"
    private var lastGestureSince = 0L
    private var lastActionTime = 0L
    private var lastTapTime = 0L

    private var controlPaused = false
    private var lastPinch = false
    private var cursorVisible = false

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()

        val notification = buildNotification("Starting camera...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        initializeHandLandmarker()
        startCamera()

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AirControl Camera",
                NotificationManager.IMPORTANCE_LOW
            )

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String): Notification {
        val stopIntent = Intent(this, AirControlCameraService::class.java)
            .setAction(ACTION_STOP)

        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(this, MainActivity::class.java)

        val openPendingIntent = PendingIntent.getActivity(
            this,
            2,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("AirControl is running")
            .setContentText(message)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop",
                stopPendingIntent
            )
            .build()
    }

    private fun updateNotification(message: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(message))
    }

    private fun initializeHandLandmarker() {
        try {
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath("hand_landmarker.task")
                        .build()
                )
                .setRunningMode(RunningMode.VIDEO)
                .setNumHands(2)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .build()

            handLandmarker = HandLandmarker.createFromOptions(
                this,
                options
            )

        } catch (e: Exception) {
            Log.e(TAG, "Hand detector initialization failed", e)
            updateNotification("Hand detector error")
        }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)

        providerFuture.addListener({
            try {
                val provider = providerFuture.get()

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

                provider.unbindAll()

                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    analysis
                )

                updateNotification("Gesture control active")

            } catch (e: Exception) {
                Log.e(TAG, "Camera start failed", e)
                updateNotification("Camera error")
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
                    val offset = y * plane.rowStride +
                            x * plane.pixelStride

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

            val rawBitmap = Bitmap.createBitmap(
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

            val result = handLandmarker?.detectForVideo(
                mpImage,
                timestamp
            )

            val landmarks = result?.landmarks()?.firstOrNull()

            val gesture = if (landmarks == null) {
                "NONE"
            } else {
                GestureClassifier.detect(landmarks)
            }

            if (landmarks != null) {
                val indexTip = landmarks[8]

                val normalizedX =
                    (1f - indexTip.x()).coerceIn(0f, 1f)

                val normalizedY =
                    indexTip.y().coerceIn(0f, 1f)

                if (!cursorVisible) {
                    AirControlCursorOverlay.show(this)
                    cursorVisible = true
                }

                AirControlCursorOverlay.updatePosition(
                    this,
                    normalizedX,
                    normalizedY
                )

                val now = SystemClock.elapsedRealtime()

                // Pinch edge = one click
                if (
                    gesture == "PINCH" &&
                    !lastPinch &&
                    !controlPaused &&
                    now - lastTapTime > 450
                ) {
                    val metrics = resources.displayMetrics

                    AirControlAccessibilityService.performTap(
                        normalizedX * metrics.widthPixels,
                        normalizedY * metrics.heightPixels
                    )

                    lastTapTime = now
                }

                lastPinch = gesture == "PINCH"

            } else {
                lastPinch = false

                if (cursorVisible) {
                    AirControlCursorOverlay.hide()
                    cursorVisible = false
                }
            }

            processGesture(gesture)

        } catch (e: Exception) {
            Log.e(TAG, "Frame analysis failed", e)

        } finally {
            bitmap?.recycle()
            image.close()
        }
    }

    private fun processGesture(gesture: String) {
        val now = SystemClock.elapsedRealtime()

        if (gesture != lastGesture) {
            lastGesture = gesture
            lastGestureSince = now
            return
        }

        if (gesture == "NONE" || gesture == "PINCH") {
            return
        }

        if (now - lastGestureSince < 350) {
            return
        }

        if (gesture == "PAUSE") {
            if (!controlPaused) {
                controlPaused = true
                lastActionTime = now
                updateNotification("Gesture control paused")
            }
            return
        }

        if (gesture == "FIST") {
            if (controlPaused) {
                controlPaused = false
                lastActionTime = now
                updateNotification("Gesture control resumed")
            }
            return
        }

        if (controlPaused) return

        when (gesture) {
            "SCROLL_UP", "SCROLL_DOWN" -> {
                if (now - lastActionTime >= 350) {
                    AirControlAccessibilityService.performAction(gesture)
                    lastActionTime = now
                }
            }

            "VOLUME_UP", "VOLUME_DOWN" -> {
                if (now - lastActionTime >= 650) {
                    AirControlAccessibilityService.performAction(gesture)
                    lastActionTime = now
                }
            }
        }
    }

    override fun onDestroy() {
        AirControlCursorOverlay.hide()
        handLandmarker?.close()
        cameraExecutor.shutdown()

        try {
            ProcessCameraProvider.getInstance(this).get().unbindAll()
        } catch (e: Exception) {
            Log.e(TAG, "Camera cleanup failed", e)
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
