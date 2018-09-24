package com.avalancheevantage.android.camera3;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Size;
import android.view.Surface;
import android.view.WindowManager;

import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A package-private class to hold internally used util methods
 *
 * @author Quinn Freedman
 */

class PrivateUtils {

    @Contract("!null, _, !null -> false")
    static boolean checkNull(Object o, String message, ErrorHandler errorHandler) {
        try {
            Objects.requireNonNull(o, message);
        } catch (NullPointerException e) {
            errorHandler.error(message, null);
            return true;
        }
        return false;
    }

    static void reportCameraAccessException(@NonNull CameraAccessException e,
                                            @NonNull ErrorHandler errorHandler) {
        errorHandler.error("Camera Access Exception", e);
    }

    static int getScreenRotation(@NonNull Context context,
                                 @NonNull ErrorHandler errorHandler) {
        WindowManager windowManager =
                (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) {
            errorHandler.warning("Unable to get Window Manager from context");
            return Surface.ROTATION_0;
        }
        return windowManager.getDefaultDisplay().getRotation();
    }

    @NonNull
    private static Point getScreenSize(@NonNull Context context,
                                       @NonNull ErrorHandler errorHandler) {
        WindowManager windowManager =
                (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) {
            errorHandler.warning("Unable to get Window Manager from context");
            return new Point();
        }
        Point displaySize = new Point();
        windowManager.getDefaultDisplay().getSize(displaySize);
        return displaySize;
    }

    @Nullable
    static CameraCharacteristics getCameraCharacteristics(String cameraId,
                                                          Context context,
                                                          ErrorHandler errorHandler) {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

        if (manager == null) {
            errorHandler.error(Camera3.NULL_MANAGER_MESSAGE, null);
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
     * TextureView for a PreviewHandler
     * @param previewHandler     The PreviewHandler to configure
     * @param context            The application context
     * @param errorHandler       Error handler
     */
    @SuppressWarnings("SuspiciousNameCombination")
    static void configureTransform(@NonNull PreviewHandler previewHandler,
                                   @NonNull Context context,
                                   @NonNull ErrorHandler errorHandler) {
        if (previewHandler.getTextureView() == null) {
            errorHandler.info("preview handler is configured with a surface instead of a texture " +
                    "view so no transform will be configured");
            return;
        }
        errorHandler.info("Configuring preview transform matrix...");
        //noinspection ConstantConditions
        if (checkNull(previewHandler, "preview session is null when trying to configure preview transform", errorHandler) ||
            checkNull(context, "context is null when trying to configure preview transform", errorHandler)
        ) {
            return;
        }
        Size previewTextureSize = previewHandler.getPreferredSize();

        int viewWidth = previewTextureSize.getWidth();
        int viewHeight = previewTextureSize.getHeight();

        int previewWidth = previewHandler.getPreviewSize().getWidth();
        int previewHeight = previewHandler.getPreviewSize().getHeight();

        int rotation = getScreenRotation(context, errorHandler);
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, previewHeight, previewWidth);
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / previewHeight,
                    (float) viewWidth / previewWidth);
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (rotation == Surface.ROTATION_180) {
            matrix.postRotate(180, centerX, centerY);
        }
        previewHandler.getTextureView().setTransform(matrix);
    }

    //TODO: refactor to take a PreviewHandler, add textureSize as a field of preview session
    @SuppressWarnings("SuspiciousNameCombination")
    static void setUpPreviewOutput(@NonNull String cameraId,
                                   @NonNull Size previewTextureSize,
                                   int sensorOrientation,
                                   @NonNull PreviewHandler previewHandler,
                                   @NonNull Context context,
                                   @NonNull ErrorHandler errorHandler) {
        //noinspection ConstantConditions
        if (
            checkNull(previewHandler, "Cannot configure null preview session", errorHandler) ||
            checkNull(previewTextureSize, "previewTextureSize is null", errorHandler) ||
            checkNull(context, "context is null in setUpPreviewOutput()", errorHandler) ||
            checkNull(cameraId, "cameraId is null in setUpPreviewOutput()", errorHandler)
        ) {
            return;
        }
        errorHandler.info("Configuring camera outputs...");

        // Find out if we need to swap dimension to get the preview size relative to sensor
        // coordinate.
        int displayRotation = getScreenRotation(context, errorHandler);

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

        Point displaySize = getScreenSize(context, errorHandler);
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

        if (maxPreviewWidth > Camera3.MAX_PREVIEW_WIDTH) {
            maxPreviewWidth = Camera3.MAX_PREVIEW_WIDTH;
        }

        if (maxPreviewHeight > Camera3.MAX_PREVIEW_HEIGHT) {
            maxPreviewHeight = Camera3.MAX_PREVIEW_HEIGHT;
        }

        CameraCharacteristics characteristics = getCameraCharacteristics(cameraId, context, errorHandler);
        if (characteristics == null) {
            errorHandler.error("Camera Characteristics were null", null);
            return;
        }
        StreamConfigurationMap map = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
            //noinspection SpellCheckingInspection
            errorHandler.error(
                    "CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP was null " +
                            "for the given cameraId",
                    null);
            return;
        }
        Size[] previewSizes =  map.getOutputSizes(SurfaceTexture.class);
        Size largestPreviewSize = Collections.max(
                Arrays.asList(previewSizes),
                new CompareSizesByArea());

        // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
        // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
        // garbage capture data.
        Size optimalSize = chooseOptimalSize(previewSizes,
                rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                maxPreviewHeight, /*aspect ratio*/ largestPreviewSize, errorHandler);
        previewHandler.setPreviewSize(optimalSize);

        //notify the user what preview size we chose
        Camera3.PreviewSizeCallback sizeCallback = previewHandler.getPreviewSizeSelectedCallback();
        if (sizeCallback != null) {
            int orientation = context.getResources().getConfiguration().orientation;
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
    @NonNull
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
