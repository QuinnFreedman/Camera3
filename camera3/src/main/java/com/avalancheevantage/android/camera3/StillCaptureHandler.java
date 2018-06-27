package com.avalancheevantage.android.camera3;

import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.Size;

import org.jetbrains.annotations.Contract;

import java.util.List;

/**
 * This class represents the configuration for a still image capture session. It is responsible for
 * processing the image data from the camera and calling the user's callback whenever an image is
 * available.
 * <p>
 * It should be created once and passed to
 * {@link Camera3#startCaptureSession(String, PreviewHandler, List)} when the capture
 * session is started. You only need to create multiple handlers if you want to capture images in
 * multiple formats/sizes. Many capture requests can be made from one session.
 *
 * @author Quinn Freedman
 */

public final class StillCaptureHandler {
    private static final int MAX_IMAGES = 2;

    private final int imageFormat;
    @NonNull
    private final OnImageAvailableListener imageAvailableListener;
    @Nullable
    private Size imageSize;
    @Nullable
    private ImageReader imageReader;
    @Nullable
    private Camera3 camera3;

    @Contract(pure = true)
    public int getImageFormat() {
        return imageFormat;
    }

    @Contract(pure = true)
    @Nullable
    public Size getImageSize() {
        return imageSize;
    }
    /**
     * @param imageFormat The format to capture in (from {@link android.graphics.ImageFormat}).
     *                    E.g. ImageFormat.JPEG
     * @param imageSize The size of the image to capture. This size should come from
     *                  {@link Camera3#getAvailableImageSizes(String, int)} or
     *                  {@link Camera3#getLargestAvailableImageSize(String, int)}
     *                  //TODO test when this is not from the list
     * @param onImageAvailableListener a callback to receive the images from this session once they
     *                                 have been captured
     */
    public StillCaptureHandler(final int imageFormat,
                               @NonNull final Size imageSize,
                               @NonNull final OnImageAvailableListener onImageAvailableListener) {
        //noinspection ConstantConditions
        if (imageSize == null) {
            throw new IllegalArgumentException("imageSize cannot be null");
        }
        //noinspection ConstantConditions
        if (onImageAvailableListener == null) {
            throw new IllegalArgumentException("onImageAvailableListener cannot be null");
        }

        this.imageFormat = imageFormat;
        this.imageSize = imageSize;
        this.imageAvailableListener = onImageAvailableListener;
    }


    @Contract(pure = true)
    @Nullable
    ImageReader getImageReader() {
        return imageReader;
    }

    void close() {
        if (this.imageReader != null) {
            this.imageReader.close();
            this.imageReader = null;
        }
        this.camera3 = null;
    }

    void initialize(@NonNull final Handler backgroundHandler,
                    @NonNull final Camera3 camera3) {
        if (this.camera3 != null && camera3 != this.camera3) {
            throw new IllegalStateException(
                    "This StillCaptureHandler is already in use by another Camera3 instance");
        }
        this.camera3 = camera3;
        this.imageReader = ImageReader.newInstance(imageSize.getWidth(), imageSize.getHeight(),
                imageFormat, MAX_IMAGES);
        this.imageReader.setOnImageAvailableListener(
                new ImageReader.OnImageAvailableListener() {
                    @Override
                    public void onImageAvailable(ImageReader reader) {
                        Log.d("StillCaptureHandler", "Image available");
                        camera3.popRequestQueue();
                        try {
                            Image image = reader.acquireNextImage();
                            boolean shouldNotCloseImage = imageAvailableListener.onImageAvailable(image);
                            if (!shouldNotCloseImage) {
                                image.close();
                            }
                        } catch (IllegalStateException e) {
                            camera3.getErrorHandler().error(
                                    "The image queue for this capture session is full. " +
                                            "More images must be processed before any new ones can " +
                                            "be captured.", e);
                        }
                    }
                }, backgroundHandler);
    }
}