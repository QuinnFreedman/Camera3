package com.avalancheevantage.camera3;

import android.content.Context;
import android.graphics.ImageFormat;
import android.media.Image;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Size;

import org.jetbrains.annotations.Contract;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Instrumentation test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class BasicInstrumentedTest {
    @Test
    public void useAppContext() throws Exception {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getTargetContext();

        assertEquals("com.avalancheevantage.camera3", appContext.getPackageName());
    }

    private static class ErrorHandlerErrorException extends RuntimeException {
        ErrorHandlerErrorException(String message, Exception cause) {
            super(message, cause);
        }
    }

    private static class ErrorHandlerWarningException extends RuntimeException {
        ErrorHandlerWarningException(String message) {
            super(message);
        }
    }

    private static final ErrorHandler testErrorHandler = new ErrorHandler() {
        @Override
        public void error(String message, @Nullable Exception e) {
            throw new ErrorHandlerErrorException(message, e);
        }

        @Override
        public void warning(String message) {
            throw new ErrorHandlerWarningException(message);
        }

        @Override
        public void info(String message) {
        }
    };

    @Contract(pure = true)
    @NonNull
    private static ErrorHandler expectError(@NonNull final Class<? extends Exception> exception,
                                            @NonNull final Lock lock) {
        lock.lock();
        return new ErrorHandler() {
            @Override
            public void error(String message, @Nullable Exception e) {
                if (e != null && e.getClass() == exception) {
                    assertTrue(true);
                    synchronized (lock) {
                        lock.unlock();
                    }
                } else {
                    throw new ErrorHandlerErrorException(message, e);
                }
            }

            @Override
            public void warning(String message) {
                throw new ErrorHandlerWarningException(message);
            }

            @Override
            public void info(String message) {

            }
        };
    }

    @Test
    public void instantiateWithoutCrashing() throws Exception {
        Context appContext = InstrumentationRegistry.getTargetContext();

        Camera3 camera = new Camera3(appContext, testErrorHandler);
    }

    @Test
    public void openCameraWithoutTarget() throws Exception {
        Context appContext = InstrumentationRegistry.getTargetContext();

        final Lock lock = new ReentrantLock();

        Camera3 camera3 = new Camera3(appContext, expectError(IllegalArgumentException.class, lock));
        String cameraId = camera3.getAvailableCameras().get(0);
        camera3.startCaptureSession(cameraId, null, null);

        synchronized (lock) {
            assertTrue(lock.tryLock(5, SECONDS));
        }

    }

    @Test
    public void startCaptureSession() throws Exception {
        Context appContext = InstrumentationRegistry.getTargetContext();

        final Lock lock = new ReentrantLock();

        Camera3 camera3 = new Camera3(appContext, expectError(IllegalArgumentException.class, lock));
        String cameraId = camera3.getAvailableCameras().get(0);

        Size size = camera3.getLargestAvailableSize(cameraId, ImageFormat.JPEG);

        StillImageCaptureSession cs = camera3.createStillImageCaptureSession(
                ImageFormat.JPEG, size, new OnImageAvailableListener() {
                    @Override
                    public void onImageAvailable(Image image) {

                    }
                });

        camera3.startCaptureSession(cameraId, null, Arrays.asList(cs));

        synchronized (lock) {
            assertTrue(lock.tryLock(5, SECONDS));
        }

//        camera3.pause();
    }
}
