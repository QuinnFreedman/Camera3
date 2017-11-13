# Camera3

**Camera3** is a wrapper around the [Android Camera2 API](https://developer.android.com/reference/android/hardware/camera2/package-summary.html). It aims to provide a simpler, safer interface to the underlying power of Camera2.

Right now, Camera3 only supports preview and still capture functionality. However, it will add video and burst capture support very soon.

Camera3 isn't on MavenCentral or JCenter yet either. If you want to use it now, just copy the `camera3` module from this repo into your app. Soon, it will be available as a dependency that you can simply add to your `build.gradle`.

## The Problem
Beyond the keyboard, the camera is one of the most basic IO devices for a mobile app. If you are on Android, you have two choices for accessing the camera:

 If you just want to get a simple image from the user, you can use [Android's built in image capture Intent](https://developer.android.com/training/camera/photobasics.html). If this is all you need, use it. It's not bad. But it gives you no control whatsoever. Do you want to style the camera activity with your apps theme or choose where the image gets saved? Sorry, out of luck. Let alone trying to do anything custom with focus, exposure, image processing, etc.

If that doesn't work for you, you can use Android's new Camera2 API. Camera2 gives you *WAY* too much control. Camera2 is very powerful, but it's one of the worst APIs  I've ever encountered. It's heavily asynchronous, but it doesn't abstract any of this asynchronousity away behind high-level calls. This, combined with a generally sprawling design, means that the official "*basic*" Google code example ([camera2basic](https://github.com/googlesamples/android-Camera2Basic/blob/master/Application/src/main/java/com/example/android/camera2basic/Camera2BasicFragment.java)) is over 1000 lines long, just to capture a still image. The control flow is almost impossible to follow as well.

If you needed any more evidence that Camera2 is a pain, this is a short snippet of the control flow from camera2basic:
> First, it has to go through the usual Android rigmarole of acquiring the needed permissions. Then, it has to wait until its custom preview `TextureView` is ready with `mSurfaceTextureListener` (unless it's already ready, in which case the control flow branches). When `mSurfaceTextureListener` is invoked, it calls `openCamera()` with the texture size it got from the texture surface. `openCamera()` sets up a new `CaptureListener` with the capture image reader's surface and a new background thread handler and then (after a few hundred lines of bazaar logic and math) opens the camera with a `StateCallback`. When the camera is finally opened, this `StateCallback` calls another method to actually create the preview session. Sort of. Actually it calls a `createCaptureSession` method which takes another callback with an `onConfigured()` method. When `onConfigured()` is invoked, it does some more configuring, sets more global variables, and then starts a repeating request to the camera with a `CaptureCallback`. This callback is a *four-way switch statement*, each case of which mutates the switch condition according to their own few hundred lines of logic, which are scattered across the file in different random methods, as this callback is repeatedly called every frame. And this is just for the preview.

This is bad design because 99% of the time, you want to do the exact same thing at each step along the way. After you open the camera, you *almost always* want to configure it with some settings. After it's configured, you *almost always* want to start a session with it. This means that camera2 applications usually ammound to 900 lines of boilerplate around a few lines of logic. You could keep 99% of the power of camera2 by just providing all your configuration and settings information up front and then letting the library deal with all the asynchronous sequencing and resource management for you.

## The Solution

Camera3 takes this simpler approach. You specify all your parameters up front, then send a request. Camera3 handles all the back-and-forth behind the scenes and then notifies you when it has a result. Start a session like this:

```
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

```
camera.captureImage(capture, Camera3.PRECAPTURE_CONFIG_TRIGGER_AUTO_FOCUS, Camera3.CAPTURE_CONFIG_DEFAULT);
```

Of course, if you want more control, you can specify your own configurations and add additional listeners, instead of using the minimum defaluts.
