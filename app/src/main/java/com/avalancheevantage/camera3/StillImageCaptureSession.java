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
    private final Camera3 parent;
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

    StillImageCaptureSession(final int imageFormat,
                             @NonNull final Size imageSize,
                             @NonNull final OnImageAvailableListener onImageAvailableListener,
                             @NonNull final Camera3 parent) {
        this.parent = parent;
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
                    parent.getErrorHandler().error(
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

    @NonNull
    public Camera3 getParent() {
        return parent;
    }
}