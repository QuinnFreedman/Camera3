package com.avalancheevantage.camera3;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaActionSound;
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

import java.io.File;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static com.avalancheevantage.camera3.PrivateUtils.configureTransform;
import static com.avalancheevantage.camera3.PrivateUtils.setUpPreviewOutput;

/**
 * Camera3 is a wrapper around the Android Camera2 API. It is an attempt to
 * provide a single, much simpler, much safer interface to the notoriously
 * bad Camera2 API.
 *
 * It is adapted from the Camera2Basic Google Sample at
 * https://github.com/googlesamples/android-Camera2Basic which was released
 * under the Apache License v2 at
 * https://raw.githubusercontent.com/googlesamples/android-Camera2Basic/master/LICENSE
 *
 * @author Quinn Freedman
 */
public class Camera3 {
    private static final String TAG = "Camera3";

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    static final int MAX_PREVIEW_HEIGHT = 1080;


    private enum CameraState {
        //Showing camera preview
        PREVIEW,
        //Waiting for the focus to be locked.
        WAITING_LOCK,
        //Waiting for the exposure to be precapture state
        WAITING_PRECAPTURE,
        //Waiting for the exposure state to be something other than precapture
        WAITING_NON_PRECAPTURE
    }

    /**
     * The current state of camera state for taking pictures.
     *
     * @see #mCaptureCallback
     */
    private CameraState mState = CameraState.PREVIEW;
    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    static final String NULL_MANAGER_MESSAGE = "No camera manager. " +
            "`getSystemService(Context.CAMERA_SERVICE)` returned `null`";

    /**
     * A {@link CameraCaptureSession} for camera preview.
     */
    private CameraCaptureSession mCaptureSession;

    /**
     * A reference to the opened {@link CameraDevice}.
     */
    private CameraDevice mCameraDevice;

//    private Queue<ImageCaptureRequest> mCaptureRequestQueue = new ArrayDeque<>();
    private ImageCaptureRequest mCaptureRequest;

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    private Activity mActivity;
    private Integer mSensorOrientation;
    private CaptureRequest.Builder mPreviewRequestBuilder;

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
    public static final ErrorHandler ERROR_HANDLER_DEFAULT = null;

    @Nullable
    private CameraCharacteristics getCameraCharacteristics(String cameraId) {
        return PrivateUtils.getCameraCharacteristics(cameraId, mActivity, mErrorHandler);
    }

    @Nullable
    private Integer setSensorOrientation(String cameraId) {
        CameraCharacteristics characteristics = getCameraCharacteristics(cameraId);
        if (characteristics == null) {
            mErrorHandler.error("Camera Characteristics were null", null);
            return null;
        }
        mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        if (mSensorOrientation == null) {
            mErrorHandler.error(
                    "Invalid Camera Configuration: " +
                            "no field `SENSOR_ORIENTATION` for the specified cameraId", null);
        }
        return mSensorOrientation;
    }


    public Camera3(@NonNull Activity activity, @Nullable ErrorHandler errorHandler) {
        if (activity == null) {
            throw new IllegalArgumentException("activity is null in `new Camera3(activity, ...)`");
        }
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

            if (previewSession != null) {
                TextureView previewTextureView = previewSession.getTextureView();

                if (previewTextureView.isAvailable()) {
                    openCamera(cameraId,
                            new Size(previewTextureView.getWidth(),
                                    previewTextureView.getHeight()));
                } else {
                    previewTextureView.setSurfaceTextureListener(new PreviewTextureListener(cameraId));
                }
            } else {
                openCamera(cameraId, null);
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

        mErrorHandler.info("Adding capture request to queue...");
//        mCaptureRequestQueue.add(
//                new ImageCaptureRequest(session, precapture, capture, mErrorHandler));
        mCaptureRequest = new ImageCaptureRequest(session, precapture, capture, mErrorHandler);

        if (mState == CameraState.PREVIEW) {
            mErrorHandler.info(
                    "Camera was in PREVIEW state, so request will be resolved immediately");
            if (mPreviewSession == null) {
                captureStillPicture();
            } else {
                lockFocus();
            }
        } else {
            mErrorHandler.warning("trying to capture an image when state is "+mState.name());
        }
    }

    private void openCamera(String cameraId, @Nullable Size previewTextureSize) /*throws CameraAccessException*/ {
        mErrorHandler.info("opening camera");
        mErrorHandler.info("Preview texture size == " + previewTextureSize);

        Integer sensorOrientation = setSensorOrientation(cameraId);
        if (sensorOrientation == null) {
            return;
        }
        if (mPreviewSession != null) {
            if (previewTextureSize == null) {
                mErrorHandler.warning("Preview Session is not null but previewTextureSize is null");
            } else {
                setUpPreviewOutput(cameraId, previewTextureSize, sensorOrientation, mPreviewSession,
                        mActivity, mErrorHandler);
                configureTransform(mPreviewSession, previewTextureSize, mActivity, mErrorHandler);
            }
        }
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
        } catch (SecurityException e) {
            mErrorHandler.error(
                    "Permission denied to access the camera. " +
                    "Camera Permission must be obtained by activity before starting Camera3",
                    e);

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
            targetSurfaces.addAll(getCaptureTargetSurfaces());

            mCameraDevice.createCaptureSession(targetSurfaces,
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (mCameraDevice == null) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                            try {
                                //previewSession.getPreviewRequest() will never be null at this point
                                if (!previewSession.usesCustomRequest()) {
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

    private void createCameraCaptureSessionWithoutPreview() {
        try {
            mCameraDevice.createCaptureSession(getCaptureTargetSurfaces(),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (mCameraDevice == null) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            mErrorHandler.error(
                                    "Failed to configure CameraCaptureSession", null);
                        }
                    }, null);
        } catch (CameraAccessException e) {
            reportCameraAccessException(e);
        }
    }

    @NonNull
    private List<Surface> getCaptureTargetSurfaces() {
        List<Surface> targetSurfaces = new ArrayList<>();
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
        return targetSurfaces;
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private void lockFocus() {
        mErrorHandler.info("Locking focus...");
        try {
            // If the camera is previewing, tell it to lock focus.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);

            // Tell #mCaptureCallback to wait for the lock.
            mState = CameraState.WAITING_LOCK;

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
        mErrorHandler.info("Unlocking focus...");
        try {
            mState = CameraState.PREVIEW;
            if (mPreviewRequestBuilder != null) {
                // Reset the auto-focus trigger
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                        CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
                mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                        mBackgroundHandler);
                // After this, the camera will go back to the normal state of preview.
                mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback,
                        mBackgroundHandler);
            }
        } catch (CameraAccessException e) {
            reportCameraAccessException(e);
        }
        //FIXME
//        if (!mCaptureRequestQueue.isEmpty()) {
//            mErrorHandler.info(
//                    "Request queue was not empty -- immediately proceeding to capture another image");
//            if (mPreviewSession == null) {
//                captureStillPicture();
//            } else {
//                lockFocus();
//            }
//        }
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
            if (mPreviewSession != null) {
                configureTransform(mPreviewSession,
                        new Size(width, height),
                        mActivity,
                        mErrorHandler);
            }
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

            if (mPreviewSession != null) {
                createPreviewCameraCaptureSession(mPreviewSession);
            } else {
                createCameraCaptureSessionWithoutPreview();
            }
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

                if (mState != CameraState.PREVIEW) {
                    mErrorHandler.info("Processing capture result. (State: " + mState.name() + ")");
                }

                switch (mState) {
                    case PREVIEW: {
                        //do nothing
                        break;
                    }
                    case WAITING_LOCK: {
                        Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                        if (afState == null) {
                            mErrorHandler.info("AF State was null, moving to capture image");
                            captureStillPicture();
                        } else if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                                afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                            // CONTROL_AE_STATE can be null on some devices
                            Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                            if (aeState == null ||
                                    aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                                mErrorHandler.info("AE State null or converged, moving to capture image");
                                captureStillPicture();
                            } else {
                                ImageCaptureRequest request = mCaptureRequest;//mCaptureRequestQueue.peek();
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
                                    mState = CameraState.WAITING_PRECAPTURE;
                                    try {
                                        mCaptureSession.capture(
                                                mPreviewRequestBuilder.build(),
                                                mCaptureCallback,
                                                mBackgroundHandler);
                                    } catch (CameraAccessException e) {
                                        reportCameraAccessException(e);
                                    }
                                } else {
                                    mErrorHandler.info("Request does not have precapture, moving to capture image");
                                    captureStillPicture();
                                }
                            }
                        }
                        break;
                    }
                    case WAITING_PRECAPTURE: {
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                                aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                            mState = CameraState.WAITING_NON_PRECAPTURE;
                        }
                        break;
                    }
                    case WAITING_NON_PRECAPTURE: {
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
     * Capture a still picture. Should be called from inside {@link #mCaptureCallback}
     */
    private void captureStillPicture() {
        if (mCameraDevice == null) {
            mErrorHandler.error("Internal error: mCameraDevice is null", null);
            return;
        }

        ImageCaptureRequest request = mCaptureRequest;//mCaptureRequestQueue.poll();
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

        try {
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

            CameraCaptureSession.CaptureCallback captureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    mErrorHandler.info("Capture Completed. result == " + result);
                    unlockFocus();
                }
            };

            mCaptureSession.stopRepeating();
            mCaptureSession.abortCaptures();
            mCaptureSession.capture(captureBuilder.build(), captureCallback, null);
            mState = CameraState.PREVIEW;
        } catch (CameraAccessException e) {
            reportCameraAccessException(e);
        }
    }



    /* Private Utils */
    private void reportCameraAccessException(CameraAccessException e) {
       PrivateUtils.reportCameraAccessException(e, mErrorHandler);
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
                new PrivateUtils.CompareSizesByArea());
    }

    public interface PreviewSizeCallback {
        void previewSizeSelected(int orientation, Size previewSize);
    }

    public void saveImage(Image image, File file) {
        if (!hasWritePermission()) {
            mErrorHandler.error("Unable to save file. This application does not have the " +
                    "required permission: WRITE_EXTERNAL_STORAGE", null);
        }
        mBackgroundHandler.post(new ImageSaver(image, file));
    }

    public PreviewSession createPreviewSession(@NonNull TextureView previewTextureView,
                                               @Nullable CaptureRequest.Builder previewRequest,
                                               @Nullable PreviewSizeCallback previewSizeSelected) {
        return new PreviewSession(
                previewTextureView,
                previewRequest,
                previewSizeSelected);
    }

    /**
     * @param imageSize   The size of the image to capture //TODO how to get this? when is it checked?
     * @param imageFormat The format to capture in (from {@link android.graphics.ImageFormat}). E.g. ImageFormat.JPEG
     */
    public StillImageCaptureSession
    createStillImageCaptureSession(int imageFormat,
                                   @NonNull Size imageSize,
                                   @NonNull OnImageAvailableListener onImageAvailableListener) {
        return new StillImageCaptureSession(
                imageFormat, imageSize,
                onImageAvailableListener,
                mBackgroundHandler,
                mErrorHandler);
    }

    public interface ImageCaptureRequestConfiguration {
        void configure(CaptureRequest.Builder request);
    }


    public boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(mActivity, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    public boolean hasWritePermission() {
        return ContextCompat.checkSelfPermission(mActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    //TODO: implement a acquirePermission(callback) convenience method similar to Dexter
    //https://github.com/Karumi/Dexter/tree/master/dexter/src/main/java/com/karumi/dexter
}
