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

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TextureView;

/**
 * A custom subclass of {@link TextureView} that will automatically adjust its size to fit a given
 * aspect ratio. This is very useful for displaying camera previews
 *
 * @author Quinn Freedman
 */
public class AutoFitTextureView extends TextureView {
    private static final String TAG = "AutoFitTextureView";

    public static final boolean STYLE_FILL = true;
    public static final boolean STYLE_FIT = false;

    private int mRatioWidth = 0;
    private int mRatioHeight = 0;
    private boolean fillStyle = false;

    public AutoFitTextureView(Context context) {
        this(context, null);
    }

    public AutoFitTextureView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AutoFitTextureView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * Sets the aspect ratio for this view. The size of the view will be measured based on the ratio
     * calculated from the parameters. Note that the actual sizes of parameters don't matter, that
     * is, calling setAspectRatio(2, 3) and setAspectRatio(4, 6) make the same result.
     *
     * @param width  Relative horizontal size
     * @param height Relative vertical size
     */
    public void setAspectRatio(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        mRatioWidth = width;
        mRatioHeight = height;
        requestLayout();
    }

    /**
     * Set whether this TextureView should scale enough to fit the available space (potentially
     * with empty margins) or enough to fill it entirely (potentially with cropping)
     * <p>
     * Use {@link #STYLE_FIT} or {@link #STYLE_FILL}
     *
     * @param fill whether the view should fit or fill the available space
     */
    public void setFill(boolean fill) {
        this.fillStyle = fill;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        Log.d(TAG, "Parent Size = " + width + "x" + height + ", aspect ratio = " + mRatioWidth + "x" + mRatioHeight);
        if (0 == mRatioWidth || 0 == mRatioHeight) {
            setMeasuredDimension(width, height);
        } else {
            if (this.fillStyle == STYLE_FILL) {
                if (width > height * mRatioWidth / mRatioHeight) {
                    Log.d(TAG, "target size = " + width + "x" + (width * mRatioHeight / mRatioWidth));
                    setMeasuredDimension(width, width * mRatioHeight / mRatioWidth);
                } else {
                    //overflow sideways
                    int overflowWidth = height * mRatioWidth / mRatioHeight;
                    setMeasuredDimension(overflowWidth, height);
//                    int offset = (overflowWidth - width) / 2;
//                    ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) getLayoutParams();
//                    params.setMargins(-offset, params.topMargin, -offset, params.bottomMargin);
//                    setLayoutParams(params);
                }
            } else {
                if (width < height * mRatioWidth / mRatioHeight) {
                    setMeasuredDimension(width, width * mRatioHeight / mRatioWidth);
                } else {
                    setMeasuredDimension(height * mRatioWidth / mRatioHeight, height);
                }
            }
        }
    }

}