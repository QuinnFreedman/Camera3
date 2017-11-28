package com.avalancheevantage.android.camera3;

import android.hardware.camera2.CaptureResult;

/**
 * A listener for capture results
 *
 * @see Camera3#setCaptureResultListener(CaptureResultListener)
 * @author Quinn Freedman
 */
public interface CaptureResultListener {
    /**
     * @see Camera3#setCaptureResultListener(CaptureResultListener)
     * @param state the state of the camera when this result was returned
     * @param result the <i>Camera2</i> result object
     */
    void onResult(Camera3.CameraState state, CaptureResult result);
}
