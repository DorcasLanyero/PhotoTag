package com.sdgsystems.collector.photos.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import com.tokenautocomplete.TokenCompleteTextView;

/**
 * Created by jay on 6/6/17.
 */

public class TagCompletionTextView extends TokenCompleteTextView<String> {
    public TagCompletionTextView(Context context) {
        super(context);
    }

    public TagCompletionTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TagCompletionTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected View getViewForObject(String object) {
        TextView tv = new TagCompletionTextView(getContext());
        tv.setText(object);
        return tv;
    }

    @Override
    protected String defaultObject(String completionText) {
        return completionText;
    }
}
