package com.avalancheevantage.camera3Demo;

import android.Manifest;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.media.Image;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

import com.avalancheevantage.camera3.AutoFitTextureView;
import com.avalancheevantage.camera3.Camera3;
import com.avalancheevantage.camera3.OnImageAvailableListener;
import com.avalancheevantage.camera3.PreviewHandler;
import com.avalancheevantage.camera3.StillCaptureHandler;

import java.util.Collections;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 999;

    private final Camera3 cameraManager = new Camera3(this, Camera3.ERROR_HANDLER_DEFAULT);
    private boolean mShowPreview = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final Switch previewSwitch = findViewById(R.id.switch_show_preview);
        previewSwitch.setChecked(mShowPreview);
        previewSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
//                mShowPreview = isChecked;
                cameraManager.pause();
                onPermissionGranted();
            }
        });
        if (cameraManager.captureConfigured()) {
            Log.d(TAG, "already started");
            return;
        }
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
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if (cameraManager.captureConfigured()) {
            cameraManager.resume();
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
        String cameraId;
        try {
            cameraId = cameraManager.getAvailableCameras().get(0);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            return;
        }
        final AutoFitTextureView previewTexture = findViewById(R.id.preview);
        previewTexture.setFill(AutoFitTextureView.STYLE_FILL);

        PreviewHandler previewHandler = new PreviewHandler(
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
        final StillCaptureHandler captureSession =
                new StillCaptureHandler(ImageFormat.JPEG,
                        cameraManager.getLargestAvailableSize(cameraId, ImageFormat.JPEG),
                        new OnImageAvailableListener() {
                            private int imageCount = 0;
                            @Override
                            public void onImageAvailable(Image image) {
                                ++imageCount;
                                Log.d(TAG, "***********************************************");
                                Log.d(TAG, "IMAGE AVAILABLE: "+imageCount);
                                Log.d(TAG, "***********************************************");
                                Toast.makeText(MainActivity.this,
                                        "Image Captured! "+imageCount, Toast.LENGTH_SHORT).show();
//                                try {
//                                    cameraManager.saveImage(image, File.createTempFile("test","jpeg"));
//                                } catch (IOException e) {
//                                    e.printStackTrace();
//                                }
                            }
                        });
        cameraManager.startCaptureSession(cameraId, mShowPreview ? previewHandler : null,
                Collections.singletonList(captureSession));
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
//        cameraManager.pause();
    }
}
