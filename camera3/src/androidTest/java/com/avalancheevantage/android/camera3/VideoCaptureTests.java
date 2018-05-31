package com.avalancheevantage.android.camera3;

import android.Manifest;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.media.Image;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.GrantPermissionRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;
import android.util.Size;
import android.view.TextureView;

import net.jodah.concurrentunit.Waiter;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeoutException;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by Quinn Freedman on 11/10/2017.
 */

@RunWith(AndroidJUnit4.class)
public class VideoCaptureTests {

    @Rule
    public GrantPermissionRule permissionRule =
            GrantPermissionRule.grant(Manifest.permission.CAMERA,
                                      Manifest.permission.RECORD_AUDIO);

    @Before
    public void before() throws Exception {
        Thread.sleep(1000);
    }

    @Test
    public void startVideoCaptureSessionWithoutPreview() throws Exception {
        final Context appContext = InstrumentationRegistry.getTargetContext();

        Camera3 camera3 = new Camera3(appContext, TestUtils.testErrorHandler);
        String cameraId = camera3.getAvailableCameras().get(0);


        Size size = camera3.getLargestAvailableImageSize(cameraId, ImageFormat.JPEG);

        //TODO how to get size for video?
        VideoCaptureHandler vidHandler = new VideoCaptureHandler(size);

        camera3.startCaptureSession(cameraId, null, null,
                singletonList(vidHandler));

        Thread.sleep(1000);
        final Waiter waiter = new Waiter();
        camera3.startVideoCapture(vidHandler, null, new VideoCaptureStartedCallback() {
            @Override
            public void captureStarted(@NonNull VideoCaptureHandler handler, @NonNull File outputFile) {
                waiter.assertNotNull(handler);
                waiter.assertNotNull(outputFile);
                waiter.resume();
            }
        });
        Log.d("TEST", "startVideoCaptureSessionWithoutCrashing: Done");
        waiter.await(5, SECONDS);

    }

    @Test
    public void stopVideoCaptureSessionWithoutPreview() throws Exception {
        final Context appContext = InstrumentationRegistry.getTargetContext();

        final Camera3 camera3 = new Camera3(appContext, TestUtils.testErrorHandler);
        String cameraId = camera3.getAvailableCameras().get(0);


        Size size = camera3.getLargestAvailableImageSize(cameraId, ImageFormat.JPEG);

        //TODO how to get size for video?
        final VideoCaptureHandler vidHandler = new VideoCaptureHandler(size);

        camera3.startCaptureSession(cameraId, null, null,
                singletonList(vidHandler));

        final Waiter waiter = new Waiter();
        camera3.startVideoCapture(vidHandler, null, new VideoCaptureStartedCallback() {
            @Override
            public void captureStarted(@NonNull VideoCaptureHandler handler, @NonNull File outputFile) {
                camera3.stopVideoCapture(vidHandler);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    waiter.rethrow(e);
                }
                waiter.resume();
            }
        });

    }
}

