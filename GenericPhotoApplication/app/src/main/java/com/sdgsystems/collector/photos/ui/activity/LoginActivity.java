package com.sdgsystems.collector.photos.ui.activity;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputLayout;
import com.sdgsystems.blueloggerclient.SDGLog;
import com.sdgsystems.collector.photos.Constants;
import com.sdgsystems.collector.photos.R;

public class LoginActivity extends AppCompatActivity {
    private static final String TAG = LoginActivity.class.getSimpleName();

    Dialog mDialog;
    Activity activity = LoginActivity.this;
    Context context;

    @RequiresApi(api = Build.VERSION_CODES.N_MR1)
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_dialog);

        context = getApplicationContext();

        final SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(this);
        String savedUsername = p.getString(Constants.PREF_USERNAME, getResources().getString(R.string.default_username));
        String savedPassword = getResources().getString(R.string.default_password);
        String savedSubdomain = p.getString(Constants.PREF_SUBDOMAIN, getResources().getString(R.string.default_subdomain));
        String savedDomain = p.getString(Constants.PREF_DOMAIN, getResources().getString(R.string.default_domain));

        if(p.getBoolean(Constants.PREF_REMEMBER_PASSWORD, false)) {
            savedPassword = p.getString(Constants.PREF_PASSWORD, getResources().getString(R.string.default_password));
        }

        ((CheckBox) findViewById(R.id.checkRememberPassword)).setChecked(p.getBoolean(Constants.PREF_REMEMBER_PASSWORD, false));
        ((CheckBox) findViewById(R.id.checkRememberPassword)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences.Editor e = p.edit();
                e.putBoolean(Constants.PREF_REMEMBER_PASSWORD, isChecked);
                e.commit();
            }
        });

        TextInputLayout subdomainLayout = findViewById(R.id.txtSubdomainLayout);
        TextInputLayout userNameLayout = findViewById(R.id.txtUsernameLayout);
        TextInputLayout passwordLayout = findViewById(R.id.txtPasswordLayout);

        subdomainLayout.setHintEnabled(false);
        userNameLayout.setHintEnabled(false);
        passwordLayout.setHintEnabled(false);


        EditText txtUsername = ((EditText)  findViewById(R.id.txtUsername));
        EditText txtPassword = ((EditText)  findViewById(R.id.txtPassword));
        EditText txtSubdomain = ((EditText) findViewById(R.id.txtSubdomain));
        txtUsername.setText(savedUsername);
        txtPassword.setText(savedPassword);
        txtSubdomain.setText(savedSubdomain);

        txtSubdomain.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if(hasFocus){
                    subdomainLayout.setHintEnabled(true);

                }else{
                    subdomainLayout.setHintEnabled(false);
                }
            }
        });

        txtUsername.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if(hasFocus){
                    userNameLayout.setHintEnabled(true);

                }else{
                    userNameLayout.setHintEnabled(false);
                }
            }
        });

        txtPassword.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if(hasFocus){
                    passwordLayout.setHintEnabled(true);

                }else{
                    passwordLayout.setHintEnabled(false);
                }
            }
        });

        String[] COUNTRIES = getResources().getStringArray(R.array.domain_names);
        Spinner spinnerDomains = findViewById(R.id.txtServerName);
        SpinnerAdapter a = spinnerDomains.getAdapter();
        String wantedDomain = savedDomain != null ? savedDomain : "phototag.app";
        SDGLog.d(TAG, "savedDomain " + savedDomain + " wantedDomain " + wantedDomain);
        for (int i = 0; i < a.getCount(); i++) {
            String domain = (String) a.getItem(i);
            if (domain.equals(wantedDomain)) {
                SDGLog.i(TAG, "Setting domain to " + domain);
                spinnerDomains.setSelection(i, true);
            }
        }

        Button login = ((Button) findViewById(R.id.login));
        login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.onUserInteraction();

                //Login happened
                String userName = txtUsername.getText().toString();
                String password = txtPassword.getText().toString();
                String subdomain = txtSubdomain.getText().toString();
                String domain = (String) spinnerDomains.getSelectedItem();

                SharedPreferences.Editor editor = p.edit();
                editor.putString(Constants.PREF_USERNAME, userName);
                editor.putString(Constants.PREF_PASSWORD, password);
                editor.putString(Constants.PREF_SUBDOMAIN, subdomain);
                editor.putString(Constants.PREF_DOMAIN, domain);

                editor.commit();

                ThumbnailListActivity listActivity = ThumbnailListActivity.ctxt;

                listActivity.performLogin();

                Intent intent = new Intent(context, ThumbnailListActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
                startActivity(intent);
            }
        });

        Button useOffline = ((Button) findViewById(R.id.useOffline));

        TextView register = (TextView) findViewById(R.id.newUserSignup);
        register.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String domain = (String) spinnerDomains.getSelectedItem();
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(
                        getResources().getString(R.string.register_url, domain)));
                startActivity(browserIntent);
            }
        });

    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @NonNull
    //@Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(this);
        String savedUsername = p.getString(Constants.PREF_USERNAME, getResources().getString(R.string.default_username));
        String savedPassword = getResources().getString(R.string.default_password);

        if(p.getBoolean(Constants.PREF_REMEMBER_PASSWORD, false)) {
            savedPassword = p.getString(Constants.PREF_PASSWORD, getResources().getString(R.string.default_password));
        }

        String savedSubdomain = p.getString(Constants.PREF_SUBDOMAIN, getResources().getString(R.string.default_subdomain));
        String savedDomain = p.getString(Constants.PREF_DOMAIN, getResources().getString(R.string.default_domain));

        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        LayoutInflater inflater = this.getLayoutInflater();

        View view = inflater.inflate(R.layout.login_dialog, null);


        ((CheckBox) view.findViewById(R.id.checkRememberPassword)).setChecked(p.getBoolean(Constants.PREF_REMEMBER_PASSWORD, false));
        ((CheckBox) view.findViewById(R.id.checkRememberPassword)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences.Editor e = p.edit();
                e.putBoolean(Constants.PREF_REMEMBER_PASSWORD, isChecked);
                e.commit();
            }
        });

        EditText txtUsername = ((EditText) view.findViewById(R.id.txtUsername));
        EditText txtPassword = ((EditText) view.findViewById(R.id.txtPassword));
        EditText txtSubdomain = ((EditText) view.findViewById(R.id.txtSubdomain));
        txtUsername.setText(savedUsername);
        txtPassword.setText(savedPassword);
        txtSubdomain.setText(savedSubdomain);

        String[] COUNTRIES = getResources().getStringArray(R.array.domain_names);
        Spinner spinnerDomains = view.findViewById(R.id.txtServerName);
        SpinnerAdapter a = spinnerDomains.getAdapter();
        String wantedDomain = savedDomain != null ? savedDomain : "phototag.app";
        SDGLog.d(TAG, "savedDomain " + savedDomain + " wantedDomain " + wantedDomain);
        for (int i = 0; i < a.getCount(); i++) {
            String domain = (String) a.getItem(i);
            if (domain.equals(wantedDomain)) {
                SDGLog.i(TAG, "Setting domain to " + domain);
                spinnerDomains.setSelection(i, true);
            }
        }
        builder.setView(view);

        Button login = ((Button) view.findViewById(R.id.login));
        login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // getActivity().onUserInteraction();

                //Login happened
                String userName = txtUsername.getText().toString();
                String password = txtPassword.getText().toString();
                String subdomain = txtSubdomain.getText().toString();
                String domain = (String) spinnerDomains.getSelectedItem();

                SharedPreferences.Editor editor = p.edit();
                editor.putString(Constants.PREF_USERNAME, userName);
                editor.putString(Constants.PREF_PASSWORD, password);
                editor.putString(Constants.PREF_SUBDOMAIN, subdomain);
                editor.putString(Constants.PREF_DOMAIN, domain);

                editor.commit();

                ThumbnailListActivity listActivity = ThumbnailListActivity.ctxt;

                listActivity.performLogin();
                Intent intent = new Intent(context, ThumbnailListActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
                startActivity(intent);

            }
        });

        Button useOffline = ((Button) view.findViewById(R.id.useOffline));

        TextView register = (TextView) view.findViewById(R.id.newUserSignup);
        register.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String domain = (String) spinnerDomains.getSelectedItem();
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(
                        getResources().getString(R.string.register_url, domain)));
                startActivity(browserIntent);
            }
        });

        // Create the AlertDialog object and return it
        mDialog = builder.create();
        return mDialog;
    }
}
