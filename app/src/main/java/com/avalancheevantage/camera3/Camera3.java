package com.avalancheevantage.camera3;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;

import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Created by Quinn Freedman on 10/19/2017.
 */

public class Camera3 {
    private static final String TAG = "Camera3";

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    /**
     * Camera state: Showing camera preview.
     */
    private static final int STATE_PREVIEW = 0;

    /**
     * Camera state: Waiting for the focus to be locked.
     */
    private static final int STATE_WAITING_LOCK = 1;

    /**
     * Camera state: Waiting for the exposure to be precapture state.
     */
    private static final int STATE_WAITING_PRECAPTURE = 2;

    /**
     * Camera state: Waiting for the exposure state to be something other than precapture.
     */
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;

    /**
     * Camera state: Picture was taken.
     */
    private static final int STATE_PICTURE_TAKEN = 4;

    /**
     * The current state of camera state for taking pictures.
     *
     * @see #mCaptureCallback
     */
    private int mState = STATE_PREVIEW;
    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private static final String NULL_MANAGER_MESSAGE = "No camera manager. " +
            "`getSystemService(Context.CAMERA_SERVICE)` returned `null`";

    /**
     * A {@link CameraCaptureSession} for camera preview.
     */
    private CameraCaptureSession mCaptureSession;

    /**
     * A reference to the opened {@link CameraDevice}.
     */
    private CameraDevice mCameraDevice;

    private Queue<ImageCaptureRequest> mCaptureRequestQueue = new ArrayDeque<>();

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    private Activity mActivity;
    private Integer mSensorOrientation;
    private CaptureRequest.Builder mPreviewRequestBuilder;

    private String mCameraId;
    @Nullable
    private PreviewSession mPreviewSession;
    @NonNull
    private List<StillImageCaptureSession> mStillCaptureSessions = new ArrayList<>();
    @NonNull
    private ErrorHandler mErrorHandler;
    private CaptureRequest mPreviewRequest;
    private boolean started = false;

    public static final ImageCaptureRequestConfiguration PRECAPTURE_CONFIG_TRIGGER_AUTO_FOCUS =
            new ImageCaptureRequestConfiguration() {
                @Override
                public void configure(CaptureRequest.Builder request) {
                    // This is how to tell the camera to trigger auto exposure.
                    request.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                            CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
                }
            };
    public static final ImageCaptureRequestConfiguration PRECAPTURE_CONFIG_NONE = null;
    public static final ImageCaptureRequestConfiguration CAPTURE_CONFIG_DEFAULT =
            new ImageCaptureRequestConfiguration() {
                @Override
                public void configure(CaptureRequest.Builder request) {

                }
            };

    public Camera3(Activity activity, @Nullable ErrorHandler errorHandler) {
        this.mActivity = activity;
        if (errorHandler != null) {
            mErrorHandler = errorHandler;
        } else {
            mErrorHandler = new ErrorHandler() {
                @Override
                public void error(String message, Exception e) {
                    Log.e(TAG, message, e);
                }

                @Override
                public void warning(String message) {
                    Log.w(TAG, message);
                }

                @Override
                public void info(String message) {
                    Log.i(TAG, message);
                }
            };
        }
    }

    public void startCaptureSession(@NonNull String cameraId,
                                    @Nullable PreviewSession previewSession,
                                    @Nullable List<StillImageCaptureSession> stillCaptureSessions) {
        try {
            mErrorHandler.info("starting preview");

            mPreviewSession = previewSession;
            mCameraId = cameraId;

            //TODO: still start session if preview is null
            if (previewSession != null) {
                TextureView previewTextureView = previewSession.getTextureView();

                if (previewTextureView.isAvailable()) {
                    openCamera(cameraId,
                            new Size(previewTextureView.getWidth(),
                                    previewTextureView.getHeight()));
                } else {
                    previewTextureView.setSurfaceTextureListener(new PreviewTextureListener(cameraId));
                }
            }

            if (stillCaptureSessions != null) {
                mStillCaptureSessions = stillCaptureSessions;
            } else {
                mStillCaptureSessions = new ArrayList<>();
            }
        } catch (Exception e) {
            mErrorHandler.error("Something went wrong", e);
        }
    }

    public void start() {
        try {
            mErrorHandler.info("start");
            if (this.started) {
                mErrorHandler.warning("Calling `start()` when Camera3 is already started.");
                return;
            }
            this.started = true;
            startBackgroundThread();
        } catch (Exception e) {
            mErrorHandler.error("Something went wrong", e);
        }
    }

    public void stop() {
        try {
            mErrorHandler.info("stop");
            if (!this.started) {
                mErrorHandler.warning("Calling `stop()` when Camera3 is already stopped.");
                return;
            }
            closeCamera();
            stopBackgroundThread();
            this.started = false;
        } catch (Exception e) {
            mErrorHandler.error("Something went wrong", e);
        }
    }

    public void captureImage(StillImageCaptureSession session,
                             ImageCaptureRequestConfiguration precapture,
                             ImageCaptureRequestConfiguration capture) {
        if (mStillCaptureSessions.isEmpty()) {
            mErrorHandler.warning("No still capture targets configured");
            return;
        }

        if (mPreviewSession == null) {
            //TODO what to do here?
        }

        mCaptureRequestQueue.add(new ImageCaptureRequest(session, precapture, capture, mErrorHandler));

        lockFocus();
    }

    private void openCamera(String cameraId, Size previewTextureSize) /*throws CameraAccessException*/ {
        mErrorHandler.info("opening camera");
        mErrorHandler.info("Preview texture size == " + previewTextureSize);
        if (ContextCompat.checkSelfPermission(mActivity, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            mErrorHandler.error(
                    "Camera Permission must be obtained by activity before starting Camera3",
                    null);
            return;
        }
        setUpCameraOutputs(cameraId, previewTextureSize);
        configureTransform(mPreviewSession, previewTextureSize);
        CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                mErrorHandler.error("Time out waiting to acquire camera lock.", null);
                return;
            }
            if (manager == null) {
                mErrorHandler.error(NULL_MANAGER_MESSAGE, null);
                return;
            }
            manager.openCamera(cameraId, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            reportCameraAccessException(e);
        } catch (InterruptedException e) {
            mErrorHandler.error("Interrupted while trying to lock camera opening.", e);
        }
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (mBackgroundThread != null) {
            mBackgroundThread.quitSafely();
            try {
                mBackgroundThread.join();
                mBackgroundThread = null;
            } catch (InterruptedException e) {
                mErrorHandler.error("Error stopping background thread", e);
            }
        }
        mBackgroundHandler = null;
    }

    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (mCaptureSession != null) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (mCameraDevice != null) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            for (StillImageCaptureSession imageCaptureSession : mStillCaptureSessions) {
                imageCaptureSession.closeImageReader();

            }
        } catch (InterruptedException e) {
            mErrorHandler.error("Interrupted while trying to close camera.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    @SuppressWarnings("SuspiciousNameCombination")
    private void setUpCameraOutputs(String cameraId, Size previewTextureSize) {
        CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);

        if (manager == null) {
            mErrorHandler.error(NULL_MANAGER_MESSAGE, null);
            return;
        }
        CameraCharacteristics characteristics;
        try {
            characteristics = manager.getCameraCharacteristics(cameraId);
        } catch (CameraAccessException e) {
            mErrorHandler.error("Camera Access Exception", e);
            return;
        }
        StreamConfigurationMap map = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
            mErrorHandler.error(
                    "CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP was null " +
                            "for the given cameraId",
                    null);
            return;
        }
        Size largestJpeg = Collections.max(
                Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                new CompareSizesByArea());

        // Find out if we need to swap dimension to get the preview size relative to sensor
        // coordinate.
        int displayRotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        //noinspection ConstantConditions
        mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        if (mSensorOrientation == null) {
            mErrorHandler.error(
                    "Invalid Camera Configuration: " +
                            "no field `SENSOR_ORIENTATION` for the specified cameraId", null);
            return;
        }
        boolean swappedDimensions = false;
        switch (displayRotation) {
            case Surface.ROTATION_0:
            case Surface.ROTATION_180:
                if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                    swappedDimensions = true;
                }
                break;
            case Surface.ROTATION_90:
            case Surface.ROTATION_270:
                if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                    swappedDimensions = true;
                }
                break;
            default:
                Log.e(TAG, "Display rotation is invalid: " + displayRotation);
        }

        Point displaySize = new Point();
        mActivity.getWindowManager().getDefaultDisplay().getSize(displaySize);
        int rotatedPreviewWidth = previewTextureSize.getWidth();
        int rotatedPreviewHeight = previewTextureSize.getHeight();
        int maxPreviewWidth = displaySize.x;
        int maxPreviewHeight = displaySize.y;

        if (swappedDimensions) {
            rotatedPreviewWidth = previewTextureSize.getHeight();
            rotatedPreviewHeight = previewTextureSize.getWidth();
            maxPreviewWidth = displaySize.y;
            maxPreviewHeight = displaySize.x;
        }

        if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
            maxPreviewWidth = MAX_PREVIEW_WIDTH;
        }

        if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
            maxPreviewHeight = MAX_PREVIEW_HEIGHT;
        }

        if (mPreviewSession != null) {
            // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
            // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
            // garbage capture data.
            //TODO allow the user to specify a preferred preview aspect ratio and size
            Size optimalSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                    rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                    maxPreviewHeight, /*aspect ratio*/ largestJpeg);
            mPreviewSession.setPreviewSize(optimalSize);

            //notify the user what preview size we chose
            PreviewSizeCallback sizeCallback = mPreviewSession.getPreviewSizeSelectedCallback();
            if (sizeCallback != null) {
                int orientation = mActivity.getResources().getConfiguration().orientation;
                sizeCallback.previewSizeSelected(orientation, optimalSize);
            }
        }

        /*// Check if the flash is supported.
        Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        mFlashSupported = available == null ? false : available;*/

        /*
        //a npe in here can signal bad support
        } catch (NullPointerException e) {
            throw new RuntimeException("Camera2 API is not supported on this device");
        }*/
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation for the
     * TextureView for a PreviewSession
     *
     * @param previewSession     The PreviewSession to configure
     * @param previewTextureSize the size of the texture to transform the preview to fit.
     */
    @SuppressWarnings("SuspiciousNameCombination")
    private void configureTransform(PreviewSession previewSession, Size previewTextureSize) {
        if (previewSession.getTextureView() == null) {
            mErrorHandler.error("textureView is null", null);
            return;
        } else if (mActivity == null) {
            mErrorHandler.error("activity is null", null);
            return;
        }
        int viewWidth = previewTextureSize.getWidth();
        int viewHeight = previewTextureSize.getHeight();

        int previewWidth = previewSession.getPreviewSize().getWidth();
        int previewHeight = previewSession.getPreviewSize().getHeight();

        int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, previewHeight, previewWidth);
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / previewHeight,
                    (float) viewWidth / previewWidth);
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        previewSession.getTextureView().setTransform(matrix);
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
     * is at least as large as the respective texture view size, and that is at most as large as the
     * respective max size, and whose aspect ratio matches with the specified value. If such size
     * doesn't exist, choose the largest one that is at most as large as the respective max size,
     * and whose aspect ratio matches with the specified value.
     *
     * @param choices           The list of sizes that the camera supports for the intended output
     *                          class
     * @param textureViewWidth  The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth          The maximum width that can be chosen
     * @param maxHeight         The maximum height that can be chosen
     * @param aspectRatio       The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    private void createPreviewCameraCaptureSession(@NonNull final PreviewSession previewSession) {
        if (previewSession == null) {
            mErrorHandler.error("Internal error: previewSession is null", null);
            return;
        }
        Size previewSize = previewSession.getPreviewSize();
        if (previewSize == null) {
            mErrorHandler.error(
                    "Internal error: previewSession.previewSize is null", null);
            return;
        }

        SurfaceTexture texture = previewSession.getTextureView().getSurfaceTexture();
        if (texture == null) {
            mErrorHandler.error(
                    "previewSession.getTextureView().getSurfaceTexture() is null",
                    null);
            return;
        }

        // We configure the size of default buffer to be the size of camera preview we want.
        texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

        // This is the output Surface we need to start preview.
        Surface surface = new Surface(texture);

        try {
            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder = previewSession.getPreviewRequest() == null ?
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW) :
                    previewSession.getPreviewRequest();
            mPreviewRequestBuilder.addTarget(surface);


            // Create a CameraCaptureSession for camera preview.
            List<Surface> targetSurfaces = new ArrayList<>(Arrays.asList(surface));
            for (StillImageCaptureSession captureSession : mStillCaptureSessions) {
                if (captureSession == null) {
                    mErrorHandler.error("a StillImageCaptureSession is null", null);
                    continue;
                }
                if (captureSession.getImageReader() == null) {
                    mErrorHandler.error("a StillImageCaptureSession has a null ImageReader", null);
                    continue;
                }
                targetSurfaces.add(captureSession.getImageReader().getSurface());
            }
            mCameraDevice.createCaptureSession(targetSurfaces,
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == mCameraDevice) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                            try {
                                if (previewSession.getPreviewRequest() == null) {
                                    // Auto focus should be continuous for camera preview.
                                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                }

                                // Finally, we start displaying the camera preview.
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                        mCaptureCallback, mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                reportCameraAccessException(e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            mErrorHandler.error(
                                    "Failed to configure CameraCaptureSession", null);
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            reportCameraAccessException(e);
        }
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private void lockFocus() {
        try {
            // This is how to tell the camera to lock focus.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the lock.
            mState = STATE_WAITING_LOCK;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            reportCameraAccessException(e);
        }
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private void unlockFocus() {
        try {
            // Reset the auto-focus trigger
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
            // After this, the camera will go back to the normal state of preview.
            mState = STATE_PREVIEW;
            mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            reportCameraAccessException(e);
        }
    }

    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    /**
     * Retrieves the JPEG orientation from the specified screen rotation.
     *
     * @param rotation The screen rotation.
     * @return The JPEG orientation (one of 0, 90, 270, and 360)
     */
    private int getOrientation(int rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    /*
       Callbacks
     */

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private class PreviewTextureListener implements TextureView.SurfaceTextureListener {
        private String cameraId;

        public PreviewTextureListener(String cameraId) {
            this.cameraId = cameraId;
        }

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            openCamera(cameraId, new Size(width, height));
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            configureTransform(mPreviewSession, new Size(width, height));
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }

    }

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            if (mPreviewSession == null) {
                mErrorHandler.warning("Internal error: mPreviewSession is null when camera is opened");
                return;
            }
            createPreviewCameraCaptureSession(mPreviewSession);
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

    };


    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            try {
                switch (mState) {
                    case STATE_PREVIEW: {
                        //do nothing
                        break;
                    }
                    case STATE_WAITING_LOCK: {
                        Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                        if (afState == null) {
                            captureStillPicture();
                        } else if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                                afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                            // CONTROL_AE_STATE can be null on some devices
                            Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                            if (aeState == null ||
                                    aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                                captureStillPicture();
                            } else {
                                ImageCaptureRequest request = mCaptureRequestQueue.peek();
                                if (request == null) {
                                    mErrorHandler.error(
                                            "Internal error: Request Queue was empty when trying to run precapture",
                                            null);
                                    return;
                                }
                                if (request.hasPrecapture()) {

                                    mErrorHandler.info("running precapture sequence");
                                    // Run precapture:
                                    request.configurePrecapture(mPreviewRequestBuilder);

                                    // Tell #mCaptureCallback to wait for the precapture sequence to be set.
                                    mState = STATE_WAITING_PRECAPTURE;
                                    try {
                                        mCaptureSession.capture(
                                                mPreviewRequestBuilder.build(),
                                                mCaptureCallback,
                                                mBackgroundHandler);
                                    } catch (CameraAccessException e) {
                                        reportCameraAccessException(e);
                                    }
                                } else {
                                    captureStillPicture();
                                }
                            }
                        }
                        break;
                    }
                    case STATE_WAITING_PRECAPTURE: {
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                                aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                            mState = STATE_WAITING_NON_PRECAPTURE;
                        }
                        break;
                    }
                    case STATE_WAITING_NON_PRECAPTURE: {
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                            captureStillPicture();
                        }
                        break;
                    }
                }
            } catch (Exception e) {
                mErrorHandler.error("Something went wrong", e);
            }
        }

        /**
         * Capture a still picture
         */
        private void captureStillPicture() {
            try {
                if (mCameraDevice == null) {
                    mErrorHandler.error("Internal error: mCameraDevice is null", null);
                    return;
                }

                ImageCaptureRequest request = mCaptureRequestQueue.peek();
                if (request == null) {
                    mErrorHandler.error("Internal Error: capture queue was empty", null);
                    return;
                }
                StillImageCaptureSession session = request.getSession();
                ImageReader imageReader = session.getImageReader();
                if (imageReader == null) {
                    mErrorHandler.error(
                            "Internal Error: capture session had a null ImageReader", null);
                    return;
                }

                // This is the CaptureRequest.Builder that we use to take a picture.
                final CaptureRequest.Builder captureBuilder =
                        mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);

                //set some defaults:

                // Use the same AE and AF modes as the preview.
                captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
                captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));

                request.configureCapture(captureBuilder);

                captureBuilder.addTarget(imageReader.getSurface());
                // Orientation

                CameraCaptureSession.CaptureCallback CaptureCallback
                        = new CameraCaptureSession.CaptureCallback() {

                    @Override
                    public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                   @NonNull CaptureRequest request,
                                                   @NonNull TotalCaptureResult result) {
                        Log.d(TAG, "captureCompleted() result = " + result);
                        unlockFocus();
                    }
                };

                mCaptureSession.stopRepeating();
                mCaptureSession.abortCaptures();
                mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
                mState = STATE_PICTURE_TAKEN;
            } catch (CameraAccessException e) {
                reportCameraAccessException(e);
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            this.process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            this.process(result);
        }

    };

    /**
     * Compares two {@code Size}s based on their areas.
     */
    private static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    /* Private Utils */
    private void reportCameraAccessException(CameraAccessException e) {
        mErrorHandler.error("Camera Access Exception", e);
    }

    /* Public Utils */
    public List<String> getAvailableCameras() throws CameraAccessException {
        CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        return Collections.unmodifiableList(Arrays.asList(manager.getCameraIdList()));
    }

    @Nullable
    public CameraCharacteristics getCameraInfo(String cameraId) throws CameraAccessException {
        CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) {
            mErrorHandler.error(NULL_MANAGER_MESSAGE, null);
            return null;
        }
        return manager.getCameraCharacteristics(cameraId);
    }

    @NonNull
    public Collection<Size> getAvailableSizes(String cameraId, int format) {
        CameraCharacteristics characteristics;
        try {
            characteristics = getCameraInfo(cameraId);
        } catch (CameraAccessException e) {
            reportCameraAccessException(e);
            return Collections.emptyList();
        }
        if (characteristics == null) {
            return Collections.emptyList();
        }
        StreamConfigurationMap map = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
            mErrorHandler.error(
                    "CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP was null " +
                            "for the given cameraId",
                    null);
            return Collections.emptyList();
        }
        return Collections.unmodifiableCollection(
                Arrays.asList(map.getOutputSizes(format)));
    }

    public Size getLargestAvailableSize(String cameraId, int imageFormat) {
        return Collections.max(
                getAvailableSizes(cameraId, imageFormat),
                new CompareSizesByArea());
    }

    public interface ErrorHandler {
        void error(String message, Exception e);

        void warning(String message);

        void info(String message);
    }

    public interface PreviewSizeCallback {
        void previewSizeSelected(int orientation, Size previewSize);
    }

    public PreviewSession createPreviewSession(@NonNull TextureView previewTextureView,
                                               @Nullable CaptureRequest.Builder previewRequest,
                                               @Nullable PreviewSizeCallback previewSizeSelected) {
        return new PreviewSession(
                previewTextureView,
                previewRequest,
                previewSizeSelected);
    }

    public static class PreviewSession {
        @NonNull
        private final TextureView previewTextureView;
        @Nullable
        private final CaptureRequest.Builder previewRequest;
        @Nullable
        private final PreviewSizeCallback previewSizeSelected;

        //actual size of the capture request (from list of available sizes)
        private Size previewSize;

        @NonNull
        TextureView getTextureView() {
            return previewTextureView;
        }

        @Nullable
        CaptureRequest.Builder getPreviewRequest() {
            return previewRequest;
        }

        @Nullable
        PreviewSizeCallback getPreviewSizeSelectedCallback() {
            return previewSizeSelected;
        }

        void setPreviewSize(Size previewSize) {
            this.previewSize = previewSize;
        }

        Size getPreviewSize() {
            return previewSize;
        }

        private PreviewSession(@NonNull TextureView previewTextureView,
                               @Nullable CaptureRequest.Builder previewRequest,
                               @Nullable PreviewSizeCallback previewSizeSelected) {
            if (previewTextureView == null) {
                throw new IllegalArgumentException("previewTextureView cannot be null");
            }
            this.previewTextureView = previewTextureView;
            this.previewRequest = previewRequest;
            this.previewSizeSelected = previewSizeSelected;
        }

    }

    /**
     * @param imageSize   The size of the image to capture //TODO how to get this? when is it checked?
     * @param imageFormat The format to capture in (from {@link android.graphics.ImageFormat}). E.g. ImageFormat.JPEG
     */
    public StillImageCaptureSession
    createStillImageCaptureSession(int imageFormat,
                                   @NonNull Size imageSize,
                                   @NonNull ImageReader.OnImageAvailableListener onImageAvailableListener) {
        // If no size was specified, use the largest available size.
        if (imageSize == null) {
            throw new IllegalArgumentException("imageSize cannot be null");
        }
        if (onImageAvailableListener == null) {
            throw new IllegalArgumentException("onImageAvailableListener cannot be null");
        }

        return new StillImageCaptureSession(
                imageFormat, imageSize,
                onImageAvailableListener,
                mBackgroundHandler);
    }


    public static class StillImageCaptureSession {
        private final int imageFormat;
        @Nullable
        private Size imageSize;
        private final ImageReader.OnImageAvailableListener onImageAvailableListener;
        private final Handler backgroundHandler;
        private ImageReader imageReader;

        public int getImageFormat() {
            return imageFormat;
        }

        @Nullable
        public Size getImageSize() {
            return imageSize;
        }

        private StillImageCaptureSession(int imageFormat,
                                         @NonNull Size imageSize,
                                         @NonNull ImageReader.OnImageAvailableListener onImageAvailableListener,
                                         Handler backgroundHandler) {
            this.imageFormat = imageFormat;
            this.onImageAvailableListener = onImageAvailableListener;
            this.backgroundHandler = backgroundHandler;
            this.imageSize = imageSize;

            this.imageReader = ImageReader.newInstance(imageSize.getWidth(), imageSize.getHeight(),
                    imageFormat, 2);
            this.imageReader.setOnImageAvailableListener(
                    onImageAvailableListener, backgroundHandler);

        }

        ImageReader getImageReader() {
            return imageReader;
        }

        private void closeImageReader() {
            if (this.imageReader != null) {
                this.imageReader.close();
                this.imageReader = null;
            }
        }
    }

    public interface ImageCaptureRequestConfiguration {
        void configure(CaptureRequest.Builder request);
    }

    private static class ImageCaptureRequest {
        @NonNull
        private final StillImageCaptureSession session;
        @Nullable
        private final ImageCaptureRequestConfiguration precapture;
        @NonNull
        private final ImageCaptureRequestConfiguration capture;
        @NonNull
        private ErrorHandler errorHandler;

        ImageCaptureRequest(@NonNull StillImageCaptureSession session,
                            @Nullable ImageCaptureRequestConfiguration precapture,
                            @NonNull ImageCaptureRequestConfiguration capture,
                            @NonNull ErrorHandler errorHandler) {

            this.session = session;
            this.precapture = precapture;
            this.capture = capture;
            this.errorHandler = errorHandler;
        }

        @NonNull
        StillImageCaptureSession getSession() {
            return session;
        }

        void configurePrecapture(CaptureRequest.Builder request) {
            if (precapture == null) {
                errorHandler.warning("Trying to configure precapture with null config");
                return;
            }
            precapture.configure(request);
        }

        boolean hasPrecapture() {
            return precapture != null;
        }

        void configureCapture(CaptureRequest.Builder request) {
            capture.configure(request);
        }
    }

    public boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(mActivity, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    //TODO: implement a acquirePermission(callback) convenience method similar to Dexter
    //https://github.com/Karumi/Dexter/tree/master/dexter/src/main/java/com/karumi/dexter
}
