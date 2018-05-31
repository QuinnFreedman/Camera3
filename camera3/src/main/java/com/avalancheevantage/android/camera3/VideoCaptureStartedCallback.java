package com.avalancheevantage.android.camera3;

import android.support.annotation.NonNull;

import java.io.File;

/**
 * A callback function that is called when video capture has started
 */
public interface VideoCaptureStartedCallback {
    /**
     * Called when video capture has started
     *
     * @param handler    the handler that is handling the current video capture
     * @param outputFile the file that the video is being written to
     */
    void captureStarted(@NonNull VideoCaptureHandler handler,
                        @NonNull File outputFile);
}
