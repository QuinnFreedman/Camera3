package com.avalancheevantage.camera3;

import android.content.Context;
import android.graphics.ImageFormat;
import android.media.Image;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Size;

import net.jodah.concurrentunit.Waiter;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

import static com.avalancheevantage.camera3.TestUtils.testErrorHandler;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class BasicInstrumentedTest {
    @Test
    public void useAppContext() throws Exception {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getTargetContext();

        assertEquals("com.avalancheevantage.camera3", appContext.getPackageName());
    }


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
        final StillImageCaptureSession cs = camera3.createStillImageCaptureSession(
                ImageFormat.JPEG, size,
                new OnImageAvailableListener() {
                    @Override
                    public void onImageAvailable(Image image) {

                    }
                });

        camera3.startCaptureSession(cameraId, null, Arrays.asList(cs),
                new Runnable() {
                    @Override
                    public void run() {
                        waiter.resume();
                    }
                });

        waiter.await(5, SECONDS);
    }


}
