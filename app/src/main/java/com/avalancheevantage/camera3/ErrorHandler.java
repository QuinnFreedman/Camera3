package com.avalancheevantage.camera3;

import android.support.annotation.Nullable;

/**
 * An interface to define a way to handle camera errors. Because most of the Camera3 code is
 * asynchronous, exceptions can't always be thrown to the user.
 * <p>
 * For debugging, you can use {@link Camera3#ERROR_HANDLER_DEFAULT} which just logs everything.
 *
 * @author Quinn Freedman
 */
public interface ErrorHandler {

    /**
     * Called when a fatal error occurred that definitely prevented the camera form working in a way
     * that is visible to the user. The end user should be notified gracefully and the camera
     * session might need to be restarted.
     *
     * @param message the error message
     * @param e       the Exception that was thrown (if any)
     */
    void error(String message, @Nullable Exception e);

    /**
     * Called when we suspect that the user might have made a mistake or when minor error occurred
     * that might alter the behavior of the camera.
     *
     * @param message the error message
     */
    void warning(String message);

    /**
     * Called throughout the Camera3 code for debugging. This should probably be ignored.
     *
     * @param message the info message
     */
    void info(String message);
}