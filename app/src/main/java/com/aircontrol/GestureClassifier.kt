package com.aircontrol

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

object GestureClassifier {

    fun detect(landmarks: List<NormalizedLandmark>): String {

        if (landmarks.size < 21) return "NONE"

        val indexUp = landmarks[8].y() < landmarks[6].y()
        val middleUp = landmarks[12].y() < landmarks[10].y()
        val ringUp = landmarks[16].y() < landmarks[14].y()
        val pinkyUp = landmarks[20].y() < landmarks[18].y()

        val thumbUp =
            landmarks[4].y() < landmarks[3].y() &&
            landmarks[3].y() < landmarks[0].y()

        val thumbDown =
            landmarks[4].y() > landmarks[3].y() &&
            landmarks[3].y() > landmarks[0].y()

        if (thumbUp && !indexUp && !middleUp) {
            return "VOLUME_UP"
        }

        if (thumbDown && !indexUp && !middleUp) {
            return "VOLUME_DOWN"
        }

        if (indexUp && !middleUp && !ringUp && !pinkyUp) {
            return "SCROLL_UP"
        }

        if (indexUp && middleUp && !ringUp && !pinkyUp) {
            return "SCROLL_DOWN"
        }

        if (!indexUp && !middleUp && !ringUp && !pinkyUp) {
            return "FIST"
        }

        if (indexUp && middleUp && ringUp && pinkyUp) {
            return "PAUSE"
        }

        return "NONE"
    }
}
