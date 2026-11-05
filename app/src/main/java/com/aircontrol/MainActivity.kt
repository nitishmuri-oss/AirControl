package com.aircontrol

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var statusText: TextView
    private var waitingForOverlayPermission = false

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                checkOverlayPermissionAndStart()
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

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.rgb(20, 25, 35))
        }

        val title = TextView(this).apply {
            text = "AirControl"
            textSize = 32f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            text = "Gesture Control is stopped"
            textSize = 17f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(12, 32, 12, 32)
        }

        val startButton = Button(this).apply {
            text = "START GESTURE CONTROL"
            setOnClickListener {
                checkCameraPermissionAndStart()
            }
        }

        val stopButton = Button(this).apply {
            text = "STOP GESTURE CONTROL"
            setOnClickListener {
                stopGestureService()
            }
        }

        layout.addView(title)
        layout.addView(statusText)
        layout.addView(startButton)
        layout.addView(stopButton)

        setContentView(layout)
    }

    private fun checkCameraPermissionAndStart() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            checkOverlayPermissionAndStart()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun checkOverlayPermissionAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            waitingForOverlayPermission = true

            statusText.text = "Please allow Display over other apps"

            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )

            startActivity(intent)
            return
        }

        startGestureService()
    }

    override fun onResume() {
        super.onResume()

        if (
            waitingForOverlayPermission &&
            Settings.canDrawOverlays(this)
        ) {
            waitingForOverlayPermission = false
            startGestureService()
        }
    }

    private fun startGestureService() {
        try {
            val intent = Intent(this, AirControlCameraService::class.java)
            ContextCompat.startForegroundService(this, intent)
            statusText.text = "Starting gesture control..."
        } catch (e: Exception) {
            statusText.text = "Could not start: ${e.message}"
        }
    }

    private fun stopGestureService() {
        stopService(Intent(this, AirControlCameraService::class.java))
        AirControlCursorOverlay.hide()
        statusText.text = "Gesture Control is stopped"
    }
}
