package com.avalancheevantage.camera3;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

/**
 * Instrumentation test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
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
        public void info(String message) {}
    };

    @Test
    public void openCamera() throws Exception {
        Context appContext = InstrumentationRegistry.getTargetContext();

        Camera3 camera = new Camera3(appContext, testErrorHandler);
    }
}
