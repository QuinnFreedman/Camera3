package com.avalancheevantage.camera3;

import android.hardware.camera2.CaptureRequest;

/**
 * An interface representing a function which configures a {@link CaptureRequest.Builder}.
 * When a capture request is made internally in Camera3, it is first created with some defaults,
 * including the targets. Then, an instance of ImageCaptureRequestConfiguration is called so that
 * the user has a chance to specify additional parameters.
 * <p>
 * For precapture, it is recommended to use {@link Camera3#PRECAPTURE_CONFIG_TRIGGER_AUTO_FOCUS}
 * as a default.
 * <p>
 * For capture, {@link Camera3#CAPTURE_CONFIG_DEFAULT} provides no additional configuration
 *
 * @author Quinn Freedman
 */
public interface ImageCaptureRequestConfiguration {
    /**
     * Provide an implementation that configures a {@link CaptureRequest.Builder} using
     * {@link CaptureRequest.Builder#set(CaptureRequest.Key, Object)}.
     * <p>
     * For example, add something like:
     * <code>
     *     request.set(CaptureRequest.CONTROL_EFFECT_MODE,
     *                 CaptureRequest.CONTROL_EFFECT_MODE_NEGATIVE);
     * </code>
     * @param request the request builder to be configured
     */
    void configure(CaptureRequest.Builder request);
}