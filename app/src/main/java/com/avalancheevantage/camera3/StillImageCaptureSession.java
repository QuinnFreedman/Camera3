package com.avalancheevantage.camera3;

import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Size;

import java.util.List;

/**
 * This class represents the configuration for a preview session. It is responsible for processing
 * the image data from the camera and calling the user's callback whenever an image is available.
 * <p>
 * This class should be instantiated via {@link Camera3#createStillImageCaptureSession(int, Size, OnImageAvailableListener)}
 * <p>
 * It should be created once and passed to
 * {@link Camera3#startCaptureSession(String, PreviewSession, List)} when the capture
 * session is started. You only need to create multiple sessions if you want to capture images in
 * multiple formats/sizes. Many capture requests can be made from one session.
 *
 * @author Quinn Freedman
 */

public class StillImageCaptureSession {
    private static final int MAX_IMAGES = 2;

    private final int imageFormat;
    @NonNull
    private final ImageReader.OnImageAvailableListener imageAvailableListener;
    @Nullable
    private Size imageSize;
    @Nullable
    private ImageReader imageReader;

    public int getImageFormat() {
        return imageFormat;
    }

    @Nullable
    public Size getImageSize() {
        return imageSize;
    }
    /**
     * @param imageSize The size of the image to capture. This size should come from
     *                  {@link Camera3#getAvailableSizes(String, int)} or
     *                  {@link Camera3#getLargestAvailableSize(String, int)}
     *                  //TODO test when this is not from the list
     * @param imageFormat The format to capture in (from {@link android.graphics.ImageFormat}).
     *                    E.g. ImageFormat.JPEG
     * @param onImageAvailableListener a callback to receive the images from this session once they
     *                                 have been captured
     * @param errorHandler an error handler to be notified if something goes wrong when reading
     *                     the image.
     */
    public StillImageCaptureSession(final int imageFormat,
                             @NonNull final Size imageSize,
                             @NonNull final OnImageAvailableListener onImageAvailableListener,
                             @NonNull final ErrorHandler errorHandler) {
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

        this.imageAvailableListener = new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                try {
                    Image image = reader.acquireNextImage();
                    onImageAvailableListener.onImageAvailable(image);
                    image.close();
                } catch (IllegalStateException e) {
                    errorHandler.error(
                            "The image queue for this capture session is full. " +
                                    "More images must be processed before any new ones can " +
                                    "be captured.", e);
                }
            }
        };

    }

    @Nullable
    ImageReader getImageReader() {
        return imageReader;
    }

    void closeImageReader() {
        if (this.imageReader != null) {
            this.imageReader.close();
            this.imageReader = null;
        }
    }

    void openImageReader(@NonNull final Handler backgroundHandler) {
        this.imageReader = ImageReader.newInstance(imageSize.getWidth(), imageSize.getHeight(),
                imageFormat, MAX_IMAGES);
        this.imageReader.setOnImageAvailableListener(
                imageAvailableListener, backgroundHandler);
    }
}