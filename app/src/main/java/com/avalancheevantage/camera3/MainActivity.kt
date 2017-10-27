package com.avalancheevantage.camera3

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.TextureView
import org.jetbrains.anko.*
import android.support.v4.content.ContextCompat



class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"

    private val cameraManager = Camera3(this, null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

    }

    private val REQUEST_CAMERA_PERMISSION: Int = 999

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        val permissionCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                permissionCheck == PackageManager.PERMISSION_DENIED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
        } else {
            onPermissionGranted()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            onPermissionGranted()
        }
    }

    private fun onPermissionGranted() {
        Log.d(TAG, "onPermissionGranted")
        cameraManager.start()
        val cameraId = cameraManager.availableCameras[0]
        val previewTexture = find<AutoFitTextureView>(R.id.preview);
        cameraManager.startPreview(
                previewTexture,
                cameraId,
                null,
                Camera3.PreviewSizeCallback({orientation, size ->
                    Log.d(TAG, "size == $size")
                    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        previewTexture.setAspectRatio(
                                size.width, size.height)
                    } else {
                        previewTexture.setAspectRatio(
                                size.height, size.width)
                    }})
        )
    }

    override fun onPause() {
        super.onPause()
        cameraManager.pause()
    }
}
