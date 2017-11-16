package com.avalancheevantage.android.camera3;

import android.Manifest;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.GrantPermissionRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Size;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.avalancheevantage.android.camera3.TestUtils.testErrorHandler;

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
}
