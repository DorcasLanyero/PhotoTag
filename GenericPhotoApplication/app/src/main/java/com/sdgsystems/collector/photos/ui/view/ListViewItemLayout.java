package com.sdgsystems.collector.photos.ui.view;

import android.content.Context;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Checkable;
import android.widget.LinearLayout;

import com.sdgsystems.blueloggerclient.SDGLog;
import com.sdgsystems.collector.photos.R;

/**
 * Created by jay on 6/1/17.
 */

public class ListViewItemLayout extends LinearLayout implements Checkable {
    private static final String TAG = "ListViewItemLayout";
    boolean mChecked = false;

    public ListViewItemLayout(Context context) {
        super(context);
    }

    public ListViewItemLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ListViewItemLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setChecked(boolean checked) {
        mChecked = checked;

        if(checked) {
            findViewById(R.id.checkbox_image).setVisibility(View.VISIBLE);
        }
        else {
            findViewById(R.id.checkbox_image).setVisibility(View.GONE);
        }
    }

    @Override
    public boolean isChecked() {
        return mChecked;
    }

    @Override
    public void toggle() {
        SDGLog.d(TAG, "Toggling item");

        mChecked = !mChecked;

        setChecked(mChecked);
    }
}
