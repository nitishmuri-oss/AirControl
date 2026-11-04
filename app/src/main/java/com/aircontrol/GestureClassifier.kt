package com.aircontrol

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.hypot

object GestureClassifier {

    private fun distance(
        a: NormalizedLandmark,
        b: NormalizedLandmark
    ): Float {
        return hypot(a.x() - b.x(), a.y() - b.y())
    }

    fun detect(landmarks: List<NormalizedLandmark>): String {

        if (landmarks.size < 21) return "NONE"

        val wrist = landmarks[0]

        val indexUp = distance(landmarks[8], wrist) >
                distance(landmarks[6], wrist) * 1.12f

        val middleUp = distance(landmarks[12], wrist) >
                distance(landmarks[10], wrist) * 1.12f

        val ringUp = distance(landmarks[16], wrist) >
                distance(landmarks[14], wrist) * 1.12f

        val pinkyUp = distance(landmarks[20], wrist) >
                distance(landmarks[18], wrist) * 1.12f

        val thumbExtended =
            distance(landmarks[4], landmarks[5]) >
            distance(landmarks[3], landmarks[5]) * 1.15f

        // Open palm = Pause
        if (indexUp && middleUp && ringUp && pinkyUp) {
            return "PAUSE"
        }

        // Thumbs up/down = Volume
        if (thumbExtended &&
            !indexUp && !middleUp && !ringUp && !pinkyUp
        ) {
            if (landmarks[4].y() < wrist.y() - 0.04f) {
                return "VOLUME_UP"
            }

            if (landmarks[4].y() > wrist.y() + 0.04f) {
                return "VOLUME_DOWN"
            }
        }

        // One finger = Scroll up
        if (indexUp && !middleUp && !ringUp && !pinkyUp) {
            return "SCROLL_UP"
        }

        // Two fingers = Scroll down
        if (indexUp && middleUp && !ringUp && !pinkyUp) {
            return "SCROLL_DOWN"
        }

        // Closed fist = Resume
        if (!indexUp && !middleUp && !ringUp && !pinkyUp) {
            return "FIST"
        }

        return "NONE"
    }
}
