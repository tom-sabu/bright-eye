package com.example.app

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

class AspectRatioFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    // width / height
    private var targetAspectRatio: Float = 3f / 4f

    fun setAspectRatio(widthOverHeight: Float) {
        if (widthOverHeight > 0f) {
            targetAspectRatio = widthOverHeight
            requestLayout()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
        val availableHeight = MeasureSpec.getSize(heightMeasureSpec)

        if (availableWidth == 0 || availableHeight == 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        val currentRatio = availableWidth.toFloat() / availableHeight.toFloat()
        val measuredWidth: Int
        val measuredHeight: Int

        if (currentRatio > targetAspectRatio) {
            measuredHeight = availableHeight
            measuredWidth = (availableHeight * targetAspectRatio).toInt()
        } else {
            measuredWidth = availableWidth
            measuredHeight = (availableWidth / targetAspectRatio).toInt()
        }

        val exactWidth = MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY)
        val exactHeight = MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY)
        super.onMeasure(exactWidth, exactHeight)
    }
}

