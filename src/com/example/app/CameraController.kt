package com.example.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import java.nio.ByteBuffer

/**
 * Handles all Camera2 logic (Background Threads, Capture Session).
 * Defers isolated tasks like File Storage to ImageSaver.
 */
class CameraController(
    private val context: Context,
    private val surfaceView: SurfaceView,
    private val onPhotoCaptured: () -> Unit
) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    init {
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                openCamera()
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
            
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                closeCamera()
            }
        })
    }

    @SuppressLint("MissingPermission") // Handled by MainActivity
    private fun openCamera() {
        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return
            cameraManager.openCamera(cameraId, stateCallback, backgroundHandler)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            startPreview()
        }

        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
            cameraDevice = null
        }
    }

    private fun startPreview() {
        try {
            val surface = surfaceView.holder.surface
            if (!surface.isValid) return

            imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 1).apply {
                setOnImageAvailableListener(onImageAvailableListener, backgroundHandler)
            }

            val builder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            builder?.addTarget(surface)

            cameraDevice?.createCaptureSession(
                listOf(surface, imageReader?.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cameraDevice == null) return
                        captureSession = session
                        builder?.build()?.let {
                            try {
                                captureSession?.setRepeatingRequest(it, null, backgroundHandler)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Toast.makeText(context, "Failed to configure camera.", Toast.LENGTH_SHORT).show()
                    }
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun capturePhoto() {
        try {
            val device = cameraDevice ?: return
            val reader = imageReader ?: return
            val session = captureSession ?: return

            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            builder.addTarget(reader.surface)
            builder.set(CaptureRequest.JPEG_ORIENTATION, 90)

            session.capture(builder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result)
                    onPhotoCaptured()
                    startPreview() // Restart preview after capture
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val onImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        val image: Image? = reader.acquireLatestImage()
        image?.let {
            val buffer: ByteBuffer = it.planes[0].buffer
            val bytes = ByteArray(buffer.capacity())
            buffer.get(bytes)
            
            // Delegate saving to strictly modularized ImageSaver
            ImageSaver.save(context, bytes)
            
            it.close()
        }
    }

    fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").apply { start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun onResume() {
        startBackgroundThread()
        if (surfaceView.holder.surface.isValid) {
            openCamera()
        }
    }

    fun closeCamera() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
    }
}
