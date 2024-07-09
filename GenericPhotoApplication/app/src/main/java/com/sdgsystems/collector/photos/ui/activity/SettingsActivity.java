package com.sdgsystems.collector.photos.ui.activity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;

import com.sdgsystems.collector.photos.Constants;
import com.sdgsystems.collector.photos.ui.fragment.AdminPINDialogFragment;
import com.sdgsystems.collector.photos.ui.fragment.PINDialogFragmentListener;

/**
 * Created by bfriedberg on 8/2/17.
 */

public class SettingsActivity extends AppCompatActivity implements PINDialogFragmentListener {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        // Display the fragment as the main content.
        getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if(!prefs.getString(Constants.PREF_ADMIN_PIN, "").isEmpty()) {
            AdminPINDialogFragment frag = new AdminPINDialogFragment();
            frag.show(getSupportFragmentManager(), "PINFragment");
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSuccess() {

    }

    @Override
    public void onFail() {

    }
}
