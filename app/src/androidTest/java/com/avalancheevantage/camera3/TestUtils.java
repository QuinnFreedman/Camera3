package com.avalancheevantage.camera3;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import net.jodah.concurrentunit.Waiter;

/**
 * Created by Quinn Freedman on 11/10/2017.
 */

public class TestUtils {

    public static class ErrorHandlerErrorException extends RuntimeException {
        ErrorHandlerErrorException(String message, Exception cause) {
            super(message, cause);
        }
    }

    public static class ErrorHandlerWarningException extends RuntimeException {
        ErrorHandlerWarningException(String message) {
            super(message);
        }
    }

    public static final ErrorHandler testErrorHandler = new ErrorHandler() {
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

    public static class ExpectError implements ErrorHandler {

        @NonNull
        private final Class<? extends Exception> exception;
        @NonNull
        private final Waiter waiter;

        ExpectError(@NonNull final Class<? extends Exception> exception,
                    @NonNull final Waiter waiter) {
            this.exception = exception;
            this.waiter = waiter;
        }
        private boolean gotError = false;
        public boolean gotError() {
            return  gotError;
        }
        @Override
        public void error(String message, @Nullable Exception e) {
            waiter.assertNotNull(e);
            waiter.assertEquals(e.getClass(), exception);
            gotError = true;
            waiter.resume();
        }

        @Override
        public void warning(String message) {
            throw new ErrorHandlerWarningException(message);
        }

        @Override
        public void info(String message) {}
    }
}
