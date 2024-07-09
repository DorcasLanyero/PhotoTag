package com.sdgsystems.collector.photos.ui.activity;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.ExifInterface;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;

import fisk.chipcloud.ChipCloud;
import fisk.chipcloud.ChipCloudConfig;
import fisk.chipcloud.ChipDeletedListener;
import fisk.chipcloud.ChipListener;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.Window;
import android.widget.Toast;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sdgsystems.blueloggerclient.SDGLog;
import com.sdgsystems.collector.photos.BuildConfig;
import com.sdgsystems.collector.photos.Constants;
import com.sdgsystems.collector.photos.GenericPhotoApplication;
import com.sdgsystems.collector.photos.R;
import com.sdgsystems.collector.photos.data.AppDatabase;
import com.sdgsystems.collector.photos.data.dao.ImageDao;
import com.sdgsystems.collector.photos.data.dao.ImageFileDao;
import com.sdgsystems.collector.photos.data.model.Image;
import com.sdgsystems.collector.photos.data.model.ImageFile;
import com.sdgsystems.collector.photos.data.model.Organization;
import com.sdgsystems.collector.photos.data.model.Site;
import com.sdgsystems.collector.photos.scanning.GenericScanningCallback;
import com.sdgsystems.collector.photos.scanning.IScanner;
import com.sdgsystems.collector.photos.scanning.LocalScanManager;
import com.sdgsystems.collector.photos.sync.ImageFileUploader;
import com.sdgsystems.collector.photos.sync.ImageLoaderRequestQueue;
import com.sdgsystems.collector.photos.sync.ImageMetaDataUploader;
import com.sdgsystems.collector.photos.sync.NetworkRequestHandler;
import com.sdgsystems.collector.photos.sync.authorizedVolleyRequests.AuthorizedJsonArrayRequest;
import com.sdgsystems.collector.photos.ui.fragment.AboutDialogFragment;
import com.sdgsystems.collector.photos.ui.fragment.OrganizationSelectionDialogFragment;
import com.sdgsystems.collector.photos.ui.fragment.ScannerSelectionDialogFragment;
import com.sdgsystems.collector.photos.ui.fragment.SiteSelectionDialogFragment;
import com.sdgsystems.synchableapplication.SynchableConstants;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static android.nfc.NfcAdapter.ACTION_NDEF_DISCOVERED;
import static android.nfc.NfcAdapter.ACTION_TAG_DISCOVERED;
import static android.nfc.NfcAdapter.ACTION_TECH_DISCOVERED;
import static com.sdgsystems.collector.photos.Constants.ScannerType.SCANNER_TYPE_BARCODE;
import static com.sdgsystems.collector.photos.Constants.ScannerType.SCANNER_TYPE_RFID;

import org.json.JSONArray;

/**
 * Created by bfriedberg on 11/8/17.
 */

public abstract class ScanningActivity extends AppCompatActivity implements SensorEventListener, LoginDialogFragment.LoginDialogListener {

    protected ProgressDialog scanningOverlay;
    protected boolean cameraScanning = false;
    private static final String TAG = "ScanningActivity";

    protected PendingIntent pendingIntent;
    protected NfcAdapter mNfcAdapter;
    protected SensorManager sensorManager;
    LoginDialogFragment loginDialogFragment;
    private AboutDialogFragment aboutDialogFragment;
    private OrganizationSelectionDialogFragment orgSelectionDialogFragment;
    private SiteSelectionDialogFragment siteSelectionDialogFragment;
    private ScannerSelectionDialogFragment scannerSelectionDialogFragment;
    private SharedPreferences sharedPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SDGLog.d(TAG, "onCreate()");
        super.onCreate(savedInstanceState);
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
    }

    protected void initScanner(GenericScanningCallback scanningCallback) {
        LocalScanManager.setCallback(scanningCallback);
        registerForNFC();
    }

    private void registerForNFC() {
        SDGLog.d(TAG, "registerForNFC()");
        pendingIntent = PendingIntent.getActivity(
                this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE);
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (mNfcAdapter != null) {
            SDGLog.d(TAG, "registerForNFC: Enabling foreground dispatch");
            mNfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
        }
    }

    protected ScanningActivity() {
    }

    private long lastInteractionTimestamp = 0;

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        updateInteractionTimer();
    }

    @Override
    public void onLoginDialogPositiveClick() {
        loginDialogFragment = null;
        performLogin();
    }

    @Override
    public void onLoginDialogNegativeClick() {
        //login was cancelled
        loginDialogFragment = null;
    }


    private void performLogin() {
        final boolean debugLogin = BuildConfig.FLAVOR.contains("localNoAuth");

        // We're not logged in, so we must do so before populating the adapter.
        NetworkRequestHandler.LoginRequestWithCallback(
                new NetworkRequestHandler.ILoginRequestCallback() {
                    @Override
                    public void loginSucceeded() {
                        SDGLog.d(TAG, "Login succeeded");
                        //GenericPhotoApplication.getInstance().retrieveCategories();
                        new CategoriesActivity().retrieveCategories(ScanningActivity.this);

                        boolean debugOrgs = false;
                        boolean debugSites = false;

                        if(debugOrgs) {

                            ArrayList<Organization> debugOrgList = new ArrayList<>();

                            for(int index = 0; index < 3; index++) {
                                Organization debugOrg = new Organization();
                                debugOrg.name = "debug : " + index;
                                debugOrg.id = "index" + index;
                                debugOrgList.add(debugOrg);
                            }
                            updateOrganizationList(debugOrgList);

                        } else {
                            updateAvailableOrganizations();
                        }

                        if(debugSites) {
                            ArrayList<Site> debugSiteList = new ArrayList<>();

                            for(int index = 0; index < 3; index++) {
                                Site debugSite = new Site();
                                debugSite.name = "debug : " + index;
                                debugSite.id = "index" + index;
                                debugSiteList.add(debugSite);
                            }

                            updateSiteList(debugSiteList);
                        } else {
                            updateAvailableSites();
                        }

                        //Reset all objects with a status of 'uploading' to 'not uploaded' and resend
                        markUnsentDatabaseObjects();

                        if(loginCallback != null) loginCallback.loginAttempted(false);
                    }

                    @Override
                    public void loginFailed(int statusCode, String errorMsg) {
                        SDGLog.d(TAG, "Login failed");

                        String msg = errorMsg.toLowerCase();

                        if(debugLogin) {
                            SDGLog.d(TAG, "DEBUG LOGIN: continuing anyway");
                            if(loginCallback != null) loginCallback.loginAttempted(true);
                        }
                        String generalLogInError = "Login to " + GenericPhotoApplication.getInstance().getAuthUrl() + " failed: " + errorMsg + " " + statusCode;
                        if (msg.contains("invalid credentials")){
                            generalLogInError = "Invalid username or password";
                        } else if(msg.contains("rate limited")){
                            generalLogInError = "Too many recent attempts, please wait before attempting again";
                        } else if(msg.contains("handshake failed")){
                            generalLogInError = "Invalid subdomain";
                        } else if(msg.contains("account locked")){
                            generalLogInError = "There have been too many failed attempts for this account";
                        }
                        Toast.makeText(ScanningActivity.this, generalLogInError, Toast.LENGTH_LONG).show();

                        GenericPhotoApplication.getInstance().setBearerToken(null);
                        if(loginCallback != null) loginCallback.loginAttempted(false);

                        // Only show the dialog if we're running
                        if(getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
                            // Redisplay login dialog if logging in fails
                            displayLoginDialog();
                        }
                    }
                }, getApplicationContext());
    }

    private void markUnsentDatabaseObjects() {
        ImageFileDao imageFileDao = GenericPhotoApplication.getInstance().getDb().imageFileDao();
        ImageDao imageDao = GenericPhotoApplication.getInstance().getDb().imageDao();

        List<ImageFile> unsentFiles = imageFileDao.getUnsent();
        List<ImageFile> updateFiles = new ArrayList<>();
        List<Image> unsentImages = imageDao.getUnsent();
        List<Image> updateImages = new ArrayList<>();

        for(ImageFile imageFile : unsentFiles) {
            if(imageFile.getUploadStatus().equals(SynchableConstants.UPLOAD_STATUS_UPLOADING)) {
                imageFile.setUploadStatus(SynchableConstants.UPLOAD_STATUS_NOT_UPLOADED);
                updateFiles.add(imageFile);
            }
        }

        for(Image image : unsentImages) {
            if(image.getUploadStatus().equals(SynchableConstants.UPLOAD_STATUS_UPLOADING)) {
                image.setUploadStatus(SynchableConstants.UPLOAD_STATUS_NOT_UPLOADED);
                updateImages.add(image);
            }
        }

        if(updateFiles.size() > 0) {
            SDGLog.d(TAG, "Marking " + updateFiles.size() + " image files as " + SynchableConstants.UPLOAD_STATUS_NOT_UPLOADED);
            imageFileDao.update(updateFiles);
        }

        if(updateImages.size() > 0) {
            SDGLog.d(TAG, "Marking " + updateImages.size() + " images as " + SynchableConstants.UPLOAD_STATUS_NOT_UPLOADED);
            imageDao.update(updateImages);
        }
    }

    private void updateAvailableOrganizations() {
        AuthorizedJsonArrayRequest request = new AuthorizedJsonArrayRequest(GenericPhotoApplication.getInstance().getOrganizationsUrl(), new Response.Listener<JSONArray>() {
            @Override
            public void onResponse(JSONArray response) {
                SDGLog.d(TAG, "Response: " + response.toString());
                Type organzationListType = new TypeToken<ArrayList<Organization>>() {
                }.getType();

                Gson gson = Organization.getGson();
                final List<Organization> availableOrganizations = gson.fromJson(response.toString(), organzationListType);

                updateOrganizationList(availableOrganizations);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                SDGLog.d(TAG, "Organization Retrieval Error: " + error.getMessage());

                //Update the org list with an empty list
                updateOrganizationList(new ArrayList<Organization>());
                updateImageList();

                //Toast.makeText(ThumbnailListActivity.this, "Failed to get organizations for the current user", Toast.LENGTH_SHORT).show();
            }
        });

        RequestQueue q = ImageLoaderRequestQueue.getInstance(getApplicationContext()).getRequestQueue();
        q.add(request);
    }

    private void updateAvailableSites() {
        AuthorizedJsonArrayRequest request = new AuthorizedJsonArrayRequest(GenericPhotoApplication.getInstance().getSitesUrl(), new Response.Listener<JSONArray>() {
            @Override
            public void onResponse(JSONArray response) {
                SDGLog.d(TAG, "Response: " + response.toString());
                Type siteListType = new TypeToken<ArrayList<Site>>() {
                }.getType();

                Gson gson = Site.getGson();
                final List<Site> availableSites = gson.fromJson(response.toString(), siteListType);

                updateSiteList(availableSites);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                SDGLog.d(TAG, "Site Retrieval Error: " + error.getMessage());

                //Update the site list with an empty list
                updateSiteList(new ArrayList<Site>());
                updateImageList();

                //Toast.makeText(ThumbnailListActivity.this, "Failed to get sites for the current user", Toast.LENGTH_SHORT).show();
            }
        });

        RequestQueue q = ImageLoaderRequestQueue.getInstance(getApplicationContext()).getRequestQueue();
        q.add(request);
    }

    private void updateOrganizationList(List<Organization> availableOrganizations) {
        SDGLog.d(TAG, "Pulled available organizations: " + availableOrganizations);

        GenericPhotoApplication.getInstance().organizations = (ArrayList<Organization>) availableOrganizations;

        if(availableOrganizations.size() > 0) {
            GenericPhotoApplication.getInstance().setCurrentOrg(availableOrganizations.get(0));
        }

        if(loginCallback != null) loginCallback.dataChanged();

        updateImageList();
    }

    private void updateSiteList(List<Site> availableSites) {
        SDGLog.d(TAG, "Pulled available sites: " + availableSites);

        GenericPhotoApplication.getInstance().sites = (ArrayList<Site>) availableSites;

        if(availableSites.size() > 0) {

            //If there is no currently set 'selected' site, display the site selection dialog
            Site currentSite = GenericPhotoApplication.getInstance().getCurrentSite();
            if(currentSite == null) {
                displaySiteSelectionDialog();
            }
        }

        if(loginCallback != null) loginCallback.dataChanged();

        updateImageList();
    }

    private void updateInteractionTimer() {
        long currentTimestamp = new Date().getTime();
        long interactionSeconds = (currentTimestamp - lastInteractionTimestamp) / 1000;

        //If we're logged in (!null bearer token) and have taken longer than X seconds...
        if(GenericPhotoApplication.getInstance().getBearerToken() != null
            && lastInteractionTimestamp != 0
            && interactionSeconds > Constants.INTERACTION_TIMEOUT_SECONDS) {
            SDGLog.d(TAG,"interaction too old, logging out: " + currentTimestamp + " > " + lastInteractionTimestamp + " by " + interactionSeconds);
            GenericPhotoApplication.getInstance().setBearerToken(null);
            Toast.makeText(this, "Logging out due to inactivity", Toast.LENGTH_SHORT).show();

            finish();
            Intent intent = new Intent(this, ThumbnailListActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);

        } else {
            //Log.d(TAG,"interaction");
            lastInteractionTimestamp = new Date().getTime();
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        final MenuItem loginItem = menu.findItem(R.id.login);
        final MenuItem logoutItem = menu.findItem(R.id.logout);
        if (isLoggedIn()) {
            if (loginItem != null) loginItem.setVisible(false);
            if (logoutItem != null) logoutItem.setVisible(true);
        }
        else {
            if (loginItem != null) loginItem.setVisible(true);
            if (logoutItem != null) logoutItem.setVisible(false);
        }
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.settings) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else if (item.getItemId() == R.id.login) {
            displayLoginDialog();
        } else if (item.getItemId() == R.id.logout) {
            performLogout();
        } else if (item.getItemId() == R.id.about) {
            displayAboutDialog();
        } else if (item.getItemId() == R.id.switchOrg) {
            displayOrgSelectionDialog();
        } else if (item.getItemId() == R.id.switchSite) {
            displaySiteSelectionDialog();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPause() {
        SDGLog.d(TAG, "onPause()");
        super.onPause();

        findViewById(Window.ID_ANDROID_CONTENT).getViewTreeObserver()
                .removeOnGlobalLayoutListener(mLayoutKeyboardVisibilityListener);

        unregisterReceivers();
        sensorManager.unregisterListener(this);

        if(!cameraScanning) {
            SDGLog.d(TAG, "onPause: Closing scanner");
            closeScanner();
        }

        if(mNfcAdapter != null) {
            SDGLog.d(TAG, "disable foreground dispatch for NFC");
            mNfcAdapter.disableForegroundDispatch(this);
        }
    }

    @Override
    protected void onStop() {
        SDGLog.d(TAG, "onStop()");
        super.onStop();
    }

    BroadcastReceiver wifiConnected, wifiDisconnected;

    private void unregisterReceivers() {
        unregisterReceiver(wifiConnected);
        unregisterReceiver(wifiDisconnected);
    }

    private void registerReceivers() {

        wifiConnected = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                AlertDialog.Builder builder = new AlertDialog.Builder(ScanningActivity.this);
                builder.setMessage("Network connectivity has been restored. Any pending images will be uploaded")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.dismiss();
                                ImageFileUploader.sendAllPendingImages(getApplicationContext());
                                ImageMetaDataUploader.sendAllPendingMetaDataObjects(getApplicationContext());

                                Intent updateIntent = new Intent(getApplicationContext().getResources().getString(R.string.UPDATE_IMAGE_INTENT));
                                getApplicationContext().sendBroadcast(updateIntent);

                            }
                        }).show();
            }
        };

        wifiDisconnected = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                AlertDialog.Builder builder = new AlertDialog.Builder(ScanningActivity.this);
                builder.setMessage("Network connectivity has been lost. Images will not be uploaded until connectivity has been restored")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.dismiss();
                            }
                        }).show();
            }
        };

        registerReceiver(wifiConnected, new IntentFilter(Constants.ACTION_WIFI_CONNECTED));
        registerReceiver(wifiDisconnected, new IntentFilter(Constants.ACTION_WIFI_DISCONNECTED));
    }

    @Override
    protected void onStart() {
        SDGLog.d(TAG, "onStart()");
        super.onStart();
    }

    protected void updateChips(final int cloudLayout, final String backgroundColor,
                               final String textColor, final List<String> chipList,
                               final String selected,
                               final IDeleteElementCallback deleteCallback) {
        SDGLog.d(TAG, "update chips: " + chipList);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                final ChipCloudConfig config = new ChipCloudConfig()
                        .selectMode(ChipCloud.SelectMode.none)
                        .uncheckedChipColor(Color.parseColor(backgroundColor))
                        .checkedChipColor(Color.parseColor(backgroundColor) | 0xFF000000)
                        .uncheckedTextColor(Color.parseColor(textColor))
                        .useInsetPadding(true);

                if (deleteCallback != null) config.showClose(Color.parseColor(textColor));

                FlexboxLayout flexBox = (FlexboxLayout) findViewById(cloudLayout);
                flexBox.removeAllViews();

                final ChipCloud chipCloud = new ChipCloud(ScanningActivity.this, flexBox, config);

                chipCloud.setListener(new ChipListener() {
                    @Override
                    public void chipCheckedChange(int i, boolean b, boolean b1) {
                        SDGLog.d(TAG, i + " " + b + " " + b1);
                        if(chipList != null) {
                            SDGLog.d(TAG, "chip was " + chipList.get(i).toString());
                        }
                    }
                });

                chipCloud.setDeleteListener(new ChipDeletedListener() {
                    @Override
                    public void chipDeleted(int i, String s) {
                        SDGLog.d(TAG, i + " " + s);
                        if (chipList != null) {
                            SDGLog.d(TAG, "chip was " + chipList.get(i).toString());
                            if (i < chipList.size() && chipList.get(i) != null && chipList.get(i).toString().equals(s)) {
                                SDGLog.d(TAG, "deleting tag");
                                deleteCallback.delete(s);
                            } else {
                                SDGLog.d(TAG, "not deleting tag...: " + chipList.size() + " " + chipList.get(i).toString());
                            }
                        }
                    }
                });


                int i = 0;
                for(String chip : chipList) {
                    chipCloud.addChip(chip);
                    if (chip.equals(selected)) {
                        chipCloud.setChecked(i);
                    }
                    i++;
                }
            }
        });
    }

    @Override
    public void onResume() {
        SDGLog.d(TAG, "onResume()");
        super.onResume();

        findViewById(Window.ID_ANDROID_CONTENT).getViewTreeObserver().addOnGlobalLayoutListener(mLayoutKeyboardVisibilityListener);

        //Set that interaction timer right away
        lastInteractionTimestamp = new Date().getTime();

        registerReceivers();
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);

        if(cameraScanning) {
            SDGLog.d(TAG, "we were camera scanning setting it to false");
            cameraScanning = false;
        }
        //closeScanner();
        SDGLog.d(TAG, "onResume: Calling openScanner()");
        openScanner();

        if(mNfcAdapter != null) {
            SDGLog.d(TAG, "enable foreground dispatch for NFC");
            mNfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
        }
    }

    public void openScanner() {
        SDGLog.e(TAG, "openScanner()");
        IScanner scanner = LocalScanManager.getInstance(this);
        if (scanner != null) {
            SDGLog.d(TAG, "openScanner: Opening " + scanner);
            scanner.open();
        }
    }

    public void closeScanner() {
        SDGLog.d(TAG, "closeScanner()");
        IScanner scanner = LocalScanManager.getInstance(this);
        if (scanner != null) {
            SDGLog.d(TAG, "closeScanner: Closing " + scanner);
            scanner.close();
        }
    }

    protected void triggerScanning(boolean closeOnScan) {
        if (LocalScanManager.getInstance(this).getScannerType().equals(SCANNER_TYPE_RFID)) {
            displayScanOverlay(this, true);
        }
        LocalScanManager.getInstance(getApplicationContext()).triggerScanning(closeOnScan);
    }

    protected void displayScanOverlay(final Context guiContext, final boolean shouldDisplay) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(shouldDisplay) {
                    scanningOverlay = new ProgressDialog(guiContext);
                    //dialog.setIndeterminate(true);
                    scanningOverlay.setCancelable(false);
                    scanningOverlay.setTitle("Scanning for RFID tag...");
                    scanningOverlay.getWindow().setBackgroundDrawable(new ColorDrawable(Color.parseColor("#44FFFFFF")));
                    scanningOverlay.setButton(ProgressDialog.BUTTON_NEGATIVE, "Cancel Scan", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.dismiss();
                            LocalScanManager.getInstance(getApplicationContext()).cancelScanning();
                        }
                    });

                    scanningOverlay.show();
                } else if(scanningOverlay != null && scanningOverlay.isShowing()){
                    scanningOverlay.dismiss();
                }
            }
        });
    }

    public boolean isLoggedIn() {
        return GenericPhotoApplication.getInstance().getBearerToken() != null || BuildConfig.FLAVOR.contains("localNoAuth");
    }

    ILoginCallback loginCallback;

    protected void setLoginCallback(ILoginCallback callback) {
        loginCallback = callback;
    }

    protected void displayLoginDialog() {
        if (loginDialogFragment == null) {
            loginDialogFragment = new LoginDialogFragment();
        }

        Intent intent = new Intent(this, LoginActivity.class);
       // intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);

        //if (!loginDialogFragment.isShowing()) {
        //    loginDialogFragment.show(getSupportFragmentManager(), "LoginDialog");
        //}
    }

    protected void displayAboutDialog() {
        aboutDialogFragment = new AboutDialogFragment();
        aboutDialogFragment.show(getSupportFragmentManager(), "AboutDialog");
    }

    protected void displayOrgSelectionDialog() {
        orgSelectionDialogFragment = new OrganizationSelectionDialogFragment();
        orgSelectionDialogFragment.setCallback(() -> {
            Toast.makeText(ScanningActivity.this, "Selected org: " +
                    GenericPhotoApplication.getInstance().getCurrentOrg().name, Toast.LENGTH_SHORT).show();
            updateImageList();
        });
        orgSelectionDialogFragment.show(getSupportFragmentManager(), "OrgSelectDialog");
    }

    // Default implementation does nothing, but subclasses can override.
    protected void updateImageList() {
    }

    protected void displaySiteSelectionDialog() {
        // TODO: Remove this line when the implementation is complete, because right now it's broken
        if (true) return;
        siteSelectionDialogFragment = new SiteSelectionDialogFragment();
        siteSelectionDialogFragment.setCallback(new ThumbnailListActivity.ISiteSelect() {
            @Override
            public void siteSelected() {
                if(GenericPhotoApplication.getInstance().getCurrentSite() != null) {
                    Toast.makeText(ScanningActivity.this, "Selected site: " +
                            GenericPhotoApplication.getInstance().getCurrentSite().name, Toast.LENGTH_SHORT).show();
                }
                updateImageList();
            }
        });
        siteSelectionDialogFragment.show(getSupportFragmentManager(), "SiteSelectDialog");
    }

    protected void performLogout() {
        //TODO: if !prefs.savePassword, delete password pref
        final AppDatabase db = GenericPhotoApplication.getInstance().getDb();
        List<ImageFile> unsentImages = db.imageFileDao().getUnsent();
        if (unsentImages.size() > 0) {
            String message = "";
            if (unsentImages.size() == 1) {
                message = "One image has not been uploaded. ";
            } else {
                message = "" + unsentImages.size() + " images have not been uploaded. ";
            }
            message += "Logging out will discard these images. Continue?";
            androidx.appcompat.app.AlertDialog.Builder b =
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Log out?")
                    .setMessage(message)
                    .setPositiveButton("Log out", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            completeLogout(db);
                        }
                    })
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            b.create().show();
            return;
        }
        completeLogout(db);
    }

    private void completeLogout(AppDatabase db) {
        db.imageDao().deleteAll();
        db.imageFileDao().deleteAll();
        setImageList(new ArrayList<Image>());
        GenericPhotoApplication.getInstance().setBearerToken(null);
        displayLoginDialog();
    }

    protected void setImageList(List<Image> images) {
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String action = intent.getAction();
        SDGLog.d(TAG, this + ": Got new intent: " + intent);
        Boolean useNFC = sharedPrefs.getBoolean("PREF_SCAN_TAGS_WITH_NFC", true);
        if (action == null || !useNFC) return;
        if (ACTION_NDEF_DISCOVERED.equals(action) || ACTION_TECH_DISCOVERED.equals(action)
                || ACTION_TAG_DISCOVERED.equals(action)) {
            SDGLog.d(TAG, "NFC tag discovered...");
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            if (tag != null) {
                byte[] tagId = tag.getId();
                StringBuilder hexdump = new StringBuilder();
                for (byte b : tagId) {
                    hexdump.append(String.format("%02x", (int) b & 0xff));
                }
                SDGLog.d(TAG, "Scanned " + hexdump);
                LocalScanManager.dataScanned(hexdump.toString(), Constants.ScannerType.SCANNER_TYPE_NFC);
            } else {
                SDGLog.e(TAG, "EXTRA_TAG not set!");
            }
        }
    }

    protected boolean isSingleTagMode() {
        return sharedPrefs.getBoolean(Constants.PREF_SINGLE_TAG_SCAN, false);
    }

    protected boolean singleTagRemovesAll() {
        return sharedPrefs.getString(Constants.PREF_SINGLE_TAG_MODE, Constants.SINGLE_TAG_SCANNED_ONLY).equals(Constants.SINGLE_TAG_ALL_TAGS);
    }

    protected String getLastTagScanned() {
        if (isSingleTagMode())
            return sharedPrefs.getString(Constants.PREF_LAST_TAG_SCANNED, "");
        else
            return "";
    }

    protected void setLastTagScanned(String tag) {
        sharedPrefs.edit().putString(Constants.PREF_LAST_TAG_SCANNED, tag).apply();
    }

    //Orientation management
    public static final int UPSIDE_DOWN = 2;
    public static final int LANDSCAPE_RIGHT = 3;
    public static final int PORTRAIT = 0;
    public static final int LANDSCAPE_LEFT = 1;
    public int mOrientationDeg; //last rotation in degrees
    public int mOrientationRounded; //last orientation int from above
    private static final int _DATA_X = 0;
    private static final int _DATA_Y = 1;
    private static final int _DATA_Z = 2;
    private static final int ORIENTATION_UNKNOWN = -1;

    int tempOrientRounded = -1;

    protected int getExifOrientationForDeviceRotation(int orientation) {
        if(Build.MANUFACTURER.equals("Zebra Technologies")) {
            // The Zebra TC20 rotates images in the camera driver to match the device orientation,
            // so photos taken with it are always ORIENTATION_NORMAL
            if (Build.MODEL.equals("TC20")) {
                return ExifInterface.ORIENTATION_NORMAL;
            }
        }

        int exifOrientation = ExifInterface.ORIENTATION_UNDEFINED;

        switch(orientation) {
            case PORTRAIT: exifOrientation = ExifInterface.ORIENTATION_NORMAL; break;
            case LANDSCAPE_LEFT: exifOrientation = ExifInterface.ORIENTATION_ROTATE_270; break;
            case UPSIDE_DOWN: exifOrientation = ExifInterface.ORIENTATION_ROTATE_180; break;
            case LANDSCAPE_RIGHT: exifOrientation = ExifInterface.ORIENTATION_ROTATE_90; break;
        }

        SDGLog.d(TAG, "For screen orientation " + orientation + " derived exif orientation " + exifOrientation);

        return exifOrientation;
    }

    @Override
    public void onSensorChanged(SensorEvent event)
    {
        float[] values = event.values;
        int orientation = ORIENTATION_UNKNOWN;
        float X = -values[_DATA_X];
        float Y = -values[_DATA_Y];
        float Z = -values[_DATA_Z];
        float magnitude = X*X + Y*Y;
        // Don't trust the angle if the magnitude is small compared to the y value
        if (magnitude * 4 >= Z*Z) {
            float OneEightyOverPi = 57.29577957855f;
            float angle = (float)Math.atan2(-Y, X) * OneEightyOverPi;
            orientation = 90 - (int)Math.round(angle);
            // normalize to 0 - 359 range
            while (orientation >= 360) {
                orientation -= 360;
            }
            while (orientation < 0) {
                orientation += 360;
            }
        }
        //^^ thanks to google for that code
        //now we must figure out which orientation based on the degrees
        if (orientation != mOrientationDeg)
        {
            mOrientationDeg = orientation;
            //figure out actual orientation
            if(orientation == ORIENTATION_UNKNOWN){//basically flat

            }
            else if(orientation <= 45 || orientation > 315){//round to 0
                tempOrientRounded = PORTRAIT;//portrait
            }
            else if(orientation > 45 && orientation <= 135){//round to 90
                tempOrientRounded = LANDSCAPE_RIGHT; //lsleft
            }
            else if(orientation > 135 && orientation <= 225){//round to 180
                tempOrientRounded = UPSIDE_DOWN; //upside down
            }
            else if(orientation > 225 && orientation <= 315){//round to 270
                tempOrientRounded = LANDSCAPE_LEFT;//lsright
            }

        }

        if(mOrientationRounded != tempOrientRounded){
            //Orientation changed, handle the change here
            mOrientationRounded = tempOrientRounded;
            SDGLog.d("Orientation", ""+mOrientationRounded);
            orientationChanged();
        }
    }

    protected void orientationChanged() {

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    void configureScanButton(FloatingActionButton scanButton) {
        runOnUiThread(() -> {
            if (!LocalScanManager.getInstance().isScannerReady()) {
                scanButton.hide();
                return;
            }
            Constants.ScannerType type = LocalScanManager.getInstance().getScannerType();
            if (type == SCANNER_TYPE_RFID) {
                SDGLog.d(TAG, "non-barcode scanner connected");
                scanButton.setImageResource(R.drawable.ic_rfid_scan);
                scanButton.show();
            } else if (type == SCANNER_TYPE_BARCODE) {
                SDGLog.d(TAG, "barcode scanner connected");
                scanButton.setImageResource(R.drawable.ic_barcode_black_24dp);
                scanButton.show();
            } else {
                SDGLog.e(TAG, "scannerConnected: Unsupported scanner type " + type);
            }
        });
    }

    void configureScanButton(ExtendedFloatingActionButton scanButton) {
        runOnUiThread(() -> {
            if (!LocalScanManager.getInstance().isScannerReady()) {
                scanButton.hide();
                return;
            }
            Constants.ScannerType type = LocalScanManager.getInstance().getScannerType();
            if (type == SCANNER_TYPE_RFID) {
                SDGLog.d(TAG, "non-barcode scanner connected");
                scanButton.setIconResource(R.drawable.ic_rfid_scan);
                scanButton.show();
            } else if (type == SCANNER_TYPE_BARCODE) {
                SDGLog.d(TAG, "barcode scanner connected");
                scanButton.setIconResource(R.drawable.ic_barcode_black_24dp);
                scanButton.show();
            } else {
                SDGLog.e(TAG, "scannerConnected: Unsupported scanner type " + type);
            }
        });
    }


    // Code below is based oon https://github.com/aurelhubert/ahbottomnavigation/issues/171
    // but only works if contennt is resized to show keyboard
    public boolean isKeyboardVisible() {
        final Rect rectangle = new Rect();
        final View contentView = findViewById(Window.ID_ANDROID_CONTENT);
        contentView.getWindowVisibleDisplayFrame(rectangle);
        int screenHeight = contentView.getRootView().getHeight();

        // r.bottom is the position above soft keypad or device button.
        // If keypad is shown, the rectangle.bottom is smaller than that before.
        int keypadHeight = screenHeight - rectangle.bottom;
        // 0.15 ratio is perhaps enough to determine keypad height.
        return keypadHeight > screenHeight * 0.15;
    }

    private boolean mKeyboardVisible = false;

    private OnGlobalLayoutListener mLayoutKeyboardVisibilityListener = () -> {

        boolean isKeyboardNowVisible = isKeyboardVisible();
        if (mKeyboardVisible != isKeyboardNowVisible) {
            if (isKeyboardNowVisible) {
                onKeyboardShown();
            } else {
                onKeyboardHidden();
            }
        }
        mKeyboardVisible = isKeyboardNowVisible;
    };

    /** Called when the soft keyboard is being shown. */
    protected void onKeyboardShown() {
        SDGLog.d(TAG, "Keyboard shown");
    }

    /** Called when the soft keyboard is hidden. */
    protected void onKeyboardHidden() {
        SDGLog.d(TAG, "Keyboard hidden");
    }
}

