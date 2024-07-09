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
import com.sdgsystems.collector.photos.GenericPhotoApplication;
import com.sdgsystems.collector.photos.R;
import com.sdgsystems.collector.photos.data.adapters.SiteAdapter;
import com.sdgsystems.collector.photos.data.model.Site;
import com.sdgsystems.collector.photos.ui.activity.ThumbnailListActivity;

import java.util.ArrayList;

/**
 * Created by bfriedberg on 9/6/17.
 */

public class SiteSelectionDialogFragment extends AppCompatDialogFragment{

    public static final String TAG = "orgselect";

    ThumbnailListActivity.ISiteSelect mCallback;

    public void setCallback(ThumbnailListActivity.ISiteSelect callback) {
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

        ArrayList<Site> sites = GenericPhotoApplication.getInstance().sites;

        Site Site = GenericPhotoApplication.getInstance().getCurrentSite();

        if(sites != null) {
            SiteAdapter adapter = new SiteAdapter(this.getContext(), sites);
            listView.setAdapter(adapter);
        }

        builder.setTitle("Select Site");

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
        Site site = (Site) adapterView.getItemAtPosition(i);
        siteSelected(site);
    }

    private void siteSelected(Site site) {
        GenericPhotoApplication.getInstance().setCurrentSite(site);

        mCallback.siteSelected();
        dismiss();
    }
}
