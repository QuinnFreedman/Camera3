package com.avalancheevantage.camera3;

import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import org.jetbrains.annotations.Contract;

import java.util.*;

import static com.avalancheevantage.camera3.Camera3.MAX_PREVIEW_HEIGHT;
import static com.avalancheevantage.camera3.Camera3.MAX_PREVIEW_WIDTH;
import static com.avalancheevantage.camera3.Camera3.NULL_MANAGER_MESSAGE;

/**
 * A package-private class to hold internally used util methods
 *
 * @author Quinn Freedman
 */

class PrivateUtils {

    @Contract("!null, _, !null -> false")
    static boolean requireNotNull(Object o, String message, ErrorHandler errorHandler) {
        if (errorHandler == null) {
            return true;
        }
        if (o == null) {
            errorHandler.error(message, null);
            return true;
        }
        return false;
    }

    static void reportCameraAccessException(CameraAccessException e, ErrorHandler errorHandler) {
        errorHandler.error("Camera Access Exception", e);
    }

    @Nullable
    static CameraCharacteristics getCameraCharacteristics(String cameraId,
                                                          Activity activity,
                                                          ErrorHandler errorHandler) {
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);

        if (manager == null) {
            errorHandler.error(NULL_MANAGER_MESSAGE, null);
            return null;
        }
        try {
            return manager.getCameraCharacteristics(cameraId);
        } catch (CameraAccessException e) {
            reportCameraAccessException(e, errorHandler);
            return null;
        }
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation for the
     * TextureView for a PreviewSession
     *
     * @param previewSession     The PreviewSession to configure
     * @param previewTextureSize the size of the texture to transform the preview to fit.
     */
    @SuppressWarnings("SuspiciousNameCombination")
    static void configureTransform(@NonNull PreviewSession previewSession,
                                   @NonNull Size previewTextureSize,
                                   @NonNull Activity activity,
                                   @NonNull ErrorHandler errorHandler) {
        errorHandler.info("Configuring preview transform matrix...");
        //noinspection ConstantConditions
        if (requireNotNull(previewSession, "preview session is null when trying to configure preview transform", errorHandler) ||
            requireNotNull(previewSession.getTextureView(), "textureView is null when trying to configure preview transform", errorHandler) ||
            requireNotNull(activity, "activity is null when trying to configure preview transform", errorHandler)
        ) {
            return;
        }
        int viewWidth = previewTextureSize.getWidth();
        int viewHeight = previewTextureSize.getHeight();

        int previewWidth = previewSession.getPreviewSize().getWidth();
        int previewHeight = previewSession.getPreviewSize().getHeight();

        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
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

    //TODO: refactor to take a PreviewSession, add textureSize as a field of preview session
    @SuppressWarnings("SuspiciousNameCombination")
    static void setUpPreviewOutput(@NonNull String cameraId,
                                   @NonNull Size previewTextureSize,
                                   int sensorOrientation,
                                   @NonNull PreviewSession previewSession,
                                   @NonNull Activity activity,
                                   @NonNull ErrorHandler errorHandler) {
        //noinspection ConstantConditions
        if (
            requireNotNull(previewSession, "Cannot configure null preview session", errorHandler) ||
            requireNotNull(previewTextureSize, "previewTextureSize is null", errorHandler) ||
            requireNotNull(activity, "activity is null in setUpPreviewOutput()", errorHandler) ||
            requireNotNull(cameraId, "cameraId is null in setUpPreviewOutput()", errorHandler)
        ) {
            return;
        }
        errorHandler.info("Configuring camera outputs...");

        // Find out if we need to swap dimension to get the preview size relative to sensor
        // coordinate.
        int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();

        boolean swappedDimensions = false;
        switch (displayRotation) {
            case Surface.ROTATION_0:
            case Surface.ROTATION_180:
                if (sensorOrientation == 90 || sensorOrientation == 270) {
                    swappedDimensions = true;
                }
                break;
            case Surface.ROTATION_90:
            case Surface.ROTATION_270:
                if (sensorOrientation == 0 || sensorOrientation == 180) {
                    swappedDimensions = true;
                }
                break;
            default:
                errorHandler.warning("Display rotation is invalid: " + displayRotation);
        }

        Point displaySize = new Point();
        activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
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

        CameraCharacteristics characteristics = getCameraCharacteristics(cameraId, activity, errorHandler);
        if (characteristics == null) {
            errorHandler.error("Camera Characteristics were null", null);
        }
        StreamConfigurationMap map = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
            errorHandler.error(
                    "CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP was null " +
                            "for the given cameraId",
                    null);
            return;
        }
        //TODO why are we looking for something with the same aspect ratio as the largest JPEG
        //instead of the largest of getOutputSizes(SurfaceTexture.class)
        Size largestJpeg = Collections.max(
                Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                new CompareSizesByArea());

        // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
        // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
        // garbage capture data.
        //TODO allow the user to specify a preferred preview aspect ratio and size
        Size optimalSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                maxPreviewHeight, /*aspect ratio*/ largestJpeg, errorHandler);
        previewSession.setPreviewSize(optimalSize);

        //notify the user what preview size we chose
        Camera3.PreviewSizeCallback sizeCallback = previewSession.getPreviewSizeSelectedCallback();
        if (sizeCallback != null) {
            int orientation = activity.getResources().getConfiguration().orientation;
            sizeCallback.previewSizeSelected(orientation, optimalSize);
        }


        /*
        //a npe in here can signal bad support maybe??
        } catch (NullPointerException e) {
            throw new RuntimeException("Camera2 API is not supported on this device");
        }*/
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
    private static Size chooseOptimalSize(@NonNull Size[] choices,
                                          int textureViewWidth, int textureViewHeight,
                                          int maxWidth, int maxHeight,
                                          @NonNull Size aspectRatio,
                                          @NonNull ErrorHandler errorHandler) {

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
            errorHandler.warning("Couldn't find any suitable preview size");
            return choices[0];
        }
    }


    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

}
