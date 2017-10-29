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
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private static final String NULL_MANAGER_MESSAGE = "No camera manager. " +
            "`getSystemService(Context.CAMERA_SERVICE)` returned `null`";
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * A {@link CameraCaptureSession} for camera preview.
     */
    private CameraCaptureSession mCaptureSession;

    /**
     * A reference to the opened {@link CameraDevice}.
     */
    private CameraDevice mCameraDevice;

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
//    private ImageReader mImageReader;

    private Activity mActivity;
//    private Integer mSensorOrientation;
//    private Size mPreviewSize;
//    private TextureView mTextureView;
    private CaptureRequest.Builder mPreviewRequestBuilder;

//    @Nullable
//    private PreviewSizeCallback mPreviewSizeSelectedCallback;
    @Nullable
    private PreviewSession mPreviewSession;
    @NonNull
    private List<StillImageCaptureSession> mStillCaptureSessions = new ArrayList<>();
    @NonNull
    private ErrorHandler mErrorHandler;
    private CaptureRequest mPreviewRequest;
    private boolean started = false;

    public Camera3(Activity activity, @Nullable ErrorHandler errorHandler) {
        this.mActivity = activity;
        if (errorHandler != null) {
            mErrorHandler = errorHandler;
        } else {
            mErrorHandler= new ErrorHandler() {
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

    private void openCamera(String cameraId, Size previewTextureSize) /*throws CameraAccessException*/ {
        mErrorHandler.info("opening camera");
        mErrorHandler.info("Preview texture size == "+previewTextureSize);
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
            mErrorHandler.error("Camera Access Exception", e);
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
                e.printStackTrace();
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
                    "Invalid Camera Configuration: no field " +
                            "`SCALER_STREAM_CONFIGURATION_MAP` for the specified cameraId",
                    null);
            return;
        }

        // For still image captures, we use the largest available size.
        Size largest = Collections.max(
                Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), //TODO add other formats
                new CompareSizesByArea());
/*            mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
                ImageFormat.JPEG, 2); //maxImages: 2
        mImageReader.setOnImageAvailableListener(
                mOnImageAvailableListener, mBackgroundHandler);
*/
        // Find out if we need to swap dimension to get the preview size relative to sensor
        // coordinate.
        int displayRotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        //noinspection ConstantConditions
        Integer mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
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
            //TODO allow the user to specify a preferred preview resolution
            Size optimalSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                    rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                    maxPreviewHeight, largest);
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
     * @param previewSession The PreviewSession to configure
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
            mCameraDevice.createCaptureSession(Arrays.asList(surface),//TODO uncomment: , mImageReader.getSurface()),
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
                                mErrorHandler.error("Camera Access Exception", e);
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
            mErrorHandler.error("Camera Access Exception", e);
        }
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
            Log.d(TAG, "Capture Result! -- " + result);
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

    /* Utils */
    public List<String> getAvailableCameras() throws CameraAccessException {
        CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        return Collections.unmodifiableList(Arrays.asList(manager.getCameraIdList()));
    }

    public void getCameraInfo(String cameraId) throws CameraAccessException {
        CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        manager.getCameraCharacteristics(cameraId);
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
     * @param imageSize The size of the image to capture //TODO how to get this? when is it checked?
     * @param imageFormat The format to capture in (from {@link android.graphics.ImageFormat}). E.g. ImageFormat.JPEG
     */
    public StillImageCaptureSession
    createStillImageCaptureSession(Size imageSize, int imageFormat,
                                   ImageReader.OnImageAvailableListener onImageAvailableListener) {
        return new StillImageCaptureSession(
                imageSize, imageFormat,
                onImageAvailableListener,
                mBackgroundHandler, mErrorHandler);
    };

    public static class StillImageCaptureSession {
        private final ErrorHandler errorHandler;
//        private final Size imageSize;
//        private final int imageFormat;
        private ImageReader imageReader;

        private StillImageCaptureSession(Size imageSize, int imageFormat,
                                        ImageReader.OnImageAvailableListener onImageAvailableListener,
                                        Handler backgroundHandler,
                                        ErrorHandler errorHandler) {
            this.errorHandler = errorHandler;
//            this.imageSize = imageSize;
//            this.imageFormat = imageFormat;
            this.imageReader = ImageReader.newInstance(imageSize.getWidth(), imageSize.getHeight(),
                    imageFormat, 2);
            this.imageReader.setOnImageAvailableListener(
                    onImageAvailableListener, backgroundHandler);
        }

        public ImageReader getImageReader() {
            return imageReader;
        }

        private void closeImageReader() {
            if (this.imageReader != null) {
                this.imageReader.close();
                this.imageReader = null;
            }
        }
    }
}
