package com.avalancheevantage.android.camera3;

import android.Manifest;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.GrantPermissionRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;
import android.util.Size;

import net.jodah.concurrentunit.Waiter;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.avalancheevantage.android.camera3.TestUtils.testErrorHandler;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Created by Quinn Freedman on 11/13/2017.
 */

@RunWith(AndroidJUnit4.class)
public class PreviewTests {

    @Rule
    public GrantPermissionRule permissionRule =
            GrantPermissionRule.grant(Manifest.permission.CAMERA);

    @Test
    public void StartPreviewWithoutCrashing() throws Exception {
        final Context appContext = InstrumentationRegistry.getTargetContext();

        Camera3 camera = new Camera3(appContext, testErrorHandler);
        PreviewHandler previewHandler = new PreviewHandler(
                new SurfaceTexture(1),
                new Size(200, 200));
        String cameraId = camera.getAvailableCameras().get(0);
        camera.startCaptureSession(cameraId, previewHandler, null);
    }

    @Test
    public void PreviewStatusCallback() throws Exception {
        final Context appContext = InstrumentationRegistry.getTargetContext();
        final Waiter waiter = new Waiter();

        Camera3 camera = new Camera3(appContext, testErrorHandler);
        PreviewHandler previewHandler = new PreviewHandler(
                new SurfaceTexture(1),
                new Size(200, 200));
        String cameraId = camera.getAvailableCameras().get(0);
        camera.setCaptureResultListener(new CaptureResultListener() {
            @Override
            public void onResult(Camera3.CameraState state, CaptureResult result) {
                if (state == Camera3.CameraState.PREVIEW) {
                    waiter.assertNotNull(result);
                    waiter.resume();
                }
            }
        });
        camera.startCaptureSession(cameraId, previewHandler, null);

        waiter.await(5, SECONDS);

    }

    @Test
    public void CustomPreviewRequest() throws Exception {
        final Context appContext = InstrumentationRegistry.getTargetContext();
        final Waiter waiter = new Waiter();

        Camera3 camera = new Camera3(appContext, testErrorHandler);

        CaptureRequestConfiguration config = new CaptureRequestConfiguration() {
            @Override
            public void configure(CaptureRequest.Builder request) {
                request.set(CaptureRequest.CONTROL_EFFECT_MODE,
                            CaptureRequest.CONTROL_EFFECT_MODE_NEGATIVE);
            }
        };

        PreviewHandler previewHandler = new PreviewHandler(
                new SurfaceTexture(1),
                new Size(200, 200),
                config);
        String cameraId = camera.getAvailableCameras().get(0);
        camera.setCaptureResultListener(new CaptureResultListener() {
            @Override
            public void onResult(Camera3.CameraState state, CaptureResult result) {
                if (state == Camera3.CameraState.PREVIEW) {
                    waiter.assertNotNull(result);
                    waiter.assertEquals(
                            CaptureRequest.CONTROL_EFFECT_MODE_NEGATIVE,
                            result.get(CaptureResult.CONTROL_EFFECT_MODE));

                    waiter.resume();
                }
            }
        });
        camera.startCaptureSession(cameraId, previewHandler, null);

        waiter.await(5, SECONDS);

    }

}
