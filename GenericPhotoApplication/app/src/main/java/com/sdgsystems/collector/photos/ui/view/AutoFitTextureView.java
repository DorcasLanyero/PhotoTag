/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sdgsystems.collector.photos.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TextureView;

import com.sdgsystems.blueloggerclient.SDGLog;

/**
 * A {@link TextureView} that can be adjusted to a specified aspect ratio.
 */
public class AutoFitTextureView extends TextureView {

    private int mRatioWidth = 0;
    private int mRatioHeight = 0;

    private boolean waitForSettling = true;

    private static String TAG = "AutoFitTextureView";

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
        SDGLog.d(TAG, "Setting aspect ratio: " + width + " " + height);

        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        mRatioWidth = width;
        mRatioHeight = height;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        SDGLog.d(TAG, "OnMeasure: " + width + " " + height);

        if (0 == mRatioWidth || 0 == mRatioHeight) {

            SDGLog.d(TAG, "ratios had a zero: " + width + " " + height);
            waitForSettling = true;

            setMeasuredDimension(width, height);
        } else {

            if (width < height * mRatioWidth / mRatioHeight) {

                SDGLog.d(TAG, "width is less: " + width + " " + width * mRatioHeight / mRatioWidth);

                setMeasuredDimension(width, width * mRatioHeight / mRatioWidth);
            } else {
                SDGLog.d(TAG, "height is less: " + height * mRatioHeight / mRatioWidth + " " + height);
                //Only handle the FIRST remeasure (getting a bad second value in landscape?)
                if(waitForSettling == false) {
                    setMeasuredDimension(height * mRatioWidth / mRatioHeight, height);
                } else {
                    SDGLog.d(TAG, "throwing out this landscape measurement since we are waiting to settle...");
                }

            }

            waitForSettling = false;
        }
    }
}