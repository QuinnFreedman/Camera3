![Camera 3](images/Camera3.png)

**Camera3** is a wrapper around the notoriously difficult [Android Camera2 API](https://developer.android.com/reference/android/hardware/camera2/package-summary.html). It aims to provide a simpler, safer interface to the underlying power of Camera2.

Camera3 is available on jcenter. To use it, make sure you have 
```groovy
repositories {
    jcenter()
}
```
in your `build.gradle` and then add the following in your dependencies
```groovy
compile 'com.avalancheevantage.android:camera3:0.1.0'
```

Right now, Camera3 only supports preview and still capture functionality. However, it will add video and burst capture support very soon.

## Why Camera3?
The current Android API ([Camera2](https://developer.android.com/reference/android/hardware/camera2/package-summary.html)) is notoriously bad. Using it requires an elaborate ceremony of callbacks and listeners. Google's official "basic" example using camera2 is [over 1000 lines long](https://github.com/googlesamples/android-Camera2Basic/blob/master/Application/src/main/java/com/example/android/camera2basic/Camera2BasicFragment.java), just to take a simple picture.

There are already a few Android camera utilities available. [Material Camera](https://github.com/afollestad/material-camera) and [CameraKit](https://github.com/wonderkiln/CameraKit-Android/blob/master/demo/src/main/java/com/wonderkiln/camerakit/demo/MainActivity.java) both probide you with pre-packaged camera views that you can drop into your app. Camera3 isn't like that.

Camera3 was originally born from highly custom CV applications that need to make full use of the power of the Android camera. **Camera3 is designed to be a direct replacement for camera2**. It is designed to provide almost all the power of camera2 through a much better designed API.

If you want to, Camera3 gives you the power to do crazy things like capture images without showing a preview, or show the preview as a texture on the side of an OpenGL-rendered 3D model, or even record from the front- and back-facing cameras at the same time (if your hardware supports that).

Despite all this power, using Camera3 can be done in just a handful of lines.

## Quick Setup

In Camera3, you specify all your parameters up front, then send a request. Camera3 handles all the back-and-forth behind the scenes and then notifies you when it has a result. Start a session like this:

```java
Camera3 camera = new Camera3(this, Camera3.ERROR_HANDLER_DEFAULT);

//...

PreviewHandler preview = new PreviewHandler(previewTextureView);
StillCaptureHandler capture = new StillCaptureHandler(ImageFormat.JPEG, imageSize,
        new OnImageAvaiableListener {
            @Override
            void onImageAvailable(Image image) {
                Log.d(TAG, "Image captured: "+image);
            }
        });
        
camera.startCameraSession(cameraId, preview, Arrays.asList(capture));
```

Then, later, you can can capture an image with just one line:

```java
camera.captureImage(capture, Camera3.PRECAPTURE_CONFIG_TRIGGER_AUTO_FOCUS, Camera3.CAPTURE_CONFIG_DEFAULT);
```

Of course, if you want more control, you can specify your own configurations and add additional listeners, instead of using the minimum defaluts.

## Documentation 

For a more complete example of camera3 in use, check out the camera3demo app in this repository.

You can read the official docs [here](https://quinnfreedman.github.io/Camera3/)
