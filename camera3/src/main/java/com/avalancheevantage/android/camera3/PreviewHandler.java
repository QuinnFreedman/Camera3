package com.avalancheevantage.android.camera3;

import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Size;
import android.view.TextureView;

import org.jetbrains.annotations.Contract;

import java.util.List;

/**
 * This class represents the configuration for a preview session. It is responsible for holding a
 * reference to the preview {@link TextureView} and for allowing the user to optionally specify
 * a custom preview request and callback. It should be created once and passed to
 * {@link Camera3#startCaptureSession(String, PreviewHandler, List)} when the capture
 * session is started.
 *
 * @author Quinn Freedman
 */

final public class PreviewHandler {
    @Nullable
    private TextureView previewTextureView = null;
    @Nullable
    private final CaptureRequest.Builder previewRequest;
    @Nullable
    private final Camera3.PreviewSizeCallback previewSizeSelected;
    @Nullable
    private Size preferredSize = null;
    @Nullable
    private SurfaceTexture previewSurface = null;

    //actual size of the capture request (from list of available sizes)
    private Size previewSize;

    @Contract(pure = true)
    @Nullable
    TextureView getTextureView() {
        return previewTextureView;
    }

    @Contract(pure = true)
    @Nullable
    CaptureRequest.Builder getPreviewRequest() {
        return previewRequest;
    }

    @Contract(pure = true)
    @Nullable
    Camera3.PreviewSizeCallback getPreviewSizeSelectedCallback() {
        return previewSizeSelected;
    }

    void setPreviewSize(Size previewSize) {
        this.previewSize = previewSize;
    }

    @Contract(pure = true)
    Size getPreviewSize() {
        return previewSize;
    }


    //Surface constructors
    /**
     * @see PreviewHandler#PreviewHandler(SurfaceTexture, Size, CaptureRequest.Builder, Camera3.PreviewSizeCallback)
     */
    public PreviewHandler(@NonNull SurfaceTexture previewSurface,
                          @NonNull Size preferredSize) {
        this(previewSurface, preferredSize, null, null);
    }
    /**
     * @see PreviewHandler#PreviewHandler(SurfaceTexture, Size, CaptureRequest.Builder, Camera3.PreviewSizeCallback)
     */
    public PreviewHandler(@NonNull SurfaceTexture previewSurface,
                          @NonNull Size preferredSize,
                          @Nullable CaptureRequest.Builder previewRequest) {
        this(previewSurface, preferredSize, previewRequest, null);
    }

    /**
     * An alternative constructor that allows you to provide a SurfaceTexture instead of a
     * TextureView. For a normal preview session, you will probably just want to use a TextureView.
     * However, if you have a special use case, this constructor offers a bit more flexibility.
     *
     * @param previewSurface the surface on which to project the preview
     * @param preferredSize the desired preview size. (required) see {@link
     * PreviewHandler#PreviewHandler( TextureView, Size, CaptureRequest.Builder,
     * Camera3.PreviewSizeCallback)}
     * @param previewRequest see {@link PreviewHandler#PreviewHandler(
     * TextureView, Size, CaptureRequest.Builder, Camera3.PreviewSizeCallback)}
     * @param previewSizeSelected see {@link PreviewHandler#PreviewHandler(
     * TextureView, Size, CaptureRequest.Builder, Camera3.PreviewSizeCallback)}
     *
     * @see PreviewHandler#PreviewHandler(TextureView, Size, CaptureRequest.Builder, Camera3.PreviewSizeCallback)
     */
    public PreviewHandler(@NonNull SurfaceTexture previewSurface,
                          @NonNull Size preferredSize,
                          @Nullable CaptureRequest.Builder previewRequest,
                          @Nullable Camera3.PreviewSizeCallback previewSizeSelected) {
        //noinspection ConstantConditions
        if (previewSurface == null) {
            throw new IllegalArgumentException("previewSurface cannot be null");
        }
        //noinspection ConstantConditions
        if (preferredSize == null) {
            throw new IllegalArgumentException(
                    "if a surface is given, a preferred size must be provided");
        }
        this.previewSurface = previewSurface;
        this.preferredSize = preferredSize;
        this.previewRequest = previewRequest;
        this.previewSizeSelected = previewSizeSelected;
    }

    //TextureView constructors
    /**
     * @see PreviewHandler#PreviewHandler(TextureView, Size, CaptureRequest.Builder, Camera3.PreviewSizeCallback)
     */
    public PreviewHandler(@NonNull TextureView previewTextureView) {
        this(previewTextureView, null, null);
    }

    /**
     * @see PreviewHandler#PreviewHandler(TextureView, Size, CaptureRequest.Builder, Camera3.PreviewSizeCallback)
     */
    public PreviewHandler(@NonNull TextureView previewTextureView,
                          @Nullable Size preferredSize) {
        this(previewTextureView, preferredSize, null, null);
    }

    /**
     * @see PreviewHandler#PreviewHandler(TextureView, Size, CaptureRequest.Builder, Camera3.PreviewSizeCallback)
     */
    public PreviewHandler(@NonNull TextureView previewTextureView,
                          @Nullable Size preferredSize,
                          @Nullable CaptureRequest.Builder previewRequest) {
        this(previewTextureView, preferredSize, previewRequest, null);
    }

    /**
     * @param previewTextureView the texture to display the preview in
     * @param preferredSize (optional) the preferred resolution for the preview. There is no grantee
     *                      that this size will be used. Camera3 take this preference into account
     *                      and pick a size form the list of available camera sizes. See {@link
     *                      Camera3#getAvailableSizes(String, int)}. If <code>null</code>, the size
     *                      of {@code previewTextureView} will be used.
     * @param previewRequest an optional custom previewRequest. This request should be expressed in
     *                       terms of a Camera2 {@link CaptureRequest.Builder}. The Builder should
     *                       be configured with your custom parameters, but you do not need to add a
     *                       target or build it. If <code>null</code>,
     *                       {@link CameraDevice#TEMPLATE_PREVIEW} is used.
     * @param previewSizeSelected an optional callback if you want to be notified when an actual
     *                            preview size is chosen (based on {@code preferredSize} or the
     *                            size of {@code previewTextureView}). This can be useful if you
     *                            want to adjust your UI (especially aspect ratio) accordingly.
     *                            Hint: if you are using {@link AutoFitTextureView}, pass the
     *                            selected size to
     *                            {@link AutoFitTextureView#setAspectRatio(int, int)} here.
     */
    public PreviewHandler(@NonNull TextureView previewTextureView,
                          @Nullable Size preferredSize,
                          @Nullable CaptureRequest.Builder previewRequest,
                          @Nullable Camera3.PreviewSizeCallback previewSizeSelected) {
        //noinspection ConstantConditions
        if (previewTextureView == null) {
            throw new IllegalArgumentException("previewTextureView cannot be null");
        }
        this.preferredSize = preferredSize;
        this.previewTextureView = previewTextureView;
        this.previewRequest = previewRequest;
        this.previewSizeSelected = previewSizeSelected;
    }

    @Contract(pure = true)
    boolean usesCustomRequest() {
        return this.previewRequest == null;
    }

    @Contract(pure = true)
    @NonNull
    public Size getPreferredSize() {
        if (this.preferredSize != null) {
            return this.preferredSize;
        } else {
            assert previewTextureView != null;
            return new Size(previewTextureView.getWidth(), previewTextureView.getHeight());
        }
    }

    @Contract(pure = true)
    @NonNull
    public SurfaceTexture getSurfaceTexture() {
        if (previewTextureView != null) {
            return previewTextureView.getSurfaceTexture();
        } else {
            assert previewSurface != null;
            return previewSurface;
        }
    }
}
