package com.avalancheevantage.android.camera3;

import android.Manifest;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.media.Image;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.GrantPermissionRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;
import android.util.Size;

import net.jodah.concurrentunit.Waiter;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.TimeoutException;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by Quinn Freedman on 11/10/2017.
 */

@RunWith(AndroidJUnit4.class)
public class StillCaptureTests {

    @Rule
    public GrantPermissionRule permissionRule =
            GrantPermissionRule.grant(Manifest.permission.CAMERA);

    // Sometimes if these tests run too close to each other they will crash.
    // I should figure out why but for now this fixes it
    @Before
    public void before() throws Exception {
        Thread.sleep(1000);
    }

    @Test
    public void startStillCaptureSession() throws Exception {
        final Context appContext = InstrumentationRegistry.getTargetContext();

        Camera3 camera3 = new Camera3(appContext, TestUtils.testErrorHandler);
        String cameraId = camera3.getAvailableCameras().get(0);

        Size size = camera3.getLargestAvailableImageSize(cameraId, ImageFormat.JPEG);

        StillCaptureHandler cs = new StillCaptureHandler(
                ImageFormat.JPEG, size, new OnImageAvailableListener() {
            @Override
            public ImageAction onImageAvailable(Image image) {
                return ImageAction.CLOSE_IMAGE;
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

        final Size size = camera.getLargestAvailableImageSize(cameraId, ImageFormat.JPEG);

        final StillCaptureHandler cs = new StillCaptureHandler(
                ImageFormat.JPEG, size, new OnImageAvailableListener() {
            @Override
            public ImageAction onImageAvailable(Image image) {
                waiter.resume();
                return ImageAction.CLOSE_IMAGE;
            }
        });

        camera.startCaptureSession(cameraId, null, Arrays.asList(cs),
                null, new Runnable() {
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

        final Size size = camera.getLargestAvailableImageSize(cameraId, ImageFormat.JPEG);

        final StillCaptureHandler cs = new StillCaptureHandler(
                ImageFormat.JPEG, size, new OnImageAvailableListener() {
            @Override
            public ImageAction onImageAvailable(Image image) {
                waiter.resume();
                return ImageAction.CLOSE_IMAGE;
            }
        });

        camera.startCaptureSession(cameraId, null, Collections.singletonList(cs),
                null, new Runnable() {
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
        assertTrue(errorHandler.gotWarning());
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

        final Size size = camera.getLargestAvailableImageSize(cameraId, ImageFormat.JPEG);

        final StillCaptureHandler cs = new StillCaptureHandler(
                ImageFormat.JPEG, size, new OnImageAvailableListener() {
            @Override
            public ImageAction onImageAvailable(Image image) {
                waiter.assertNotNull(image);
                waiter.resume();
                return ImageAction.CLOSE_IMAGE;
            }
        });

        camera.startCaptureSession(cameraId, null, Arrays.asList(cs),
                null, new Runnable() {
                    @Override
                    public void run() {
                        camera.pause();
                        camera.startCaptureSession(cameraId, null,
                                Collections.singletonList(cs),
                                null, new Runnable() {
                                    @Override
                                    public void run() {
                                        camera.captureImage(cs, Camera3.PRECAPTURE_CONFIG_NONE,
                                                Camera3.CAPTURE_CONFIG_DEFAULT);
                                    }
                                });
                    }
                });


        waiter.await(8, SECONDS);
        assertTrue(errorHandler.gotWarning());
    }

    @Test
    public void requestCaptureBeforeCameraOpens() throws Exception {
        final Context appContext = InstrumentationRegistry.getTargetContext();
        final Waiter waiter = new Waiter();

        final Camera3 camera = new Camera3(appContext, TestUtils.testErrorHandler);
        final String cameraId = camera.getAvailableCameras().get(0);

        final Size size = camera.getLargestAvailableImageSize(cameraId, ImageFormat.JPEG);

        final StillCaptureHandler cs = new StillCaptureHandler(
                ImageFormat.JPEG, size, new OnImageAvailableListener() {
            @Override
            public ImageAction onImageAvailable(Image image) {
                waiter.resume();
                return ImageAction.CLOSE_IMAGE;
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

        final Size size = camera.getLargestAvailableImageSize(cameraId, ImageFormat.JPEG);

        final StillCaptureHandler cs = new StillCaptureHandler(
                ImageFormat.JPEG, size, new OnImageAvailableListener() {
            @Override
            public ImageAction onImageAvailable(Image image) {
                master.assertNotNull(image);
                synchronized (imagesCaptured) {
                    imagesCaptured[0]++;
                }
                waiterWrapper[0].resume();
                return ImageAction.CLOSE_IMAGE;
            }
        });

        camera.startCaptureSession(cameraId, null, Collections.singletonList(cs),
                null, new Runnable() {
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
        assertEquals(NUM_IMAGES, imagesCaptured[0]);
    }

    @Test
    public void captureMultipleImagesWithoutWaiting() throws Exception {
        final Context appContext = InstrumentationRegistry.getTargetContext();
        final Waiter waiter = new Waiter();
        final int[] imagesCaptured = new int[]{0};
        final int NUM_IMAGES = 6;

        final Camera3 camera = new Camera3(appContext, TestUtils.testErrorHandler);
        final String cameraId = camera.getAvailableCameras().get(0);

        final Size size = camera.getLargestAvailableImageSize(cameraId, ImageFormat.JPEG);

        final StillCaptureHandler cs = new StillCaptureHandler(
                ImageFormat.JPEG, size, new OnImageAvailableListener() {
                    @Override
                    public ImageAction onImageAvailable(Image image) {
                        waiter.assertNotNull(image);
                        synchronized (imagesCaptured) {
                            imagesCaptured[0]++;
                            Log.d("TEST","imagesCaptured == "+imagesCaptured[0]);
                            if (imagesCaptured[0] == NUM_IMAGES) {
                                waiter.resume();
                            }
                        }
                        return ImageAction.CLOSE_IMAGE;
                    }
                });

        camera.startCaptureSession(cameraId, null, Collections.singletonList(cs));
        for (int i = 0; i < NUM_IMAGES; i++) {
            camera.captureImage(cs, Camera3.PRECAPTURE_CONFIG_NONE,
                    Camera3.CAPTURE_CONFIG_DEFAULT);
        }

        waiter.await();
        assertEquals(NUM_IMAGES, imagesCaptured[0]);
    }

    @Test
    public void captureResultListener() throws Exception {
        final Context appContext = InstrumentationRegistry.getTargetContext();
        final Waiter waiter = new Waiter();

        final HashMap<Camera3.CameraState, Boolean> stateHasBeenReported = new HashMap<>();
        for (Camera3.CameraState state : Camera3.CameraState.values()) {
            stateHasBeenReported.put(state, false);
        }

        final Camera3 camera = new Camera3(appContext, TestUtils.testErrorHandler);
        final String cameraId = camera.getAvailableCameras().get(0);

        final Size size = camera.getLargestAvailableImageSize(cameraId, ImageFormat.JPEG);

        final StillCaptureHandler handler = new StillCaptureHandler(
                ImageFormat.JPEG, size, new OnImageAvailableListener() {
            @Override
            public ImageAction onImageAvailable(Image image) {
                waiter.resume();
                return ImageAction.CLOSE_IMAGE;
            }
        });
        PreviewHandler previewHandler = new PreviewHandler(
                new SurfaceTexture(1),
                new Size(200, 200));

        camera.setCaptureResultListener(new CaptureResultListener() {
            @Override
            public void onResult(Camera3.CameraState state, CaptureResult result) {
                stateHasBeenReported.put(state, true);
            }
        });

        camera.startCaptureSession(cameraId, previewHandler, Arrays.asList(handler),
                null, new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            waiter.rethrow(e);
                        }
                        camera.captureImage(handler, Camera3.PRECAPTURE_CONFIG_TRIGGER_AUTO_EXPOSE,
                                Camera3.CAPTURE_CONFIG_DEFAULT);
                    }
                });


        waiter.await(5, SECONDS);
        assertTrue(stateHasBeenReported.get(Camera3.CameraState.PREVIEW));
        assertTrue(stateHasBeenReported.get(Camera3.CameraState.WAITING_FOCUS_LOCK));
        assertTrue(stateHasBeenReported.get(Camera3.CameraState.WAITING_PRECAPTURE));
        assertTrue(stateHasBeenReported.get(Camera3.CameraState.WAITING_NON_PRECAPTURE));
        assertTrue(stateHasBeenReported.get(Camera3.CameraState.CAPTURE_COMPLETED));

    }

    @Test
    public void captureImageWithLockedFocus() throws Exception {
        final Context appContext = InstrumentationRegistry.getTargetContext();
        final Waiter waiter1 = new Waiter();
        final Waiter waiter2 = new Waiter();


        final Camera3 camera = new Camera3(appContext, TestUtils.testErrorHandler);
        final String cameraId = camera.getAvailableCameras().get(0);

        final Size size = camera.getLargestAvailableImageSize(cameraId, ImageFormat.JPEG);

        final StillCaptureHandler handler = new StillCaptureHandler(
                ImageFormat.JPEG, size, new OnImageAvailableListener() {
            @Override
            public ImageAction onImageAvailable(Image image) {
                waiter1.resume();
                return ImageAction.CLOSE_IMAGE;
            }
        });
        PreviewHandler previewHandler = new PreviewHandler(
                new SurfaceTexture(1),
                new Size(200, 200),
                new CaptureRequestConfiguration() {
                    @Override
                    public void configure(CaptureRequest.Builder request) {
                        request.set(CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_OFF);
                        request.set(CaptureRequest.LENS_FOCUS_DISTANCE, 1.0f);
                    }
                }
        );

        camera.setCaptureResultListener(new CaptureResultListener() {
            @Override
            public void onResult(Camera3.CameraState state, CaptureResult result) {
                waiter1.assertEquals(CaptureResult.CONTROL_AF_MODE_OFF,
                        result.get(CaptureResult.CONTROL_AF_MODE));
                Float focusDistance = result.get(CaptureResult.LENS_FOCUS_DISTANCE);
                // focus distance does not exist on all types of CaptureResult
                if (focusDistance != null) {
                    waiter1.assertEquals(1.0f, focusDistance);
                }
                if (state == Camera3.CameraState.CAPTURE_COMPLETED) {
                    waiter2.resume();
                }
            }
        });

        camera.startCaptureSession(cameraId, previewHandler, Arrays.asList(handler),
                null, new Runnable() {
                    @Override
                    public void run() {
                        camera.captureImage(handler, Camera3.PRECAPTURE_CONFIG_TRIGGER_AUTO_EXPOSE,
                                new CaptureRequestConfiguration() {
                                    @Override
                                    public void configure(CaptureRequest.Builder request) {
                                        request.set(CaptureRequest.CONTROL_AF_MODE,
                                                CaptureRequest.CONTROL_AF_MODE_OFF);
                                        request.set(CaptureRequest.LENS_FOCUS_DISTANCE, 1.0f);
                                    }
                                });
                    }
                });


        waiter1.await(5, SECONDS);
        waiter2.await(5, SECONDS);
    }
}
