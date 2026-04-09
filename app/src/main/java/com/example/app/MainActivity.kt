package com.example.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

/**
 * Main Application Thread.
 * Acts merely as the Entry Point orchestrator managing lifecycles and permissions,
 * delegating work to cleanly isolated modules.
 */
class MainActivity : Activity() {

    private lateinit var mainUI: MainUI
    private lateinit var cameraController: CameraController
    private lateinit var gestureHelper: GestureRecognizerHelper
    private var lastFrameLogMs: Long = 0L
    private var lastRgbLogMs: Long = 0L
    private var lastGestureRunMs: Long = 0L
    private var lastUiUpdateMs: Long = 0L
    private var smoothedBrightness: Float = 0.5f

    companion object {
        private const val REQUEST_PERMISSIONS = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (hasPermissions()) {
            initializeApp()
        } else {
            requestCameraPermissions()
        }
    }

    private fun initializeApp() {
        // Delegate UI creation
        mainUI = MainUI(this) {
            cameraController.capturePhoto()
        }

        gestureHelper = GestureRecognizerHelper(this) { distance ->
            // Distance is normalized 0..1 in image coordinates.
            val brightness = applyBrightnessFromPinchDistance(distance)
            val now = SystemClock.elapsedRealtime()
            if (now - lastUiUpdateMs >= 120) {
                lastUiUpdateMs = now
                runOnUiThread {
                    mainUI.updateStatus(
                        "Pinch distance: ${"%.3f".format(distance)}  |  Brightness: ${(brightness * 100).roundToInt()}%"
                    )
                }
            }
            android.util.Log.d("GestureHelper", "Thumb-index distance=$distance brightness=$brightness")
        }
        gestureHelper.initialize()

        val frameAnalyser = FrameAnalyser { width, height, yPlane, nv21 ->
            val now = SystemClock.elapsedRealtime()

            if (yPlane.isEmpty()) return@FrameAnalyser
            if (now - lastFrameLogMs >= 500) {
                lastFrameLogMs = now
                var sum = 0L
                for (b in yPlane) sum += (b.toInt() and 0xFF)
                val avg = (sum.toDouble() / yPlane.size).roundToInt()
                android.util.Log.d(
                    "FrameAnalyser",
                    "Frame ${width}x${height} Ybytes=${yPlane.size} NV21=${nv21.size} avgLuma=${avg}"
                )
            }

            // Every ~2s, convert a single pixel via NV21->RGB as a sanity check.
            if (now - lastRgbLogMs >= 2000) {
                lastRgbLogMs = now
                try {
                    val center = nv21CenterPixelRgb(width, height, nv21)
                    android.util.Log.d(
                        "FrameAnalyser",
                        "RGB centerPixel r=${center[0]} g=${center[1]} b=${center[2]}"
                    )
                } catch (e: Exception) {
                    android.util.Log.e("FrameAnalyser", "Failed NV21->RGB sanity check", e)
                }
            }

            // Gesture model inference at ~10 FPS for now.
            if (now - lastGestureRunMs >= 100) {
                lastGestureRunMs = now
                gestureHelper.analyseFrame(width, height, nv21, now)
            }
        }

        // Delegate Camera functions
        cameraController = CameraController(
            context = this,
            surfaceView = mainUI.surfaceView,
            frameAnalyser = frameAnalyser,
            onPhotoCaptured = {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Photo Captured!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // --- Permission Handling Isolated ---
    
    private fun hasPermissions(): Boolean {
        val hasCamera = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val hasStorage = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Scoped storage applies in API 29+
        }
        return hasCamera && hasStorage
    }

    private fun requestCameraPermissions() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        requestPermissions(permissions.toTypedArray(), REQUEST_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initializeApp()
                cameraController.onResume() // Force immediate manual resume post permission grant
            } else {
                val errorView = TextView(this).apply {
                    text = "Camera & Storage permissions are required."
                    setBackgroundColor(Color.BLACK)
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                }
                setContentView(errorView)
            }
        }
    }

    // --- Orchestrate Lifecycles ---

    override fun onResume() {
        super.onResume()
        if (::cameraController.isInitialized) {
            cameraController.onResume()
        }
    }

    override fun onPause() {
        if (::cameraController.isInitialized) {
            cameraController.closeCamera()
            cameraController.stopBackgroundThread()
        }
        if (::gestureHelper.isInitialized) {
            gestureHelper.close()
        }
        super.onPause()
    }

    private fun applyBrightnessFromPinchDistance(distance: Float): Float {
        val minDistance = 0.04f
        val maxDistance = 0.30f
        val normalized = ((distance - minDistance) / (maxDistance - minDistance)).coerceIn(0f, 1f)
        val targetBrightness = 0.10f + (normalized * 0.90f)

        // Smooth transitions to avoid visible flicker.
        smoothedBrightness = (smoothedBrightness * 0.85f) + (targetBrightness * 0.15f)

        runOnUiThread {
            val lp: WindowManager.LayoutParams = window.attributes
            lp.screenBrightness = smoothedBrightness
            window.attributes = lp
        }

        return smoothedBrightness
    }

    private fun nv21CenterPixelRgb(width: Int, height: Int, nv21: ByteArray): IntArray {
        if (width <= 0 || height <= 0) throw IllegalArgumentException("Invalid dimensions")
        val x = width / 2
        val y = height / 2

        val frameSize = width * height
        val yIndex = y * width + x
        if (yIndex >= frameSize) throw IllegalArgumentException("Y index out of range")
        val yVal = nv21[yIndex].toInt() and 0xFF

        val uvRow = (y / 2)
        val uvCol = (x / 2)
        val uvIndex = frameSize + uvRow * width + uvCol * 2
        if (uvIndex + 1 >= nv21.size) throw IllegalArgumentException("UV index out of range")

        val v = (nv21[uvIndex].toInt() and 0xFF) - 128
        val u = (nv21[uvIndex + 1].toInt() and 0xFF) - 128

        // YUV -> RGB (BT.601), integer math.
        var r = (yVal + (1.402f * v)).roundToInt()
        var g = (yVal - (0.344136f * u) - (0.714136f * v)).roundToInt()
        var b = (yVal + (1.772f * u)).roundToInt()

        if (r < 0) r = 0 else if (r > 255) r = 255
        if (g < 0) g = 0 else if (g > 255) g = 255
        if (b < 0) b = 0 else if (b > 255) b = 255

        return intArrayOf(r, g, b)
    }
}
