package com.sdgsystems.collector.photos.data.adapters;

import android.content.Context;
import androidx.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.sdgsystems.collector.photos.R;
import com.sdgsystems.collector.photos.scanning.ScannerOption;

import java.util.List;

/**
 * Created by bfriedberg on 10/4/17.
 */

public class ScannerAdapter extends ArrayAdapter<ScannerOption> {
    public ScannerAdapter(@NonNull Context context, @NonNull List<ScannerOption> objects) {
        super(context, 0, objects);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
       // Get the data item for this position
        ScannerOption scannerOption = getItem(position);
       // Check if an existing view is being reused, otherwise inflate the view
       if (convertView == null) {
          convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_site, parent, false);
       }
       // Lookup view for data population
       TextView siteName = (TextView) convertView.findViewById(R.id.siteName);
       // Populate the data into the template view using the data object
        siteName.setText(scannerOption.name);
       // Return the completed view to render on screen
       return convertView;
   }
}
