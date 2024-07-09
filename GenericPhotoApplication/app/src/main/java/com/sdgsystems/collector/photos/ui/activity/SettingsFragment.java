package com.sdgsystems.collector.photos.ui.activity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import com.sdgsystems.collector.photos.Constants;
import com.sdgsystems.collector.photos.R;
import com.sdgsystems.collector.photos.scanning.IDEngineGenericScanner;
import com.sdgsystems.collector.photos.scanning.IScanner;
import com.sdgsystems.collector.photos.scanning.LocalScanManager;
import com.sdgsystems.collector.photos.scanning.ScannerOption;

import java.util.List;

import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

/**
 * Created by bfriedberg on 8/2/17.
 */

public class SettingsFragment extends PreferenceFragmentCompat {

    private static final String TAG = SettingsFragment.class.getName();

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.prefs, rootKey);

        Preference selectPref = findPreference("select_scanner");
        Preference scannerSettingsPref = findPreference("scanner_settings");
        PreferenceCategory preferenceCategory = (PreferenceCategory) findPreference("barcode_settings");

        final IScanner scanManager = LocalScanManager.getInstance(getActivity());
        if (scanManager instanceof IDEngineGenericScanner) {

            preferenceCategory.setVisible(true);

            // Set the name of the currently selected scanner.
            List<ScannerOption> scanners = scanManager.enumerateScanners();
            if (scanners != null && scanners.size() > 0 && scanManager.getScannerIndex() >= 0) {
                selectPref.setSummary(scanners.get(scanManager.getScannerIndex()).name);
                scanManager.open();
            } else {
                selectPref.setSummary(R.string.no_scanners_avail);
            }

            selectPref.setOnPreferenceClickListener(preference -> {
                displayScannerSelectionDialog();
                return true;
            });

            scannerSettingsPref.setOnPreferenceClickListener(preference -> {
                scanManager.startSettingsActivity();
                return true;
            });

        } else {
            preferenceCategory.setVisible(false);
        }

        final String[] summaries = getResources().getStringArray(R.array.single_tag_mode_summaries);
        final String[] values = getResources().getStringArray(R.array.single_tag_mode_values);

        Preference singleTagModePref = findPreference(Constants.PREF_SINGLE_TAG_MODE);
        singleTagModePref.setOnPreferenceChangeListener((preference, newValue) -> {
            if(!(newValue instanceof String)) throw new IllegalArgumentException();

            String val = (String) newValue;

            for(int i = 0; i < values.length; i++) {
                if(values[i].equals(val)) {
                    preference.setSummary(summaries[i]);
                }
            }

            return true;
        });

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        for(int i = 0; i < values.length; i++) {
            if(values[i].equals(prefs.getString(singleTagModePref.getKey(), Constants.SINGLE_TAG_SCANNED_ONLY))) {
                singleTagModePref.setSummary(summaries[i]);
            }
        }

        EditTextPreference clearTagsTimeoutPref = findPreference(Constants.PREF_CLEAR_TAGS_TIMEOUT);
        clearTagsTimeoutPref.setOnPreferenceChangeListener((preference, newValue) -> {
            if(!(newValue instanceof String)) throw new IllegalArgumentException();

            String val = (String) newValue;
            if (!val.isEmpty()) {
                Long value = Long.parseLong(val);
                if (value < 30 || value > 6000) {
                    // Reset the value to the default if it's outside the valid range
                    // preference.setKey("120");
                    Toast.makeText(getActivity(),
                            "Valid values are 30-6000", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
            return true;
        });
    }

    private void displayScannerSelectionDialog() {

        final IScanner scanManager = LocalScanManager.getInstance(getActivity());
        final List<ScannerOption> scanners = scanManager.enumerateScanners();
        if (scanners == null || scanners.size() == 0) {
            Toast.makeText(getActivity(), R.string.no_scanners_avail, Toast.LENGTH_LONG).show();
            return;
        }
        int titleResource = R.string.select_scanner;
        final CharSequence[] scannerNames = new CharSequence[scanners.size()];
        int i = 0, selectedItem = -1;
        for (ScannerOption si : scanners) {
            //Todo: convert to sentence case
            scannerNames[i] = si.name;
            if (si.index == scanManager.getScannerIndex()) {
                selectedItem = i;
            }
            i++;
        }

        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(getActivity());
        b.setTitle(titleResource);
        b.setCancelable(true);
        b.setNegativeButton(android.R.string.cancel, null);
        b.setSingleChoiceItems(scannerNames, selectedItem,
                               (dialogInterface, index) -> {
                                   CharSequence name = scannerNames[index];
                                   // Convert list index to scanner index
                                   index = scanners.get(index).index;
                                   scanManager.setScannerIndex(index);
                                   Preference selectPref = findPreference("select_scanner");
                                   selectPref.setSummary(name);
                                   dialogInterface.dismiss();
                               });
        b.show();
    }

}
