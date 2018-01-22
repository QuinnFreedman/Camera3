package com.avalancheevantage.android.camera3;

import android.Manifest;
import android.content.Context;
import android.graphics.ImageFormat;
import android.media.Image;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.GrantPermissionRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Size;

import net.jodah.concurrentunit.Waiter;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

import static com.avalancheevantage.android.camera3.TestUtils.testErrorHandler;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class BasicInstrumentedTest {

    @Rule
    public GrantPermissionRule permissionRule =
            GrantPermissionRule.grant(Manifest.permission.CAMERA);

    @Test
    public void instantiateWithoutCrashing() throws Exception {
        Context appContext = InstrumentationRegistry.getTargetContext();

        Camera3 camera = new Camera3(appContext, testErrorHandler);
    }

    @Test
    public void asyncTest() throws Exception {
        final Waiter waiter = new Waiter();

        new Thread(new Runnable() {
            @Override
            public void run() {
                waiter.assertEquals("foo", "foo");
                waiter.resume();
            }
        }).start();

        waiter.await(1000);
    }

    @Test
    public void openCameraWithoutTarget() throws Exception {
        final Context appContext = InstrumentationRegistry.getTargetContext();

        final Waiter waiter = new Waiter();
        TestUtils.ExpectError errorHandler = new TestUtils.ExpectError(IllegalArgumentException.class, waiter);

        Camera3 camera3 = new Camera3(appContext, errorHandler);
        String cameraId = camera3.getAvailableCameras().get(0);

        camera3.startCaptureSession(cameraId, null, null);


        assertTrue(errorHandler.gotError());

        waiter.await(5, SECONDS);
    }

    @Test
    public void openCamera() throws Exception {
        final Context appContext = InstrumentationRegistry.getTargetContext();

        final Waiter waiter = new Waiter();

        Camera3 camera3 = new Camera3(appContext, testErrorHandler);
        String cameraId = camera3.getAvailableCameras().get(0);

        final Size size = camera3.getLargestAvailableSize(cameraId, ImageFormat.JPEG);
        final StillCaptureHandler cs = new StillCaptureHandler(
                ImageFormat.JPEG, size,
                new OnImageAvailableListener() {
                    @Override
                    public void onImageAvailable(Image image) {

                    }
                });

        camera3.startCaptureSession(cameraId, null, Arrays.asList(cs),
                null, new Runnable() {
                    @Override
                    public void run() {
                        waiter.resume();
                    }
                });

        waiter.await(5, SECONDS);
    }


}
