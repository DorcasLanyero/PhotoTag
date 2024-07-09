package com.sdgsystems.collector.photos.ui.fragment;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatDialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.sdgsystems.collector.photos.R;
import com.sdgsystems.collector.photos.Utilities;

import java.util.Calendar;
import java.util.Date;

/**
 * Created by bfriedberg on 9/6/17.
 */

public class AboutDialogFragment extends AppCompatDialogFragment{

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

    // Use the Builder class for convenient dialog construction
    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

    LayoutInflater inflater = getActivity().getLayoutInflater();

    View view = inflater.inflate(R.layout.about_dialog, null);
    TextView copyright = view.findViewById(R.id.copyright_text);
    CharSequence text = copyright.getText();
    Calendar c = Calendar.getInstance();
    text = text + Integer.toString(c.get(Calendar.YEAR));
    copyright.setText(text);

    view.findViewById(R.id.logsButton).setOnClickListener(v -> {
        Utilities.collectLogs(getContext());
    });

    try {
        ((TextView) view.findViewById(R.id.txtVersion)).setText("Version: " + getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0).versionName);

        ((TextView) view.findViewById(R.id.txtDebug)).setText(Utilities.useCamera1(getActivity().getApplicationContext()) ? "camera1" : "camera2" + "\n" +
        "Device: " + Build.DEVICE);
    } catch (PackageManager.NameNotFoundException e) {
        e.printStackTrace();
    }
    builder.setView(view);

    // Create the AlertDialog object and return it
    return builder.create();
    }
}
