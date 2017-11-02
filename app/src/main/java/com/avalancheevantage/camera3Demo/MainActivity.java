package com.avalancheevantage.camera3Demo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.media.ImageReader;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.support.v4.content.ContextCompat;
import android.util.Size;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.widget.Toast;
import com.avalancheevantage.camera3.Camera3;

import java.util.Collections;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 999;

    private final Camera3 cameraManager = new Camera3(this, Camera3.ERROR_HANDLER_DEFAULT);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if (cameraManager.hasCameraPermission()) {
            onPermissionGranted();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            Toast.makeText(this,
                    "Oops! This app does not have permission to access your camera",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            onPermissionGranted();
        }
    }

    private void onPermissionGranted() {
        Log.d(TAG, "onPermissionGranted");
        cameraManager.start();
        String cameraId = null;
        try {
            cameraId = cameraManager.getAvailableCameras().get(0);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            return;
        }
        final AutoFitTextureView previewTexture = findViewById(R.id.preview);
//        final TextureView previewTexture = findViewById(R.id.preview);

        Camera3.PreviewSession previewSession = cameraManager.createPreviewSession(
                previewTexture,
                null,
                new Camera3.PreviewSizeCallback() {
                    @Override
                    public void previewSizeSelected(int orientation, Size size) {
                        Log.d(TAG, "preview size == " + size);
                        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                            previewTexture.setAspectRatio(
                                    size.getWidth(), size.getHeight());
                        } else {
                            previewTexture.setAspectRatio(
                                    size.getHeight(), size.getWidth());
                        }
                    }
                }
        );
        final Camera3.StillImageCaptureSession captureSession =
                cameraManager.createStillImageCaptureSession(ImageFormat.JPEG,
                        cameraManager.getLargestAvailableSize(cameraId, ImageFormat.JPEG),
                        new ImageReader.OnImageAvailableListener() {
                            @Override
                            public void onImageAvailable(ImageReader reader) {
                                Log.d(TAG, "***********************************************");
                                Log.d(TAG, "IMAGE AVAILABLE");
                                Log.d(TAG, "***********************************************");
                                Toast.makeText(MainActivity.this,
                                        "Image Captured!", Toast.LENGTH_SHORT).show();
                            }
                        });
        cameraManager.startCaptureSession(cameraId, previewSession , Collections.singletonList(captureSession));
        findViewById(R.id.capture).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                cameraManager.captureImage(captureSession,
                        Camera3.PRECAPTURE_CONFIG_TRIGGER_AUTO_FOCUS,
                        Camera3.CAPTURE_CONFIG_DEFAULT);
                v.performClick();
                return false;
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        cameraManager.stop();
    }
}
