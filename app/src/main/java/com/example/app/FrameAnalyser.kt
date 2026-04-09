package com.example.app

import android.graphics.ImageFormat
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.view.Surface

class FrameAnalyser(
    private val onFrameAnalysed: (width: Int, height: Int, yPlane: ByteArray, nv21: ByteArray) -> Unit
) {
    private val imageReader: ImageReader =
        ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2)

    val surface: Surface
        get() = imageReader.surface

    private val listener = ImageReader.OnImageAvailableListener { reader ->
        val image = reader.acquireLatestImage() ?: return@OnImageAvailableListener
        try {
            val buffer = image.planes[0].buffer
            val yBytes = ByteArray(buffer.remaining())
            buffer.get(yBytes)

            val nv21 = yuv420888ToNv21(image)
            onFrameAnalysed(image.width, image.height, yBytes, nv21)
        } finally {
            image.close()
        }
    }

    fun setBackgroundHandler(backgroundHandler: Handler?) {
        imageReader.setOnImageAvailableListener(listener, backgroundHandler)
    }

    fun close() {
        imageReader.close()
    }

    private fun yuv420888ToNv21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val out = ByteArray(ySize + (ySize / 2))

        // Copy Y (luma)
        val yPlane = image.planes[0]
        val yBuffer = yPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        var outPos = 0
        for (row in 0 until height) {
            val rowStart = row * yRowStride
            if (yPixelStride == 1) {
                yBuffer.position(rowStart)
                yBuffer.get(out, outPos, width)
                outPos += width
            } else {
                for (col in 0 until width) {
                    out[outPos++] = yBuffer.get(rowStart + col * yPixelStride)
                }
            }
        }

        // Interleave VU for NV21 from U/V planes (which are subsampled 2x2).
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride

        val chromaWidth = width / 2
        val chromaHeight = height / 2

        outPos = ySize
        for (row in 0 until chromaHeight) {
            val uRowStart = row * uRowStride
            val vRowStart = row * vRowStride
            for (col in 0 until chromaWidth) {
                val uIndex = uRowStart + col * uPixelStride
                val vIndex = vRowStart + col * vPixelStride
                out[outPos++] = vBuffer.get(vIndex) // V
                out[outPos++] = uBuffer.get(uIndex) // U
            }
        }

        return out
    }
}
