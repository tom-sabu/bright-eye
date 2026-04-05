package com.example.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast

/**
 * Main Application Thread.
 * Acts merely as the Entry Point orchestrator managing lifecycles and permissions,
 * delegating work to cleanly isolated modules.
 */
class MainActivity : Activity() {

    private lateinit var mainUI: MainUI
    private lateinit var cameraController: CameraController

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

        // Delegate Camera functions
        cameraController = CameraController(
            context = this,
            surfaceView = mainUI.surfaceView,
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
        super.onPause()
    }
}
