package com.example.app

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

class GestureRecognizerHelper(
    private val context: Context,
    private val onGestureResult: (distance: Float?, landmarks: List<Pair<Float, Float>>) -> Unit
) {
    private var recognizer: GestureRecognizer? = null
    private var recognizerReady = false

    fun initialize() {
        if (recognizerReady) return
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("gesture_recognizer.task")
                .build()

            val options = GestureRecognizer.GestureRecognizerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result: GestureRecognizerResult, _: com.google.mediapipe.framework.image.MPImage ->
                    handleResult(result)
                }
                .setErrorListener { error ->
                    Log.e("GestureHelper", "Gesture recognizer error", error)
                }
                .build()

            recognizer = GestureRecognizer.createFromOptions(context, options)
            recognizerReady = true
            Log.d("GestureHelper", "Gesture recognizer initialized")
        } catch (e: Exception) {
            recognizerReady = false
            Log.e(
                "GestureHelper",
                "Could not initialize GestureRecognizer. Place gesture_recognizer.task in app/src/main/assets/",
                e
            )
        }
    }

    fun analyseFrame(width: Int, height: Int, nv21: ByteArray, timestampMs: Long = SystemClock.uptimeMillis()) {
        val activeRecognizer = recognizer ?: return
        try {
            // Temporary bridge: NV21 -> JPEG -> Bitmap -> MPImage.
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val output = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 80, output)
            val jpegBytes = output.toByteArray()
            val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size) ?: return
            val mpImage = BitmapImageBuilder(bitmap).build()

            activeRecognizer.recognizeAsync(mpImage, timestampMs)
        } catch (e: Exception) {
            Log.e("GestureHelper", "Failed to analyse frame", e)
        }
    }

    fun close() {
        recognizer?.close()
        recognizer = null
        recognizerReady = false
    }

    private fun handleResult(result: GestureRecognizerResult) {
        try {
            val landmarks = result.landmarks()
            if (landmarks.isEmpty()) {
                onGestureResult(null, emptyList())
                return
            }

            val firstHand = landmarks[0]
            val points = firstHand.map { it.x() to it.y() }
            if (firstHand.size <= 8) {
                onGestureResult(null, points)
                return
            }

            val thumbTip = firstHand[4]
            val indexTip = firstHand[8]

            val dx = thumbTip.x() - indexTip.x()
            val dy = thumbTip.y() - indexTip.y()
            val distance = sqrt(dx * dx + dy * dy)
            onGestureResult(distance, points)
        } catch (e: Exception) {
            Log.e("GestureHelper", "Failed to parse gesture result", e)
        }
    }
}

