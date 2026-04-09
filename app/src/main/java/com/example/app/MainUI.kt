package com.example.app

import android.app.Activity
import android.graphics.Color
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Handles the programmatic creation of our User Interface.
 * Acts as the "View" portion of the application.
 */
class MainUI(private val activity: Activity, private val onCaptureClicked: () -> Unit) {
    
    val surfaceView: SurfaceView
    private val handOverlayView: HandOverlayView
    private val statusText: TextView

    init {
        val rootLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val previewContainer = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
        }

        surfaceView = SurfaceView(activity)
        handOverlayView = HandOverlayView(activity).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        previewContainer.addView(
            surfaceView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        previewContainer.addView(
            handOverlayView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val captureButton = Button(activity).apply {
            text = "Capture"
            textSize = 18f
            setPadding(0, 40, 0, 40)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { onCaptureClicked() }
        }

        statusText = TextView(activity).apply {
            text = "Gesture: waiting..."
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#66000000"))
            setPadding(24, 16, 24, 16)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        rootLayout.addView(previewContainer)
        rootLayout.addView(statusText)
        rootLayout.addView(captureButton)

        activity.setContentView(rootLayout)
    }

    fun updateStatus(text: String) {
        statusText.text = text
    }

    fun updateLandmarks(points: List<Pair<Float, Float>>) {
        handOverlayView.updateLandmarks(points)
    }

    fun clearLandmarks() {
        handOverlayView.clear()
    }
}
