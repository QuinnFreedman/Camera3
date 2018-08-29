package com.avalancheevantage.android.camera3;

import android.media.Image;

/**
 * A listener for capturing still images
 * @author Quinn Freedman
 */
public interface OnImageAvailableListener {
    enum ImageAction {
        /**
         * Indicates that the image should be closed after the listener returns.
         */
        CLOSE_IMAGE,
        /**
         * Indicates that the image should NOT be closed after the listener returns.
         * The user must {@link Image#close()} the image manually to prevent a memory leak.
         */
        KEEP_IMAGE_OPEN
    }
    /**
     * This method will be called when an image becomes available. Override it to implement
     * behavior on image capture
     * <p>
     * If you return {@code false} form this function, the image will automatically be
     * {@link Image#close()}'d immediately after this function returns. If you want to maintain
     * a reference to the image past the lifetime of this function call, you should return
     * {@link ImageAction#KEEP_IMAGE_OPEN}.
     * </p><p>
     * Node: this method will only be called from a background handler thread,
     * so unless you want to do something very intensive with the image, you can just do
     * whatever you need to do inline.
     * </p>
     * @param image the captured image
     * @return {@link ImageAction#KEEP_IMAGE_OPEN} if the image will persist after the function
     *         returns; {@link ImageAction#KEEP_IMAGE_OPEN} otherwise
     */
    ImageAction onImageAvailable(Image image);
}
