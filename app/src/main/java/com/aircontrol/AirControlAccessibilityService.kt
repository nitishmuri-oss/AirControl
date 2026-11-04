package com.aircontrol

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.media.AudioManager
import android.view.accessibility.AccessibilityEvent

class AirControlAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AirControlAccessibilityService? = null

        fun performAction(action: String) {
            instance?.runAction(action)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Accessibility events will be handled here later.
    }

    override fun onInterrupt() {
        // Stop ongoing actions if interrupted.
    }

    fun runAction(action: String) {
        when (action) {
            "BACK" -> performGlobalAction(GLOBAL_ACTION_BACK)

            "VOLUME_UP" -> adjustVolume(AudioManager.ADJUST_RAISE)

            "VOLUME_DOWN" -> adjustVolume(AudioManager.ADJUST_LOWER)

            "SCROLL_UP" -> scrollScreen(true)

            "SCROLL_DOWN" -> scrollScreen(false)
        }
    }

    private fun adjustVolume(direction: Int) {
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        audio.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            direction,
            AudioManager.FLAG_SHOW_UI
        )
    }

    private fun scrollScreen(up: Boolean) {
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels / 2f
        val startY = if (up) metrics.heightPixels * 0.75f else metrics.heightPixels * 0.25f
        val endY = if (up) metrics.heightPixels * 0.25f else metrics.heightPixels * 0.75f

        val path = Path().apply {
            moveTo(x, startY)
            lineTo(x, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(path, 0, 400)
            )
            .build()

        dispatchGesture(gesture, null, null)
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }
}
