package com.avalancheevantage.camera3Demo;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.avalancheevantage.android.camera3.AutoFitTextureView;
import com.avalancheevantage.android.camera3.Camera3;
import com.avalancheevantage.android.camera3.CaptureRequestConfiguration;
import com.avalancheevantage.android.camera3.OnImageAvailableListener;
import com.avalancheevantage.android.camera3.PreviewHandler;
import com.avalancheevantage.android.camera3.StillCaptureHandler;
import com.avalancheevantage.android.camera3.VideoCaptureHandler;
import com.avalancheevantage.android.camera3.VideoCaptureStartedCallback;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 999;

    /**
     * Initialize the camera3 camera manager
     */
    private final Camera3 cameraManager = new Camera3(this, Camera3.ERROR_HANDLER_DEFAULT);

    /**
     * Keep track of the last file we saved an image to so we can preview it.
     */
    @Nullable
    private File lastCapture;

    /**
     * Whether or not a camera preview should be shone
     */
    private boolean mShowPreview = true;

    /**
     * Keep track of if we are recording video
     */
    private boolean recordingVideo = false;

    /**
     * This just deals with getting the user's permission to access the camera.
     *
     * Once we have permission, all the actual camera3 stuff happens in
     * {@link MainActivity#onPermissionGranted()}
     *
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        setContentView(R.layout.activity_main);
        final Switch previewSwitch = findViewById(R.id.switch_show_preview);
        previewSwitch.setChecked(mShowPreview);
        previewSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mShowPreview = isChecked;
                cameraManager.pause();
                onPermissionGranted();
            }
        });
        if (cameraManager.captureConfigured()) {
            Log.d(TAG, "already started");
            return;
        }
        if (cameraManager.hasCameraPermission() &&
                cameraManager.hasMicrophonePermission() &&
                cameraManager.hasWritePermission()) {
            onPermissionGranted();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(
                    new String[]{Manifest.permission.CAMERA,
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_CAMERA_PERMISSION);
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
        // Restart the camera when the activity is re-opened
        if (cameraManager.captureConfigured()) {
            cameraManager.resume();
        }

    }

    /**
     * Override a built-in Android function so we can be notified when the user
     * grants us permission to use the camera.
     *
     * @see android.app.Activity#onRequestPermissionsResult(int, String[], int[])
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                    grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                onPermissionGranted();
            } else {
                Toast.makeText(this,
                        "This app needs access to the camera and microphone to work",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * This function sets up all the camera stuff.
     *
     * It is called right away if we already have permission to access the camera
     * or after permission is approved, otherwise.
     */
    private void onPermissionGranted() {
        Log.d(TAG, "onPermissionGranted");
        final String cameraId;
        try {
            // Get the front-facing camera
            cameraId = cameraManager.getAvailableCameras().get(0);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            return;
        }
        final AutoFitTextureView previewTexture = findViewById(R.id.preview);
        previewTexture.setFill(AutoFitTextureView.STYLE_FILL);

        // Handler to control everything about the preview
        final PreviewHandler previewHandler = new PreviewHandler(
                // The preview will automatically be rendered to this texture
                previewTexture,
                // No preferred size
                null,
                // No special configuration
                null,
                // Once a preview resolution has been selected, this callback will be called
                new Camera3.PreviewSizeCallback() {
                    @Override
                    public void previewSizeSelected(int orientation, Size size) {
                        // Once the preview size has been determined, scale the preview
                        // TextureView accordingly
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

        // Handler to control everything about still image captures
        final StillCaptureHandler captureSession =
                new StillCaptureHandler(ImageFormat.JPEG,
                        cameraManager.getLargestAvailableImageSize(cameraId, ImageFormat.JPEG),
                        new OnImageAvailableListener() {
                            private int imageCount = 0;

                            @Override
                            public ImageAction onImageAvailable(Image image) {
                                ++imageCount;
                                Log.d(TAG, "***********************************************");
                                Log.d(TAG, "IMAGE AVAILABLE: " + imageCount);
                                Log.d(TAG, "***********************************************");
                                Toast.makeText(MainActivity.this,
                                        "Image Captured! " + imageCount,
                                        Toast.LENGTH_SHORT).show();

                                // Delete the old file, if there is one
                                if (lastCapture != null) {
                                    lastCapture.delete();
                                }
                                lastCapture = createMediaFile("jpg");
                                lastCapture.deleteOnExit();
                                cameraManager.saveImageAsync(image, lastCapture);
                                return ImageAction.KEEP_IMAGE_OPEN;
                            }
                        });

        final VideoCaptureHandler videoSession = new VideoCaptureHandler(cameraManager.getDefaultVideoSize(cameraId));

        cameraManager.startCaptureSession(cameraId, mShowPreview ? previewHandler : null,
                Collections.singletonList(captureSession), Collections.singletonList(videoSession));
        findViewById(R.id.capture).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraManager.captureImage(captureSession,
                        new CaptureRequestConfiguration() {
                            @Override
                            public void configure(CaptureRequest.Builder request) {
                                request.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                                        CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
                            }
                        },
                        new CaptureRequestConfiguration() {
                            @Override
                            public void configure(CaptureRequest.Builder request) {
                                request.set(CaptureRequest.CONTROL_AE_MODE,
                                        CaptureRequest.CONTROL_AE_MODE_ON);
                            }
                        });
            }
        });

        final Button videoButton = findViewById(R.id.video);
        videoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recordingVideo = !recordingVideo;
                videoButton.setText(recordingVideo ? "Stop Recording" : "Start Recording");
                if (recordingVideo) {
                    File myOutputFile = createMediaFile("mp4");
                    cameraManager.startVideoCapture(videoSession, myOutputFile,
                            new VideoCaptureStartedCallback() {
                                @Override
                                public void captureStarted(@NonNull VideoCaptureHandler handler,
                                                           @NonNull File outputFile) {
                                    Toast.makeText(MainActivity.this,
                                            "Started recording video to " + outputFile,
                                            Toast.LENGTH_LONG).show();
                                    // Delete the old file
                                    if (lastCapture != null) {
                                        lastCapture.delete();
                                    }
                                    lastCapture = outputFile;
                                    lastCapture.deleteOnExit();
                                }
                            });
                } else {
                    cameraManager.stopVideoCapture(videoSession);
                }
            }
        });

        final Button viewButton = findViewById(R.id.btn_view_image);
        viewButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (lastCapture != null) {
                    Intent intent = new Intent();
                    intent.setAction(Intent.ACTION_VIEW);
                    Uri photoURI = FileProvider.getUriForFile(MainActivity.this,
                            BuildConfig.APPLICATION_ID + ".provider",
                            lastCapture);
                    Log.d(TAG, "photoUri = " + photoURI);
                    intent.setData(photoURI);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(intent);
                }
            }
        });
        ToggleButton toggleTorch = findViewById(R.id.btn_toggle_torch);
        toggleTorch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, final boolean isChecked) {
                    previewHandler.updateRequestConfig(new CaptureRequestConfiguration() {
                        @Override
                        public void configure(CaptureRequest.Builder request) {
                            if (isChecked) {
                                request.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                            } else {
                                request.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                            }
                        }
                    });
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        cameraManager.pause();

    }

    @Nullable
    private static File createMediaFile(String ext) {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM), "Camera");

        try {
            return File.createTempFile(imageFileName, "." + ext, storageDir);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
