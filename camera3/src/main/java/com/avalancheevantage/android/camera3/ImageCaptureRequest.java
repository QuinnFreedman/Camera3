package com.avalancheevantage.android.camera3;

import android.hardware.camera2.CaptureRequest;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 * Package-private class. Represents the intention to capture a single still image.
 *
 * @author Quinn Freedman
 */

class ImageCaptureRequest {
    @NonNull
    private final StillCaptureHandler session;
    @Nullable
    private final CaptureRequestConfiguration precapture;
    @NonNull
    private final CaptureRequestConfiguration capture;
    @NonNull
    private ErrorHandler errorHandler;

    ImageCaptureRequest(@NonNull StillCaptureHandler session,
                        @Nullable CaptureRequestConfiguration precapture,
                        @NonNull CaptureRequestConfiguration capture,
                        @NonNull ErrorHandler errorHandler) {

        this.session = session;
        this.precapture = precapture;
        this.capture = capture;
        this.errorHandler = errorHandler;
    }

    @NonNull
    StillCaptureHandler getSession() {
        return session;
    }

    void configurePrecapture(CaptureRequest.Builder request) {
        if (precapture == null) {
            errorHandler.warning("Trying to configure precapture with null config");
            return;
        }
        precapture.configure(request);
    }

    boolean hasPrecapture() {
        return precapture != null;
    }

    void configureCapture(CaptureRequest.Builder request) {
        capture.configure(request);
    }
}