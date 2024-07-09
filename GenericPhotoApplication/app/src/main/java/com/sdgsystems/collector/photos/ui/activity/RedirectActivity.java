package com.sdgsystems.collector.photos.ui.activity;

import android.content.Intent;
import android.preference.PreferenceManager;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import com.sdgsystems.collector.photos.Constants;
import com.sdgsystems.collector.photos.R;
import com.sdgsystems.collector.photos.Utilities;

public class RedirectActivity extends AppCompatActivity {

    private static final String TAG = "RedirectActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String startScreen = PreferenceManager.getDefaultSharedPreferences(this).getString(Constants.PREF_START_SCREEN, "Photo List");

        switch(startScreen) {
            case "Camera":
                Utilities.captureImages(this);
                break;
            case "Tag Setup":
                Utilities.startTopLevelActivity(this, ImageSetupActivity.class);
                break;
            default:
                Utilities.startTopLevelActivity(this, ThumbnailListActivity.class);
                break;
        }
    }
}
