package com.avalancheevantage.android.camera3;

import android.media.MediaRecorder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;

import java.io.IOException;
import java.util.Objects;

import static com.avalancheevantage.android.camera3.PrivateUtils.requireNotNull;

/**
 * Created by Quinn Freedman on 12/21/2017.
 */

public class VideoCaptureHandler {
    public static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    public static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;
    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();
    static {
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180);

        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }

    @NonNull
    private final Size videoSize;

    @Nullable
    private MediaRecorder mediaRecorder;
    private ErrorHandler errorHandler;
    private boolean recording = false;

    @NonNull
    public Size getVideoSize() {
        return videoSize;
    }

    //TODO: allow the user to not specify a size; use Camera3.getDefaultVideoSize
    public VideoCaptureHandler(@NonNull Size videoSize) {
        this.videoSize = videoSize;
    }

    void setErrorHandler(@NonNull ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    void close() {
        if (this.mediaRecorder != null) {
            this.mediaRecorder.release();
            this.mediaRecorder = null;
        }
    }

    void setUpMediaRecorder(String videoFilePath, int sensorOrientation, int rotation) {
        //TODO: why are all these methods failing?? check example
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setOutputFile(videoFilePath);
        mediaRecorder.setVideoEncodingBitRate(10000000);
        mediaRecorder.setVideoFrameRate(30);
        mediaRecorder.setVideoSize(videoSize.getWidth(), videoSize.getHeight());
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        switch (sensorOrientation) {
            case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                mediaRecorder.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation));
                break;
            case SENSOR_ORIENTATION_INVERSE_DEGREES:
                mediaRecorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation));
                break;
        }
        try {
            mediaRecorder.prepare();
        } catch (IOException e) {
            assert this.errorHandler != null;
            this.errorHandler.error("Unable to start MediaRecorder", e);
        }
    }

    Surface getRecorderSurface() {
        Objects.requireNonNull(mediaRecorder, "MediaRecorder is null");

        Log.d("VCH", "mediaRecorder.getSurface().isValid(): " + mediaRecorder.getSurface().isValid());

        return mediaRecorder.getSurface();
    }

    void start() throws IllegalStateException {
        if (recording) {
            throw new IllegalStateException(
                    "Trying to start video capture but video capture is already in progress");
        }

        if (requireNotNull(mediaRecorder, "Internal error: MediaRecorder is null", errorHandler)) return;

        mediaRecorder.start();
    }

    void stop() throws IllegalStateException {
        if (!recording) {
            throw new IllegalStateException(
                    "Trying to stop video capture but no video is being recorded");
        }

        if (requireNotNull(mediaRecorder, "Internal error: MediaRecorder is null", errorHandler)) return;

        mediaRecorder.stop();
        mediaRecorder.reset();
        recording = false;
    }


    boolean isRecording() {
        return recording;
    }
}
