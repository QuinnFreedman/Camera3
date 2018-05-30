/*
 * Copied in part from the Google Samples github repository
 * (https://github.com/googlesamples/android-Camera2Basic),
 * with substantial modification
 *
 * The original file was distributed under the Apache v2 license
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is redistributed under the MIT License.
 *
 * See the included LICENSE file
 */


package com.avalancheevantage.android.camera3;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;

import org.jetbrains.annotations.Contract;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

/**
 * Camera3 is a wrapper around the Android Camera2 API. It is an attempt to
 * provide a single simple and safe interface to the notoriously
 * bad Android Camera2 API.
 * <p>
 * It is adapted from the Camera2Basic Google Sample at
 * https://github.com/googlesamples/android-Camera2Basic
 *
 * @author Quinn Freedman
 */
public final class Camera3 {
    public static final CaptureRequestConfiguration PRECAPTURE_CONFIG_TRIGGER_AUTO_FOCUS =
            new CaptureRequestConfiguration() {
                @Override
                public void configure(CaptureRequest.Builder request) {
                    // This is how to tell the camera to trigger auto exposure.
                    request.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                            CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
                }
            };
    public static final CaptureRequestConfiguration PRECAPTURE_CONFIG_NONE = null;
    public static final CaptureRequestConfiguration CAPTURE_CONFIG_DEFAULT =
            new CaptureRequestConfiguration() {
                @Override
                public void configure(CaptureRequest.Builder request) {

                }
            };
    public static final ErrorHandler ERROR_HANDLER_DEFAULT = null;
    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    static final int MAX_PREVIEW_WIDTH = 1920;
    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    static final int MAX_PREVIEW_HEIGHT = 1080;
    static final String NULL_MANAGER_MESSAGE = "No camera manager. " +
            "`getSystemService(Context.CAMERA_SERVICE)` returned `null`";
    private static final String TAG = "Camera3";
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

    private final BlockingQueue<ImageCaptureRequest> mCaptureRequestQueue = new
            LinkedBlockingDeque<>();
    @Nullable
    private Runnable mOnSessionStartedCallback;
    /**
     * The current state of camera state for taking pictures.
     *
     * @see #mCaptureCallback
     */
    private CameraState mState = CameraState.WAITING_CAMERA_OPEN;
    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    /**
     * A {@link CameraCaptureSession} for camera preview.
     */
    private CameraCaptureSession mCaptureSession;
    /**
     * A reference to the opened {@link CameraDevice}.
     */
    private CameraDevice mCameraDevice;
    private ImageCaptureRequest mCurrentCaptureRequest;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private Context mContext;
    private Integer mSensorOrientation;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    @Nullable
    private Session mSession;
    @NonNull
    private ErrorHandler mErrorHandler;
    private CaptureRequest mPreviewRequest;
    private boolean mStarted = false;
    @Nullable
    private CaptureResultListener mCaptureResultListener = null;
    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            CameraState oldState = mState;
            try {
                if (mState != CameraState.PREVIEW) {
                    mErrorHandler.info("Processing capture result. (State: " + mState.name() + ")");
                }

                switch (mState) {
                    case PREVIEW: {
                        //do nothing
                        break;
                    }
                    case WAITING_FOCUS_LOCK: {
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
                                mErrorHandler.info("AE State null or converged, moving to capture" +
                                        " image");
                                captureStillPicture();
                            } else {
                                ImageCaptureRequest request = mCurrentCaptureRequest;
                                if (request == null) {
                                    mErrorHandler.error(
                                            "Internal Error: Request Queue was empty when " +
                                                    "trying to run precapture",
                                            null);
                                    return;
                                }
                                if (request.hasPrecapture()) {

                                    mErrorHandler.info("running precapture sequence");
                                    // Run precapture:
                                    request.configurePrecapture(mPreviewRequestBuilder);

                                    // Tell #mCaptureCallback to wait for the precapture sequence
                                    // to be set.
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
                                    mErrorHandler.info("Request does not have precapture, moving " +
                                            "to capture image");
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
                        if (aeState == null || aeState != CaptureResult
                                .CONTROL_AE_STATE_PRECAPTURE) {
                            captureStillPicture();
                        }
                        break;
                    }
                }
            } catch (Exception e) {
                reportUnknownException(e);
            }

            try {
                if (mCaptureResultListener != null) {
                    mCaptureResultListener.onResult(oldState, result);
                }
            } catch (Exception e) {
                mErrorHandler.error("Error in CaptureResultListener callback", e);
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
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;

            if (requireNotNull(mSession,
                    "Internal error: session is null when calling openCamera()")) {
                return;
            }

            if (mSession.getPreview() != null) {
                createPreviewCameraCaptureSession(mSession.getPreview());
            } else {
                createCameraCaptureSessionWithoutPreview();
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mErrorHandler.info("Camera disconnected");
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            String errorName;
            switch (error) {
                case ERROR_CAMERA_DEVICE:
                    errorName = "ERROR_CAMERA_DEVICE";
                    break;
                case ERROR_CAMERA_DISABLED:
                    errorName = "ERROR_CAMERA_DISABLED";
                    break;
                case ERROR_CAMERA_IN_USE:
                    errorName = "ERROR_CAMERA_IN_USE";
                    break;
                case ERROR_CAMERA_SERVICE:
                    errorName = "ERROR_CAMERA_SERVICE";
                    break;
                case ERROR_MAX_CAMERAS_IN_USE:
                    errorName = "ERROR_MAX_CAMERAS_IN_USE";
                    break;
                default:
                    errorName = "No error message";
                    break;
            }
            mErrorHandler.error("Got error when opening camera: " + errorName +
                            "(Error code: " + error + ")",
                    null);
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

    };


    /**
     * Creates a new Camera3 manager instance. Only one such manager should have to exist
     * per {@link Activity}. Many different preview sessions, capture sessions, and capture
     * requests can exist for one Camera3 object.
     *
     * @param context      The context from which to access the camera. Should usually just be the
     *                     current activity
     * @param errorHandler An {@link ErrorHandler} to handle any errors that arise over the lifetime
     */
    public Camera3(@NonNull Context context, @Nullable ErrorHandler errorHandler) {
        //noinspection ConstantConditions
        if (context == null) {
            throw new IllegalArgumentException("activity is null in `new Camera3(activity, ...)`");
        }
        this.mContext = context;
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

    @Contract(pure = true)
    private boolean isRecordingVideo() {
        return mState == CameraState.RECORDING_VIDEO;
    }

    @Nullable
    private CameraCharacteristics getCameraCharacteristics(String cameraId) {
        return PrivateUtils.getCameraCharacteristics(cameraId, mContext, mErrorHandler);
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

    /**
     * @see Camera3#startCaptureSession(String, PreviewHandler, List, List, Runnable)
     */
    public void startCaptureSession(@NonNull String cameraId,
                                    @Nullable PreviewHandler previewHandler,
                                    @Nullable List<StillCaptureHandler> stillCaptureSessions) {
        startCaptureSession(cameraId, previewHandler, stillCaptureSessions, null, null);
    }

    /**
     * @see Camera3#startCaptureSession(String, PreviewHandler, List, List, Runnable)
     */
    public void startCaptureSession(@NonNull String cameraId,
                                    @Nullable PreviewHandler previewHandler,
                                    @Nullable List<StillCaptureHandler> stillCaptureSessions,
                                    @Nullable List<VideoCaptureHandler> videoCaptureHandlers) {
        startCaptureSession(cameraId, previewHandler, stillCaptureSessions,
                videoCaptureHandlers, null);
    }

    /**
     * Starts a new session. Only one session can be open at a time.
     *
     * @param cameraId             which camera to use (from {@link Camera3#getAvailableCameras()}).
     * @param previewHandler       an object representing the configuration for the camera
     *                             preview, or
     *                             <code>null</code> to not show a preview (see
     *                             {@link PreviewHandler}).
     * @param stillCaptureHandlers a list of zero or more {@link StillCaptureHandler}'s,
     *                             or null if no still images will be captured. Usually only one
     *                             is required.
     * @param videoCaptureHandlers a list of zero or more {@link VideoCaptureHandler}'s,
     *                             or null if no video will be recorded. Usually a maximum of one
     *                             is required.
     * @param onSessionStarted     an optional callback that will be called to notify the user when
     *                             the camera has been opened and the capture session has been
     *                             started.
     * @see PreviewHandler
     * @see StillCaptureHandler
     * @see VideoCaptureHandler
     */
    public void startCaptureSession(@NonNull String cameraId,
                                    @Nullable PreviewHandler previewHandler,
                                    @Nullable List<StillCaptureHandler> stillCaptureHandlers,
                                    @Nullable List<VideoCaptureHandler> videoCaptureHandlers,
                                    @Nullable Runnable onSessionStarted) {
        try {
            //noinspection ConstantConditions
            if (cameraId == null) {
                throw new IllegalArgumentException("cameraId cannot be null");
            }
            if (this.mStarted) {
                mErrorHandler.warning(
                        "A capture session is already started. The current session will be " +
                                "terminated");
                //TODO it is redundant to close and then re-open some things. room for optimization
                pause();
            }

            if (stillCaptureHandlers == null) {
                stillCaptureHandlers = new ArrayList<>();
            }
            if (videoCaptureHandlers == null) {
                videoCaptureHandlers = new ArrayList<>();
            }

            if (previewHandler == null && stillCaptureHandlers.isEmpty()
                    && videoCaptureHandlers.isEmpty()) {
                throw new IllegalArgumentException("no targets provided for capture session");
            }
            mSession = new Session(cameraId, previewHandler,
                    stillCaptureHandlers, videoCaptureHandlers);
            mOnSessionStartedCallback = onSessionStarted;
            startCaptureSession(mSession);
        } catch (Exception e) {
            reportUnknownException(e);
        }
    }

    /**
     * @see Camera3#resume(Runnable)
     */
    public void resume() {
        resume(null);
    }

    /**
     * Resumes the capture session established by the last call to
     * {@link Camera3#startCaptureSession(String, PreviewHandler, List)}
     *
     * @param onSessionRestarted an optional callback that will be called to notify the user when
     *                           the camera has been opened and the capture session has been
     *                           restarted.
     */
    public void resume(@Nullable Runnable onSessionRestarted) {
        if (mStarted) {
            mErrorHandler.warning("calling resume when a session is already started.");
            return;
        }
        if (mSession == null) {
            throw new IllegalStateException(
                    "No session configured. Call startCaptureSession(...) first");
        }
        mOnSessionStartedCallback = onSessionRestarted;
        startCaptureSession(mSession);
    }

    /**
     * Does the actual work of starting the camera session. Called by both
     * {@link Camera3#startCaptureSession(String, PreviewHandler, List)} and
     * {@link Camera3#resume()}
     */
    private void startCaptureSession(@NonNull Session session) {
        this.mStarted = true;
        startBackgroundThread();
        for (StillCaptureHandler imageCaptureSession : session.getStillCaptures()) {
            imageCaptureSession.initialize(mBackgroundHandler, this);
            if (imageCaptureSession.getImageReader() == null ||
                    !imageCaptureSession.getImageReader().getSurface().isValid()) {
                mErrorHandler.warning("Internal Error: Image capture surface is not valid");
            }
        }

        for (VideoCaptureHandler videoHandler : session.videoCaptureHandlers) {
            videoHandler.setErrorHandler(mErrorHandler);
        }

        mErrorHandler.info("starting preview");

        if (session.getPreview() != null && session.getPreview().getTextureView() != null) {
            TextureView previewTextureView = session.getPreview().getTextureView();

            if (previewTextureView.isAvailable()) {
                openCamera(session.getCameraId());
            } else {
                previewTextureView.setSurfaceTextureListener(
                        new PreviewTextureListener(session.getCameraId()));
            }
        } else {
            openCamera(session.getCameraId());
        }
    }

    /**
     * Stops background threads and frees the camera.
     * <p>
     * <code>pause</code> <b>must</b> be called in {@link Activity#onPause()} form the activity
     * that started the session. Otherwise, the app could close without relinquishing control of the
     * camera.
     */
    public void pause() {
        try {
            mErrorHandler.info("pause");
            if (!this.mStarted) {
                mErrorHandler.warning("Calling `pause()` when Camera3 is already stopped.");
                return;
            }
            closeCamera();
            stopBackgroundThread();
            this.mStarted = false;
        } catch (Exception e) {
            reportUnknownException(e);
        }
    }

    /**
     * Registers a listener to be updated whenever Camera3 gets a result from the camera. This is
     * useful for keeping track of what state the camera is in (e.g. if you want to play a sound
     * when the capture is completed). Note: this is <b>NOT</b> the way you receive the result of
     * the capture (i.e. an image). For that, use the {@link OnImageAvailableListener} in
     * {@link StillCaptureHandler#StillCaptureHandler(int, Size, OnImageAvailableListener)}
     *
     * @param listener the listener to register or {@code null} to stop listening
     */
    public void setCaptureResultListener(@Nullable final CaptureResultListener listener) {
        mCaptureResultListener = listener;
    }

    /**
     * Asyncronously requests to start capturing video
     *
     * @param handler    the handler responsible for configuring and writing the video
     * @param outputFile the file where the video will be saved, or null to write to a temp file
     * @param callback   a callback function to be called when the video capture has been started
     */
    public void startVideoCapture(@NonNull final VideoCaptureHandler handler,
                                  @Nullable final File outputFile,
                                  @Nullable final VideoCaptureStartedCallback callback) {
        if (requireNotNull(handler, "VideoCaptureHandler cannot be null") ||
                requireNotNull(mSession, "Trying to start video capture when session is null")) {
            return;
        }

        if (!mSession.getVideoCaptures().contains(handler)) {
            mErrorHandler.error(
                    "VideoCaptureHandler is not configured with the current camera session",
                    null);
            return;
        }

        //check permissions
        if (!(hasCameraPermission() && hasMicrophonePermission())) {
            mErrorHandler.error(
                    "Permission denied to access the camera or microphone. " +
                            "Camera Permission must be obtained by activity before starting " +
                            "Camera3",
                    null);
        }

        final File output;
        try {
            //TODO get suffix from handler's
            output = outputFile == null ? File.createTempFile("vid", ".mp4") : outputFile;
            if (!output.exists()) {
                assert outputFile != null;
                //noinspection ResultOfMethodCallIgnored
                outputFile.createNewFile();
            }
        } catch (IOException e) {
            mErrorHandler.error("Unable to create video file", e);
            return;
        }

        try {
            //close preview
            if (mCaptureSession != null) {
                mCaptureSession.stopRepeating();
                mCaptureSession.abortCaptures();
                mCaptureSession.close();
                mCaptureSession = null;
            }

            mErrorHandler.info(String.format("Recording %s video to temporary file: %s",
                    handler.getVideoSize().toString(), output.getPath()));

            int rotation = PrivateUtils.getScreenRotation(mContext, mErrorHandler);
            handler.setUpMediaRecorder(output.getPath(), mSensorOrientation, rotation);

            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(
                    CameraDevice.TEMPLATE_RECORD);

            List<Surface> surfaces = new ArrayList<>();

            PreviewHandler previewHandler = mSession.getPreview();

            if (previewHandler != null) {
                SurfaceTexture previewTexture = previewHandler.getSurfaceTexture();

                Size previewSize = previewHandler.getPreviewSize();
                previewTexture.setDefaultBufferSize(
                        previewSize.getWidth(),previewSize.getHeight());

                Surface previewSurface = new Surface(previewTexture);
                surfaces.add(previewSurface);
                mPreviewRequestBuilder.addTarget(previewSurface);
            }

            Surface recorderSurface = handler.getRecorderSurface();
            surfaces.add(recorderSurface);
            mPreviewRequestBuilder.addTarget(recorderSurface);

            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    mCaptureSession = cameraCaptureSession;
                    if (mSession.getPreview() != null) {
                        try {
                            mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(),
                                    null, mBackgroundHandler);
                        } catch (CameraAccessException e) {
                            reportCameraAccessException(e);
                        }
                    }
                    handler.start();
                    if (callback != null) {
                        callback.captureStarted(handler, output);
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    mErrorHandler.error("Failed to configure video capture session", null);
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            reportCameraAccessException(e);
        } catch (Exception e) {
            mErrorHandler.error("Unable to start video capture", e);
        }
    }

    /**
     * Stops video recording. Video recording must have already started.
     *
     * Note: the starting video recording is asyncronous so if you stop immediately after you stop
     * this method may cause an IllegalStateException
     *
     * @param handler the handler that is currently recording video
     *
     */
    public void stopVideoCapture(@NonNull VideoCaptureHandler handler) {
        try {
            handler.stop();
        } catch (IllegalStateException e) {
            mErrorHandler.error("Video already stopped", e);
        }


        if (mSession != null && mSession.getPreview() != null) {
            createPreviewCameraCaptureSession(mSession.getPreview());
        } else {
            createCameraCaptureSessionWithoutPreview();
        }
    }

    /**
     * Starts the process of capturing a still image from the camera. Should be called after calling
     * {@link Camera3#startCaptureSession(String, PreviewHandler, List)}
     *
     * @param handler    the {@link StillCaptureHandler} which will be responsible for processing
     *                   the image
     * @param precapture the precapture configuration. Use {@link
     *                   Camera3#PRECAPTURE_CONFIG_TRIGGER_AUTO_FOCUS}
     *                   to trigger auto focus prior to the image capture or use {@link
     *                   Camera3#PRECAPTURE_CONFIG_NONE} to skip precapture (and use the preview
     *                   focus). This will speed up the capture process.
     * @param capture    the configuration for the actual image capture (on top of the defaults).
     *                   Use {@link Camera3#CAPTURE_CONFIG_DEFAULT} if you don't want to change the
     *                   default configuration at all.
     */
    public void captureImage(@NonNull StillCaptureHandler handler,
                             @Nullable CaptureRequestConfiguration precapture,
                             @NonNull CaptureRequestConfiguration capture) {
        try {
            //TODO make capture nullable to be consistent with precapture
            if (!this.mStarted) {
                throw new IllegalStateException("trying to call captureImage(...) " +
                        "but a capture handler has not been started yet");
            }

            if (requireNotNull(handler, "capture handler is null") ||
                    requireNotNull(handler.getImageReader(), "capture handler imageReader is " +
                            "null") ||
                    requireNotNull(mSession,
                            "Internal error: Somehow the session is null even though started is " +
                                    "true")) {
                return;
            }

            if (!handler.getImageReader().getSurface().isValid()) {
                mErrorHandler.warning("Internal Error: The provided handler's surface is invalid");
            }

            if (!mSession.getStillCaptures().contains(handler)) {
                mErrorHandler.error(
                        "StillCaptureHandler is not configured with the current camera session",
                        null);
                return;
            }

            mErrorHandler.info("Adding capture request to queue...");
            mCaptureRequestQueue.add(
                    new ImageCaptureRequest(handler, precapture, capture, mErrorHandler));

            if (mState == CameraState.PREVIEW) {
                mErrorHandler.info(
                        "Camera was in PREVIEW state, so request will be resolved immediately");
                popRequestQueue();
            } else {
                mErrorHandler.info("Camera state is " + mState.name() + ". The image will be " +
                        "captured ASAP");
            }

        } catch (Exception e) {
            reportUnknownException(e);
        }
    }

    void popRequestQueue() {
        mErrorHandler.info("Popping request queue...");
        mErrorHandler.info("About " + mCaptureRequestQueue.size() + " requests left in queue.");
        CameraState state = mState;
        if (state != CameraState.PREVIEW) {
            mErrorHandler.info("Trying to pop queue when in mode: " + state.name() + ". Aborting.");
            return;
        }
        mCurrentCaptureRequest = mCaptureRequestQueue.poll();
        if (mCurrentCaptureRequest != null) {
            mErrorHandler.info(
                    "Request queue was not empty -- immediately proceeding to capture another " +
                            "image");
            if (!mStarted) {
                mErrorHandler.warning(
                        "Attempting to dequeue capture request after the session has been stopped");
            }
            if (requireNotNull(mSession,
                    "Internal error: Somehow the session is null even though started is true")) {
                return;
            }
            if (mSession.getPreview() == null) {
                captureStillPicture();
            } else {
                lockFocus();
            }
        } else {
            mErrorHandler.info("Request queue was empty -- nothing to do");
        }
    }

    @Contract(pure = true)
    public ErrorHandler getErrorHandler() {
        return mErrorHandler;
    }

    /**
     * It is only safe to call {@link Camera3#resume()} if you have already configured a capture
     * session with
     * {@link Camera3#startCaptureSession(String, PreviewHandler, List, List, Runnable)}.
     * This method allows you to check that status.
     *
     * @return true if this instance of camera3 is configured with a capture session
     */
    @Contract(pure = true)
    public boolean captureConfigured() {
        return mSession != null;
    }

    /**
     * Images can only be captured between calling {@link Camera3#resume()} or
     * {@link Camera3#startCaptureSession(String, PreviewHandler, List, List, Runnable)} and calling
     * {@link Camera3#pause()}. This method allows you to check whether the camera can receive
     * requests. If this is false, you can call {@link Camera3#resume()}.
     *
     * @return <code>true</code> if the camera can receive requests
     */
    @Contract(pure = true)
    public boolean isStarted() {
        return mStarted;
    }

    /**
     * Check if there are any enqueued requests that have not yet been resolved
     *
     * @return <code>true</code> if the request queue is not empty.
     */
    @Contract(pure = true)
    public boolean requestsInQueue() {
        return !mCaptureRequestQueue.isEmpty();
    }

    private void openCamera(String cameraId) {
        mErrorHandler.info("opening camera");

        Integer sensorOrientation = setSensorOrientation(cameraId);
        if (sensorOrientation == null) {
            return;
        }
        if (requireNotNull(mSession,
                "Internal error: session is null when calling openCamera()")) {
            return;
        }
        if (mSession.getPreview() != null) {
            Size previewTextureSize = mSession.getPreview().getPreferredSize();
            mErrorHandler.info("Preview texture size == " + previewTextureSize);
            PrivateUtils.setUpPreviewOutput(cameraId, previewTextureSize, sensorOrientation,
                    mSession.getPreview(), mContext, mErrorHandler);
            PrivateUtils.configureTransform(mSession.getPreview(),
                    mContext, mErrorHandler);

        }
        CameraManager manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                mErrorHandler.error("Time out waiting to acquire camera lock.", null);
                return;
            }
            if (requireNotNull(manager, NULL_MANAGER_MESSAGE)) {
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
                            "Camera Permission must be obtained by activity before starting " +
                            "Camera3",
                    e);

        }
    }

    private void startBackgroundThread() {
        if (Thread.currentThread() == mBackgroundThread) {
            mErrorHandler.warning("Starting session from background thread");
            return;
        }
        mErrorHandler.info("Starting background threads...");
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (Thread.currentThread() == mBackgroundThread) {
            return;
        }
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
        mState = CameraState.WAITING_CAMERA_OPEN;
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
            if (mSession == null) {
                mErrorHandler.warning("Internal Error: session null when closing camera");
                return;
            }
            for (StillCaptureHandler captureHandler : mSession.getStillCaptures()) {
                captureHandler.close();

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
    private void createPreviewCameraCaptureSession(@NonNull final PreviewHandler previewHandler) {

        if (requireNotNull(previewHandler, "Internal error: previewHandler is null")) {
            return;
        }
        Size previewSize = previewHandler.getPreviewSize();
        if (requireNotNull(previewSize, "Internal error: previewHandler.previewSize is null")) {
            return;
        }

        SurfaceTexture texture = previewHandler.getSurfaceTexture();
        if (requireNotNull(texture, "previewHandler.getSurfaceTexture() is null")) {
            return;
        }

        // We configure the size of default buffer to be the size of camera preview we want.
        texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

        // This is the output Surface we need to start preview.
        Surface surface = new Surface(texture);
        if (!surface.isValid()) {
            mErrorHandler.warning("Internal Error: preview surface is not valid");
        }

        try {
            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewHandler.configureCaptureRequest(mPreviewRequestBuilder);
            mPreviewRequestBuilder.addTarget(surface);


            // Create a CameraCaptureSession for camera preview.
            List<Surface> targetSurfaces = new ArrayList<>(singletonList(surface));
            targetSurfaces.addAll(getCaptureTargetSurfaces());

            Log.d(TAG, "target surfaces == " + targetSurfaces);
            mCameraDevice.createCaptureSession(targetSurfaces,
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession
                                                         cameraCaptureSession) {
                            // The camera is already closed
                            if (mCameraDevice == null) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                            try {
                                //previewHandler.getRequestConfig() will never be null at this point
                                if (!previewHandler.usesCustomRequest()) {
                                    // Auto focus should be continuous for camera preview.
                                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                }

                                // Finally, we start displaying the camera preview.
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                        mCaptureCallback, mBackgroundHandler);
                                onSessionStarted();
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
                        public void onConfigured(@NonNull CameraCaptureSession
                                                         cameraCaptureSession) {
                            // The camera is already closed
                            if (mCameraDevice == null) {
                                return;
                            }

                            mCaptureSession = cameraCaptureSession;
                            onSessionStarted();
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

    private void onSessionStarted() {
        mState = CameraState.PREVIEW;
        mErrorHandler.info("Session started. Calling onSessionStarted callback...");
        if (mOnSessionStartedCallback != null) {
            mOnSessionStartedCallback.run();
        }
        popRequestQueue();
    }

    @NonNull
    private List<Surface> getCaptureTargetSurfaces() {
        List<Surface> targetSurfaces = new ArrayList<>();
        if (requireNotNull(mSession,
                "Internal error: session is null when calling getCaptureTargetSurfaces()")) {
            return new ArrayList<>();
        }
        for (StillCaptureHandler captureHandler : mSession.getStillCaptures()) {
            if (captureHandler == null) {
                mErrorHandler.error("a StillCaptureHandler is null", null);
                continue;
            }
            if (captureHandler.getImageReader() == null) {
                mErrorHandler.error("a StillCaptureHandler has a null ImageReader", null);
                continue;
            }
            if (!captureHandler.getImageReader().getSurface().isValid()) {
                mErrorHandler.warning("Internal Error: a StillCaptureHandler has an invalid " +
                        "surface");
            }
            targetSurfaces.add(captureHandler.getImageReader().getSurface());
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
            mState = CameraState.WAITING_FOCUS_LOCK;

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
    }

    /*
       Callbacks
     */

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

    /**
     * Capture a still picture. Should be called from inside {@link Camera3#mCaptureCallback}
     */
    private void captureStillPicture() {
        if (mCameraDevice == null) {
            mErrorHandler.error("Internal Error: mCameraDevice is null", null);
            return;
        }

        ImageCaptureRequest request = mCurrentCaptureRequest;
        if (request == null) {
            mErrorHandler.error("Internal Error: capture queue was empty", null);
            return;
        }
        StillCaptureHandler session = request.getSession();
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

            int rotation = PrivateUtils.getScreenRotation(mContext, mErrorHandler);
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

            //if this is called from a thread without a looper (esp in testing), use the background
            //handler to handle the result
            //(A null handler will tell mCaptureSession.capture to use the current thread's looper)
            Handler captureHandler = null;
            if (Looper.myLooper() == null) {
                captureHandler = mBackgroundHandler;
            }
            mCaptureSession.capture(captureBuilder.build(), captureCallback, captureHandler);
            mState = CameraState.PREVIEW;
        } catch (CameraAccessException e) {
            reportCameraAccessException(e);
        }
    }

    /* Private Utils */
    private void reportCameraAccessException(CameraAccessException e) {
        PrivateUtils.reportCameraAccessException(e, mErrorHandler);
    }

    private void reportUnknownException(Exception e) {
        mErrorHandler.error("Oops! Something went wrong.", e);
    }

    @Contract("null, _ -> true")
    private boolean requireNotNull(Object o, String message) {
        return PrivateUtils.checkNull(o, message, mErrorHandler);
    }

    /* Public Utils */
    public List<String> getAvailableCameras() throws CameraAccessException {
        CameraManager manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        return Collections.unmodifiableList(asList(manager.getCameraIdList()));
    }

    @Nullable
    public CameraCharacteristics getCameraInfo(String cameraId) throws CameraAccessException {
        CameraManager manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        if (requireNotNull(manager, NULL_MANAGER_MESSAGE)) {
            return null;
        }
        return manager.getCameraCharacteristics(cameraId);
    }

    @Nullable
    private StreamConfigurationMap getConfigurationMap(@NonNull String cameraId) {
        CameraCharacteristics characteristics;
        try {
            characteristics = getCameraInfo(cameraId);
        } catch (CameraAccessException e) {
            reportCameraAccessException(e);
            return null;
        }
        if (characteristics == null) {
            return null;
        }
        StreamConfigurationMap map = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
            mErrorHandler.error(
                    "CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP was null " +
                            "for the given cameraId",
                    null);
            return null;
        }

        return map;
    }

    /**
     * TODO doc
     *
     * @param cameraId
     * @param format
     * @return
     */
    @NonNull
    public Collection<Size> getAvailableImageSizes(@NonNull String cameraId, int format) {

        StreamConfigurationMap map = getConfigurationMap(cameraId);

        return map == null ? Collections.<Size>emptySet() :
                Collections.unmodifiableCollection(asList(map.getOutputSizes(format)));
    }

    /**
     * TODO doc
     *
     * @param cameraId
     * @param imageFormat
     * @return
     */
    @Nullable
    public Size getLargestAvailableImageSize(String cameraId, int imageFormat) {
        try {
            return Collections.max(
                    getAvailableImageSizes(cameraId, imageFormat),
                    new PrivateUtils.CompareSizesByArea());
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    /**
     * TODO doc
     * TODO BUG sometimes some of these sizes don't actually work - lead to surface abandoned error
     *
     * @param cameraId
     * @return
     */
    @NonNull
    public Collection<Size> getAvailableVideoSizes(@NonNull String cameraId) {

        StreamConfigurationMap map = getConfigurationMap(cameraId);

        return map == null ? Collections.<Size>emptySet() :
                Collections.unmodifiableCollection(asList(map.getOutputSizes(MediaRecorder.class)));
    }

    /**
     * TODO doc
     *
     * @param cameraId
     * @return
     */
    public Size getDefaultVideoSize(@NonNull String cameraId) {
        List<Size> choices = new ArrayList<>(getAvailableVideoSizes(cameraId));
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
                return size;
            }
        }
        mErrorHandler.warning("Couldn't find any suitable video size");
        return choices.get(choices.size() - 1);
    }

    /**
     * A utility method to synchronously save an image file. The caller must obtain permission to
     * write to external storage before calling this method.
     *
     * @param image the image to save
     * @param file  the file to write to
     */
    public void saveImage(Image image, File file) {
        if (!hasWritePermission()) {
            mErrorHandler.error("Unable to save file. This application does not have the " +
                    "required permission: WRITE_EXTERNAL_STORAGE", null);
        }
        //this is synchronous for now because if you get the image from onImageAvailable it could
        // be closed
        // before the ImageSaver runs. onImageAvailable is called in a background thread anyway.

        //mBackgroundHandler.post(new ImageSaver(image, file));
        new ImageSaver(image, file).run();
    }

    /**
     * Checks if the app has permission to access the camera
     *
     * @return true if you can capture images
     * @see Manifest.permission.CAMERA
     */
    @SuppressWarnings("JavadocReference")
    public boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Checks if the app has permission to access the microphone
     *
     * @return true if you can record audio
     * @see Manifest.permission.RECORD_AUDO
     */
    @SuppressWarnings("JavadocReference")
    public boolean hasMicrophonePermission() {
        return ContextCompat.checkSelfPermission(mContext, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Checks if the app has permission to write to the file system.
     *
     * @return true if you can write files
     * @see Manifest.permission.WRITE_EXTERNAL_STORAGE
     */
    @SuppressWarnings("JavadocReference")
    public boolean hasWritePermission() {
        return ContextCompat.checkSelfPermission(mContext, Manifest.permission
                .WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    public enum CameraState {
        //Waiting for the camera to open
        WAITING_CAMERA_OPEN,
        //Showing camera preview
        PREVIEW,
        //Waiting for the focus to be locked.
        WAITING_FOCUS_LOCK,
        //Waiting for the exposure to be precapture state
        WAITING_PRECAPTURE,
        //Waiting for the exposure state to be something other than precapture
        WAITING_NON_PRECAPTURE,
        //Recording video. May also be showing preview
        RECORDING_VIDEO
    }

    public interface PreviewSizeCallback {
        void previewSizeSelected(int orientation, Size previewSize);
    }

    private static final class Session {
        @NonNull
        private final String cameraId;
        @Nullable
        private final PreviewHandler previewHandler;
        @NonNull
        private final List<StillCaptureHandler> stillCaptureHandlers;
        @NonNull
        private final List<VideoCaptureHandler> videoCaptureHandlers;

        Session(@NonNull String cameraId,
                @Nullable PreviewHandler previewHandler,
                @NonNull List<StillCaptureHandler> stillCaptureHandlers,
                @NonNull List<VideoCaptureHandler> videoCaptureHandlers) {
            this.cameraId = cameraId;
            this.previewHandler = previewHandler;
            this.stillCaptureHandlers = stillCaptureHandlers;
            this.videoCaptureHandlers = videoCaptureHandlers;
        }

        @Contract(pure = true)
        @NonNull
        String getCameraId() {
            return cameraId;
        }

        @Contract(pure = true)
        @Nullable
        PreviewHandler getPreview() {
            return previewHandler;
        }

        @Contract(pure = true)
        @NonNull
        List<StillCaptureHandler> getStillCaptures() {
            return stillCaptureHandlers;
        }

        @Contract(pure = true)
        @NonNull
        List<VideoCaptureHandler> getVideoCaptures() {
            return videoCaptureHandlers;
        }
    }

    //TODO: implement a acquirePermission(callback) convenience method similar to Dexter
    //https://github.com/Karumi/Dexter/tree/master/dexter/src/main/java/com/karumi/dexter

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
            openCamera(cameraId);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            if (mSession != null && mSession.getPreview() != null) {
                PrivateUtils.configureTransform(mSession.getPreview(),
                        mContext,
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
}
