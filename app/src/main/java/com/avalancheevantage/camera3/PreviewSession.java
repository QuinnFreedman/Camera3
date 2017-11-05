package com.avalancheevantage.camera3;

import android.hardware.camera2.CaptureRequest;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Size;
import android.view.TextureView;

import java.util.List;

/**
 * This class represents the configuration for a preview session. It is responsible for holding a
 * reference to the preview {@link TextureView} and for allowing the user to optionally specify
 * a custom preview request and callback. It should be created once and passed to
 * {@link Camera3#startCaptureSession(String, PreviewSession, List)} when the capture
 * session is started.
 * <p>
 * This class should be instantiated via {@link Camera3#createPreviewSession(TextureView, CaptureRequest.Builder, Camera3.PreviewSizeCallback)}
 *
 * @author Quinn Freedman
 */

public class PreviewSession {
    @NonNull
    private final TextureView previewTextureView;
    @Nullable
    private final CaptureRequest.Builder previewRequest;
    @Nullable
    private final Camera3.PreviewSizeCallback previewSizeSelected;
    private final boolean usesCustomRequest;

    //actual size of the capture request (from list of available sizes)
    private Size previewSize;
    private Camera3 parent;

    @NonNull
    TextureView getTextureView() {
        return previewTextureView;
    }

    @Nullable
    CaptureRequest.Builder getPreviewRequest() {
        return previewRequest;
    }

    @Nullable
    Camera3.PreviewSizeCallback getPreviewSizeSelectedCallback() {
        return previewSizeSelected;
    }

    void setPreviewSize(Size previewSize) {
        this.previewSize = previewSize;
    }

    Size getPreviewSize() {
        return previewSize;
    }

    PreviewSession(@NonNull TextureView previewTextureView,
                   @Nullable CaptureRequest.Builder previewRequest,
                   @Nullable Camera3.PreviewSizeCallback previewSizeSelected,
                   @NonNull Camera3 parent) {
        this.parent = parent;
        //noinspection ConstantConditions
        if (previewTextureView == null) {
            throw new IllegalArgumentException("previewTextureView cannot be null");
        }
        this.previewTextureView = previewTextureView;
        this.previewRequest = previewRequest;
        this.previewSizeSelected = previewSizeSelected;
        this.usesCustomRequest = previewRequest == null;
    }

    boolean usesCustomRequest() {
        return usesCustomRequest;
    }

    public Camera3 getParent() {
        return parent;
    }
}
