package com.sdgsystems.collector.photos.ui.fragment;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatDialogFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.sdgsystems.blueloggerclient.SDGLog;
import com.sdgsystems.collector.photos.R;
import com.sdgsystems.collector.photos.data.adapters.ScannerAdapter;
import com.sdgsystems.collector.photos.scanning.LocalScanManager;
import com.sdgsystems.collector.photos.scanning.ScannerOption;
import com.sdgsystems.collector.photos.ui.activity.ThumbnailListActivity;

import java.util.List;

/**
 * Created by bfriedberg on 9/6/17.
 */

public class ScannerSelectionDialogFragment extends AppCompatDialogFragment{

    public static final String TAG = "orgselect";

    ThumbnailListActivity.IScannerSelect mCallback;

    public void setCallback(ThumbnailListActivity.IScannerSelect callback) {
        mCallback = callback;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        LayoutInflater inflater = getActivity().getLayoutInflater();

        View view = inflater.inflate(R.layout.list_selection_dialog, null);

        final ListView listView = (ListView) view.findViewById(R.id.list_selection_list_view);

        List<ScannerOption> scanners = LocalScanManager.getInstance(getContext()).enumerateScanners();

        if(scanners != null) {
            ScannerAdapter adapter = new ScannerAdapter(this.getContext(), scanners);
            listView.setAdapter(adapter);
        }

        builder.setTitle("Select Scanner");

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                itemSelected(adapterView, i);
            }
        });

        listView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                itemSelected(adapterView, i);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                dismiss();
            }
        });

        builder.setView(view);

        // Create the AlertDialog object and return it
        return builder.create();
    }

    private void itemSelected(AdapterView<?> adapterView, int i) {
        SDGLog.d(TAG, "item selected: " + i);
        mCallback.scannerSelected(i);

        dismiss();
    }
}
