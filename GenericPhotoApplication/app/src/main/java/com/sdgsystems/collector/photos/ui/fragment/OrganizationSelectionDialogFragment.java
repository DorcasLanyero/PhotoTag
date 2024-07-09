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
import com.sdgsystems.collector.photos.data.adapters.OrganizationAdapter;
import com.sdgsystems.collector.photos.data.model.Organization;
import com.sdgsystems.collector.photos.ui.activity.ThumbnailListActivity;

import java.util.ArrayList;

/**
 * Created by bfriedberg on 9/6/17.
 */

public class OrganizationSelectionDialogFragment extends AppCompatDialogFragment{

    public static final String TAG = "orgselect";

    ThumbnailListActivity.IOrgSelect mCallback;

    public void setCallback(ThumbnailListActivity.IOrgSelect callback) {
        mCallback = callback;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        LayoutInflater inflater = getActivity().getLayoutInflater();

        View view = inflater.inflate(R.layout.org_selection_dialog, null);

        final ListView listView = (ListView) view.findViewById(R.id.org_list_view);

        ArrayList<Organization> orgs = GenericPhotoApplication.getInstance().organizations;

        Organization currentOrg = GenericPhotoApplication.getInstance().getCurrentOrg();

        if(orgs != null) {
            OrganizationAdapter adapter = new OrganizationAdapter(this.getContext(), orgs);
            listView.setAdapter(adapter);

            //No need to set the value to 'selected' if we have it displayed on the title...
            /*if(currentOrg != null) {
                for(int index = 0; index < orgs.size(); index++) {
                    if(orgs.get(index).id.equals(currentOrg.id)) {
                        listView.setSelection(index);
                    }
                }
            }*/
        }

        builder.setTitle("Select Organization");

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
        Organization organization = (Organization) adapterView.getItemAtPosition(i);
        orgSelected(organization);
    }

    private void orgSelected(Organization organization) {
        GenericPhotoApplication.getInstance().setCurrentOrg(organization);

        mCallback.orgSelected();
        dismiss();
    }
}
