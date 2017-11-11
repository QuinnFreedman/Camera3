package com.avalancheevantage.camera3;

import android.media.Image;

/**
 * A listener for capturing still images
 * @author Quinn Freedman
 */
public interface OnImageAvailableListener {
    /**
     * This method will be called when an image becomes available.
     * <p>
     * Note: the image will
     * automatically be {@link Image#close()}'d immediately after this function returns.
     * If you want to keep the image around (e.g. to save it asynchronously) you must
     * clone it. However, this method will only be called from a background handler thread,
     * so unless you want to do something very intensive with the image, you can just do
     * whatever you need to do inline.
     * @param image the captured image
     */
    void onImageAvailable(Image image);
}
