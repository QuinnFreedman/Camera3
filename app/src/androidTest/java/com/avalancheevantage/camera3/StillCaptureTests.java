package com.avalancheevantage.camera3;

import android.content.Context;
import android.graphics.ImageFormat;
import android.media.Image;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;
import android.util.Size;

import net.jodah.concurrentunit.Waiter;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

import static com.avalancheevantage.camera3.TestUtils.testErrorHandler;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Created by Quinn Freedman on 11/10/2017.
 */

@RunWith(AndroidJUnit4.class)
public class StillCaptureTests {
    @Test
    public void startStillCaptureSession() throws Exception {
        final Context appContext = InstrumentationRegistry.getTargetContext();

        Camera3 camera3 = new Camera3(appContext, testErrorHandler);
        String cameraId = camera3.getAvailableCameras().get(0);

        Size size = camera3.getLargestAvailableSize(cameraId, ImageFormat.JPEG);

        StillCaptureHandler cs = new StillCaptureHandler(
                ImageFormat.JPEG, size, new OnImageAvailableListener() {
            @Override
            public void onImageAvailable(Image image) {

            }
        }, camera3.getErrorHandler());

        camera3.startCaptureSession(cameraId, null, Arrays.asList(cs));

    }

    @Test
    public void getCaptureResult() throws Exception {
        final Context appContext = InstrumentationRegistry.getTargetContext();
        final Waiter waiter = new Waiter();

        final Camera3 camera = new Camera3(appContext, testErrorHandler);
        final String cameraId = camera.getAvailableCameras().get(0);

        final Size size = camera.getLargestAvailableSize(cameraId, ImageFormat.JPEG);

        final StillCaptureHandler cs = new StillCaptureHandler(
                ImageFormat.JPEG, size, new OnImageAvailableListener() {
                    @Override
                    public void onImageAvailable(Image image) {
                        waiter.resume();
                    }
                }, camera.getErrorHandler());

        camera.startCaptureSession(cameraId, null, Arrays.asList(cs),
                new Runnable() {
                    @Override
                    public void run() {
                        camera.captureImage(cs, Camera3.PRECAPTURE_CONFIG_NONE,
                                Camera3.CAPTURE_CONFIG_DEFAULT);
                    }
                });


        waiter.await(5, SECONDS);
    }

    @Test
    public void pauseResume() throws Exception {
        final Context appContext = InstrumentationRegistry.getTargetContext();
        final Waiter waiter = new Waiter();

        final Camera3 camera = new Camera3(appContext,
                new TestUtils.ExpectWarning(
                        "Starting session from background thread",
                        waiter));
        final String cameraId = camera.getAvailableCameras().get(0);

        final Size size = camera.getLargestAvailableSize(cameraId, ImageFormat.JPEG);

        final StillCaptureHandler cs = new StillCaptureHandler(
                ImageFormat.JPEG, size, new OnImageAvailableListener() {
                    @Override
                    public void onImageAvailable(Image image) {
                        waiter.resume();
                    }
                }, camera.getErrorHandler());

        camera.startCaptureSession(cameraId, null, Arrays.asList(cs),
                new Runnable() {
                    @Override
                    public void run() {
                        Log.d("TEST", "started first session");
                        camera.pause();
                        Log.d("TEST", "camera paused");

                        camera.resume(new Runnable() {
                            @Override
                            public void run() {
                                Log.d("TEST", "camera resumed");
                                camera.captureImage(cs, Camera3.PRECAPTURE_CONFIG_NONE,
                                        Camera3.CAPTURE_CONFIG_DEFAULT);
                            }
                        });
                    }
                });


        waiter.await();
    }

    //TODO fail gracefully when requesting capture before camera has opened
}