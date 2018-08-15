/*
 * Copied in part from the Google Samples github repository
 * (https://github.com/googlesamples/android-Camera2Basic),
 * with substantial modification
 *
 * The original file was distributed under the Apache v2 license
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is redistributed under the MIT License.
 *
 * See the included LICENSE file
 */

package com.avalancheevantage.android.camera3;

import android.media.Image;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A utility runnable for saving a JPEG {@link Image} into the specified {@link File}.
 */
public class ImageSaver implements Runnable {
    private final Image mImage;
    private final File mFile;

    /**
     * Constructs a new ImageSaver with the given parameters.
     *
     * <p>Note: If permission {@link android.Manifest.permission#WRITE_EXTERNAL_STORAGE} is
     * needed to write to the given file, it must be obtained before calling
     * {@link ImageSaver#run()}</p>
     *
     * @param image The JPEG image
     * @param file The file we save the image into.
     */
    public ImageSaver(Image image, File file) {
        mImage = image;
        mFile = file;
    }

    @Override
    public void run() {
        ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(mFile);
            output.write(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            mImage.close();
            if (null != output) {
                try {
                    output.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}