package com.example.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class HandOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN
        style = Paint.Style.FILL
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private var normalizedLandmarks: List<Pair<Float, Float>> = emptyList()

    private val connections = arrayOf(
        0 to 1, 1 to 2, 2 to 3, 3 to 4,
        0 to 5, 5 to 6, 6 to 7, 7 to 8,
        0 to 9, 9 to 10, 10 to 11, 11 to 12,
        0 to 13, 13 to 14, 14 to 15, 15 to 16,
        0 to 17, 17 to 18, 18 to 19, 19 to 20,
        5 to 9, 9 to 13, 13 to 17
    )

    fun updateLandmarks(points: List<Pair<Float, Float>>) {
        normalizedLandmarks = points
        postInvalidate()
    }

    fun clear() {
        normalizedLandmarks = emptyList()
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (normalizedLandmarks.isEmpty()) return

        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)

        for ((a, b) in connections) {
            if (a >= normalizedLandmarks.size || b >= normalizedLandmarks.size) continue
            val p1 = normalizedLandmarks[a]
            val p2 = normalizedLandmarks[b]
            canvas.drawLine(p1.first * w, p1.second * h, p2.first * w, p2.second * h, linePaint)
        }

        for ((x, y) in normalizedLandmarks) {
            canvas.drawCircle(x * w, y * h, 8f, pointPaint)
        }
    }
}

