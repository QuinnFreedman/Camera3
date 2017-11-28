package com.avalancheevantage.android.camera3;

import android.Manifest;
import android.content.Context;
import android.graphics.ImageFormat;
import android.media.Image;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.GrantPermissionRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;
import android.util.Size;

import net.jodah.concurrentunit.Waiter;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeoutException;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Created by Quinn Freedman on 11/10/2017.
 */

@RunWith(AndroidJUnit4.class)
public class StillCaptureTests {

    @Rule
    public GrantPermissionRule permissionRule =
            GrantPermissionRule.grant(Manifest.permission.CAMERA);

    @Test
    public void startStillCaptureSession() throws Exception {
        final Context appContext = InstrumentationRegistry.getTargetContext();

        Camera3 camera3 = new Camera3(appContext, TestUtils.testErrorHandler);
        String cameraId = camera3.getAvailableCameras().get(0);

        Size size = camera3.getLargestAvailableSize(cameraId, ImageFormat.JPEG);

        StillCaptureHandler cs = new StillCaptureHandler(
                ImageFormat.JPEG, size, new OnImageAvailableListener() {
            @Override
            public void onImageAvailable(Image image) {

            }
        });

        camera3.startCaptureSession(cameraId, null, Collections.singletonList(cs));

    }

    @Test
    public void getCaptureResult() throws Exception {
        final Context appContext = InstrumentationRegistry.getTargetContext();
        final Waiter waiter = new Waiter();

        final Camera3 camera = new Camera3(appContext, TestUtils.testErrorHandler);
        final String cameraId = camera.getAvailableCameras().get(0);

        final Size size = camera.getLargestAvailableSize(cameraId, ImageFormat.JPEG);

        final StillCaptureHandler cs = new StillCaptureHandler(
                ImageFormat.JPEG, size, new OnImageAvailableListener() {
            @Override
            public void onImageAvailable(Image image) {
                waiter.resume();
            }
        });

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

    /**
     * This test crashes sometimes due to threading issues when multiple tests run at once
     * However, it will always pass when run on its own
     */
    @Test
    public void pauseResume() throws Exception {
        final Context appContext = InstrumentationRegistry.getTargetContext();
        final Waiter waiter = new Waiter();

        final TestUtils.ExpectWarning errorHandler = new TestUtils.ExpectWarning(
                "Starting session from background thread",
                waiter);

        final Camera3 camera = new Camera3(appContext, errorHandler);
        final String cameraId = camera.getAvailableCameras().get(0);

        final Size size = camera.getLargestAvailableSize(cameraId, ImageFormat.JPEG);

        final StillCaptureHandler cs = new StillCaptureHandler(
                ImageFormat.JPEG, size, new OnImageAvailableListener() {
            @Override
            public void onImageAvailable(Image image) {
                waiter.resume();
            }
        });

        camera.startCaptureSession(cameraId, null, Collections.singletonList(cs),
                new Runnable() {
                    @Override
                    public void run() {
                        camera.pause();
                        camera.resume(new Runnable() {
                            @Override
                            public void run() {
                                camera.captureImage(cs, Camera3.PRECAPTURE_CONFIG_NONE,
                                        Camera3.CAPTURE_CONFIG_DEFAULT);
                            }
                        });
                    }
                });


        waiter.await(5, SECONDS);
        Assert.assertTrue(errorHandler.gotWarning());
    }

    @Test
    public void pauseRestart() throws Exception {
        final Context appContext = InstrumentationRegistry.getTargetContext();
        final Waiter waiter = new Waiter();

        final TestUtils.ExpectWarning errorHandler = new TestUtils.ExpectWarning(
                "Starting session from background thread",
                waiter);

        final Camera3 camera = new Camera3(appContext, errorHandler);
        final String cameraId = camera.getAvailableCameras().get(0);

        final Size size = camera.getLargestAvailableSize(cameraId, ImageFormat.JPEG);

        final StillCaptureHandler cs = new StillCaptureHandler(
                ImageFormat.JPEG, size, new OnImageAvailableListener() {
            @Override
            public void onImageAvailable(Image image) {
                waiter.assertNotNull(image);
                waiter.resume();
            }
        });

        camera.startCaptureSession(cameraId, null, Arrays.asList(cs),
                new Runnable() {
                    @Override
                    public void run() {
                        camera.pause();
                        camera.startCaptureSession(cameraId, null,
                                Collections.singletonList(cs),
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        camera.captureImage(cs, Camera3.PRECAPTURE_CONFIG_NONE,
                                                Camera3.CAPTURE_CONFIG_DEFAULT);
                                    }
                                });
                    }
                });


        waiter.await(8, SECONDS);
        Assert.assertTrue(errorHandler.gotWarning());
    }

    @Test
    public void requestCaptureBeforeCameraOpens() throws Exception {
        final Context appContext = InstrumentationRegistry.getTargetContext();
        final Waiter waiter = new Waiter();

        final Camera3 camera = new Camera3(appContext, TestUtils.testErrorHandler);
        final String cameraId = camera.getAvailableCameras().get(0);

        final Size size = camera.getLargestAvailableSize(cameraId, ImageFormat.JPEG);

        final StillCaptureHandler cs = new StillCaptureHandler(
                ImageFormat.JPEG, size, new OnImageAvailableListener() {
            @Override
            public void onImageAvailable(Image image) {
                waiter.resume();
            }
        });

        camera.startCaptureSession(cameraId, null, Collections.singletonList(cs));

        camera.captureImage(cs, Camera3.PRECAPTURE_CONFIG_NONE,
                Camera3.CAPTURE_CONFIG_DEFAULT);

        waiter.await(5, SECONDS);
    }

    @Test
    public void captureMultipleImages() throws Exception {
        final Context appContext = InstrumentationRegistry.getTargetContext();
        final Waiter[] waiterWrapper = new Waiter[1];
        final Waiter master = new Waiter();
        final int[] imagesCaptured = new int[]{0};
        final int NUM_IMAGES = 5;

        final Camera3 camera = new Camera3(appContext, TestUtils.testErrorHandler);
        final String cameraId = camera.getAvailableCameras().get(0);

        final Size size = camera.getLargestAvailableSize(cameraId, ImageFormat.JPEG);

        final StillCaptureHandler cs = new StillCaptureHandler(
                ImageFormat.JPEG, size, new OnImageAvailableListener() {
            @Override
            public void onImageAvailable(Image image) {
                master.assertNotNull(image);
                synchronized (imagesCaptured) {
                    imagesCaptured[0]++;
                }
                waiterWrapper[0].resume();
            }
        });

        camera.startCaptureSession(cameraId, null, Collections.singletonList(cs),
                new Runnable() {
                    @Override
                    public void run() {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                for (int i = 0; i < NUM_IMAGES; i++) {
                                    waiterWrapper[0] = new Waiter();
                                    camera.captureImage(cs, Camera3.PRECAPTURE_CONFIG_NONE,
                                            Camera3.CAPTURE_CONFIG_DEFAULT);
                                    try {
                                        waiterWrapper[0].await(5, SECONDS);
                                    } catch (TimeoutException e) {
                                        master.fail(e);
                                    }
                                }
                                master.resume();
                            }
                        }).start();

                    }
                });

        master.await(10, SECONDS);
        Assert.assertEquals(NUM_IMAGES, imagesCaptured[0]);
    }

    @Test
    public void captureMultipleImagesWithoutWaiting() throws Exception {
        final Context appContext = InstrumentationRegistry.getTargetContext();
        final Waiter waiter = new Waiter();
        final int[] imagesCaptured = new int[]{0};
        final int NUM_IMAGES = 6;

        final Camera3 camera = new Camera3(appContext, TestUtils.testErrorHandler);
        final String cameraId = camera.getAvailableCameras().get(0);

        final Size size = camera.getLargestAvailableSize(cameraId, ImageFormat.JPEG);

        final StillCaptureHandler cs = new StillCaptureHandler(
                ImageFormat.JPEG, size, new OnImageAvailableListener() {
                    @Override
                    public void onImageAvailable(Image image) {
                        waiter.assertNotNull(image);
                        synchronized (imagesCaptured) {
                            imagesCaptured[0]++;
                            Log.d("TEST","imagesCaptured == "+imagesCaptured[0]);
                            if (imagesCaptured[0] == NUM_IMAGES) {
                                waiter.resume();
                            }
                        }
                    }
                });

        camera.startCaptureSession(cameraId, null, Collections.singletonList(cs));
        for (int i = 0; i < NUM_IMAGES; i++) {
            camera.captureImage(cs, Camera3.PRECAPTURE_CONFIG_NONE,
                    Camera3.CAPTURE_CONFIG_DEFAULT);
        }

        waiter.await();
        Assert.assertEquals(NUM_IMAGES, imagesCaptured[0]);
    }
}