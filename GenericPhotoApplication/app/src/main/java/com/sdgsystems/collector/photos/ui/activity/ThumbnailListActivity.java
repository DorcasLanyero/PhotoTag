package com.sdgsystems.collector.photos.ui.activity;

import static com.sdgsystems.collector.photos.Constants.ScannerType.SCANNER_TYPE_BARCODE;
import static com.sdgsystems.collector.photos.Constants.ScannerType.SCANNER_TYPE_RFID;
import static com.sdgsystems.collector.photos.Utilities.startTopLevelActivity;
import static com.sdgsystems.idengine.internal.Debug.debug;

import android.Manifest;
import android.app.Activity;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.core.view.MenuItemCompat;
import androidx.lifecycle.Lifecycle;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sdgsystems.blueloggerclient.SDGLog;
import com.sdgsystems.collector.photos.BuildConfig;
import com.sdgsystems.collector.photos.Constants;
import com.sdgsystems.collector.photos.GenericPhotoApplication;
import com.sdgsystems.collector.photos.R;
import com.sdgsystems.collector.photos.Utilities;
import com.sdgsystems.collector.photos.data.adapters.PhotoServiceListAdapter;
import com.sdgsystems.collector.photos.data.dao.ImageDao;
import com.sdgsystems.collector.photos.data.dao.ImageFileDao;
import com.sdgsystems.collector.photos.data.model.Image;
import com.sdgsystems.collector.photos.data.model.ImageCategory;
import com.sdgsystems.collector.photos.data.model.ImageFile;
import com.sdgsystems.collector.photos.data.model.Organization;
import com.sdgsystems.collector.photos.data.model.Site;
import com.sdgsystems.collector.photos.scanning.GenericScanningCallback;
import com.sdgsystems.collector.photos.scanning.LocalScanManager;
import com.sdgsystems.collector.photos.sync.ImageFileUploader;
import com.sdgsystems.collector.photos.sync.ImageLoaderRequestQueue;
import com.sdgsystems.collector.photos.sync.ImageMetaDataUploader;
import com.sdgsystems.collector.photos.sync.NetworkRequestHandler;
import com.sdgsystems.collector.photos.sync.authorizedVolleyRequests.AuthorizedJsonArrayRequest;
import com.sdgsystems.collector.photos.ui.view.EndlessScrollListener;
import com.sdgsystems.synchableapplication.SynchableConstants;
import com.sdgsystems.synchableapplication.Synchronizer;
import com.sdgsystems.synchableapplication.callbacks.IUploadCallback;

import org.json.JSONArray;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;


public class ThumbnailListActivity extends ScanningActivity {
    public static final String URL_ROOT = "com.sdgsystems.EXTRA_URL_ROOT";
    private static final String TAG = "MainActivity";
    private static final String SEARCH_TAG = "MainActivitySearch";
    private static final String PREF_OVERWRITE_TAGS = "PREF_OVERWRITE_TAGS";

    boolean useExecutor = true;
    ImageUpdateIntentReceiver myReceiver = null;
    Boolean myReceiverIsRegistered = false;
    int currentPage = 0;
    EndlessScrollListener scrollListener;
    private PhotoServiceListAdapter mAdapter;
    private String query = null;
    private String lastScan = null;
    private SearchView searchView;
    private Menu mOptionsMenu;

    public static ThumbnailListActivity ctxt;
    private FloatingActionButton scanButton;
    GenericScanningCallback mScanningCallback = new GenericScanningCallback() {
        @Override
        public void scanAvailable(final String scanData, Constants.ScannerType scannerType) {
            Log.d(TAG, "scanAvailable(" + scanData + ", " + scannerType);
            if (Utilities.shouldIgnoreScan(ThumbnailListActivity.this, scanData, scannerType))
                return;

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    lastScan = scanData;
                    displayScanOverlay(ThumbnailListActivity.this, false);
                    Log.d(SEARCH_TAG, ": " + scanData);
                    triggerSearch(scanData);
                }
            });
        }

        @Override
        public void statusMessage(String message, boolean error) {
            Toast.makeText(ThumbnailListActivity.this, message, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void cameraScanningStarted() {
            cameraScanning = true;
        }

        @Override
        public void scanningComplete() {
            cameraScanning = false;
            displayScanOverlay(ThumbnailListActivity.this, false);
        }

        @Override
        public void scannerClosed() {
            runOnUiThread(() -> {
                scanButton.hide();
            });
        }

        @Override
        public void scannerConnected() {
            Log.d(TAG, "scanner connected");

            runOnUiThread(() -> {
                Constants.ScannerType type = LocalScanManager.getInstance().getScannerType();
                if (type == SCANNER_TYPE_RFID) {
                    Log.d(TAG, "non-barcode scanner connected");
                    scanButton.setImageResource(R.drawable.ic_rfid_scan);
                    scanButton.show();
                } else if (type == SCANNER_TYPE_BARCODE) {
                    Log.d(TAG, "barcode scanner connected");
                    scanButton.setImageResource(R.drawable.ic_barcode);
                    scanButton.show();
                } else {
                    Log.e(TAG, "scannerConnected: Unsupported scanner type " + type);
                }
            });
        }

        @Override
        public boolean shouldUseCameraScanning() {
            return false;
        }
    };
    private boolean offlineOnly = false;
    private BottomNavigationView bottomNavigationView;

    public static File createImageFile(Context context) throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        return image;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_thumbnails);

        mAdapter = new PhotoServiceListAdapter(this, GenericPhotoApplication.getInstance().getImageApiUrl());
        final AbsListView imageCollectionView = (AbsListView) findViewById(R.id.image_collection_view);
        imageCollectionView.setAdapter(mAdapter);

        scrollListener = new EndlessScrollListener() {
            @Override
            public boolean onLoadMore(int page, int totalItemsCount) {

                SDGLog.d(TAG, "Scrolling, on load more.  page : " + page);

                currentPage = page;
                updateImageList();

                return true;
            }
        };

        imageCollectionView.setOnScrollListener(scrollListener);

        //Configure the delete mode behavior
        configureDeleteModeBehavior(imageCollectionView);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }

        SDGLog.d(TAG, "Setting item click and long click listener...");

        imageCollectionView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long id) {

                SDGLog.d(TAG, "Long press of item at position: " + position);

                AbsListView v = (AbsListView) adapterView;
                if (v.getChoiceMode() == AbsListView.CHOICE_MODE_NONE) {

                    SDGLog.d(TAG, "setting item to checked");

                    v.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE_MODAL);
                    v.setItemChecked(position, true);
                }

                return true;
            }
        });

        imageCollectionView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                SDGLog.d(TAG, "Item clicked");
                Image image = (Image) mAdapter.getItem(i);
                Intent detail = new Intent(ThumbnailListActivity.this, PhotoDetailActivity.class);
                detail.putExtra(PhotoDetailActivity.IMAGE_DATABASE_ID, image.getUid());
                detail.putExtra(PhotoDetailActivity.IMAGE_MONGO_ID, image.mongo_id);
                startActivity(detail);
            }
        });

        scanButton = (FloatingActionButton) findViewById(R.id.scan);
        scanButton.setOnClickListener(v -> {
            if (imageCollectionView.getCheckedItemCount() < 1) {
                triggerScanning(true);
            } else {
                SparseBooleanArray items = imageCollectionView.getCheckedItemPositions();

                final List<Image> images = new ArrayList<>(imageCollectionView.getCheckedItemCount());
                for (int i = 0; i < mAdapter.getCount(); i++) {
                    if (items.get(i)) {
                        images.add((Image) mAdapter.getItem(i));
                    }
                }
                deleteImages(images);
            }
        });

        bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ThumbnailListActivity.this);

                SDGLog.d(TAG, "Clicked " + item.getTitle());
                int prevSelectedId = bottomNavigationView.getSelectedItemId();
                int id = item.getItemId();
                if (id == prevSelectedId) {
                    debug(TAG, item.getTitle() + " is already selected");
                    return false;
                }

                if (lastScan != null && prefs.getBoolean(PREF_OVERWRITE_TAGS, false)) {
                    ArrayList<String> tags = new ArrayList<>();   //(PreferenceManager.getDefaultSharedPreferences(this).getStringSet(ImageSetupActivity.PREF_DEFAULT_TAGS, new HashSet<String>()));
                    if (lastScan != null && !lastScan.isEmpty()) {
                        tags.add(lastScan);
                        if(isSingleTagMode()) {
                            setLastTagScanned(lastScan);
                        }
                        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit();
                        editor.putStringSet(ImageSetupActivity.PREF_DEFAULT_TAGS, new HashSet<String>(tags));
                        editor.commit();
                    }
                }
                //todo - bottom nav bar button  onclicks.
                if (id == R.id.action_list) {
                } else if (id == R.id.action_camera) {
                    Utilities.captureImages(ThumbnailListActivity.this);
                } else if (id == R.id.action_tags) {
                    startTopLevelActivity(ThumbnailListActivity.this, ImageSetupActivity.class);
                }
                return true;
            }
        });
        bottomNavigationView.setSelectedItemId(R.id.action_list);

        onNewIntent(getIntent());

        myReceiver = new ImageUpdateIntentReceiver();

        SwipeRefreshLayout refreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipeRefresh);

        if (refreshLayout != null) {
            refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    updateImageList();
                }
            });
        }
        ctxt = ThumbnailListActivity.this;

        showBackButton(false);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if (getIntent().getStringExtra(SearchManager.QUERY) != null) {
            // Since ThumbnailListActivity's launchMode is "singleTask", if we were launched with
            // an ACTION_SEARCH Intent, then there's no main activity to go back to, so we
            // launch the activity again with the "clear task" option to simulate going back to
            // the previous screen.
            startTopLevelActivity(this, ThumbnailListActivity.class);
        } else {
            finish();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG, "got permission for request " + requestCode + " " + permissions[0] + " " + grantResults[0]);
    }

    @Override
    public void onLoginDialogPositiveClick() {

    }

    @Override
    public void onLoginDialogNegativeClick() {

    }

    public void performLogin() {
        final boolean debugLogin = BuildConfig.FLAVOR.contains("localNoAuth");

        // We're not logged in, so we must do so before populating the adapter.
        NetworkRequestHandler.LoginRequestWithCallback(
                new NetworkRequestHandler.ILoginRequestCallback() {
                    @Override
                    public void loginSucceeded() {
                        Log.d(TAG, "Login succeeded");
                        handleConnection(true);
                    }

                    @Override
                    public void loginFailed(int statusCode, String errorMsg) {
                        Log.d(TAG, "Login failed");

                        if (debugLogin) {
                            Log.d(TAG, "DEBUG LOGIN: continuing anyway");
                            handleConnection(true);
                        }
                        String generalLogInError = "Login to \" + GenericPhotoApplication.getInstance().getAuthUrl() + \" failed: \" + errorMsg + \" \" + statusCode";
                        if (errorMsg.contains("invalid credentials")) {
                            generalLogInError = "Invalid Username or Password";
                        } else if (errorMsg.contains("rate limited")) {
                            generalLogInError = "Too Many Recent Attempts, Please Wait before attempting Again";
                        } else if (errorMsg.contains("Handshake failed")) {
                            generalLogInError = "Invalid Subdomain";
                        } else if (errorMsg.contains("account locked")) {
                            generalLogInError = "There has been too many Failed Attempts for this account";
                        }
                        Toast.makeText(ThumbnailListActivity.this, generalLogInError, Toast.LENGTH_LONG).show();

                        GenericPhotoApplication.getInstance().setBearerToken(null);
                        handleConnection(false);

                        // Only show the dialog if we're running
                        if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
                            // Redisplay login dialog if logging in fails
                            displayLoginDialog();
                        }
                    }
                }, getApplicationContext());
    }

    private void handleConnection(boolean connected) {

        boolean debugOrgs = false;
        boolean debugSites = false;

        if (connected) {

            if (debugOrgs) {

                ArrayList<Organization> debugOrgList = new ArrayList<>();

                for (int index = 0; index < 3; index++) {
                    Organization debugOrg = new Organization();
                    debugOrg.name = "debug : " + index;
                    debugOrg.id = "index" + index;
                    debugOrgList.add(debugOrg);
                }
                updateOrganizationList(debugOrgList);


            } else {
                updateAvailableOrganizations();

            }

            if (debugSites) {
                ArrayList<Site> debugSiteList = new ArrayList<>();

                for (int index = 0; index < 3; index++) {
                    Site debugSite = new Site();
                    debugSite.name = "debug : " + index;
                    debugSite.id = "index" + index;
                    debugSiteList.add(debugSite);
                }

                updateSiteList(debugSiteList);
            } else {
                updateAvailableSites();
            }

            if (GenericPhotoApplication.getInstance().categories.isEmpty()) {
                updateCategories();
            }

            //Reset all objects with a status of 'uploading' to 'not uploaded' and resend
            markUnsentDatabaseObjects();

        } else {
            //This will be CLEARING the image list
            updateImageList();
        }
    }

    private void markUnsentDatabaseObjects() {
        ImageFileDao imageFileDao = GenericPhotoApplication.getInstance().getDb().imageFileDao();
        ImageDao imageDao = GenericPhotoApplication.getInstance().getDb().imageDao();

        List<ImageFile> unsentFiles = imageFileDao.getUnsent();
        List<ImageFile> updateFiles = new ArrayList<>();
        List<Image> unsentImages = imageDao.getUnsent();
        List<Image> updateImages = new ArrayList<>();

        for (ImageFile imageFile : unsentFiles) {
            if (imageFile.getUploadStatus().equals(SynchableConstants.UPLOAD_STATUS_UPLOADING)) {
                imageFile.setUploadStatus(SynchableConstants.UPLOAD_STATUS_NOT_UPLOADED);
                updateFiles.add(imageFile);
            }
        }

        for (Image image : unsentImages) {
            if (image.getUploadStatus().equals(SynchableConstants.UPLOAD_STATUS_UPLOADING)) {
                image.setUploadStatus(SynchableConstants.UPLOAD_STATUS_NOT_UPLOADED);
                updateImages.add(image);
            }
        }

        if (updateFiles.size() > 0) {
            Log.d(TAG, "Marking " + updateFiles.size() + " image files as " + SynchableConstants.UPLOAD_STATUS_NOT_UPLOADED);
            imageFileDao.update(updateFiles);
        }

        if (updateImages.size() > 0) {
            Log.d(TAG, "Marking " + updateImages.size() + " images as " + SynchableConstants.UPLOAD_STATUS_NOT_UPLOADED);
            imageDao.update(updateImages);
        }
    }

    private void updateCategories() {
        AuthorizedJsonArrayRequest request = new AuthorizedJsonArrayRequest(GenericPhotoApplication.getInstance().getImageCategoryUrl(), new Response.Listener<JSONArray>() {
            @Override
            public void onResponse(JSONArray response) {
                Log.d(SEARCH_TAG, "Response: " + response.toString());
                Type categoryListType = new TypeToken<ArrayList<ImageCategory>>() {
                }.getType();

                Gson gson = ImageCategory.getGson();
                List<ImageCategory> imageCategories = gson.fromJson(response.toString(), categoryListType);

                Log.d(SEARCH_TAG, "Pulled remote categories: " + imageCategories);

                List<ImageCategory> filteredImageCategories = new ArrayList<ImageCategory>();

                for (ImageCategory category : imageCategories) {
                    if (!category.hidden) {
                        filteredImageCategories.add(category);
                    }
                }

                GenericPhotoApplication.getInstance().categories = (ArrayList<ImageCategory>) filteredImageCategories;
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.d(TAG, "Category Retrieval Error: " + error.getMessage());
                Toast.makeText(ThumbnailListActivity.this, "Failed to get image categories", Toast.LENGTH_SHORT).show();
            }
        });

        RequestQueue q = ImageLoaderRequestQueue.getInstance(getApplicationContext()).getRequestQueue();
        q.add(request);
    }

    private void updateAvailableOrganizations() {
        AuthorizedJsonArrayRequest request = new AuthorizedJsonArrayRequest(GenericPhotoApplication.getInstance().getOrganizationsUrl(), new Response.Listener<JSONArray>() {
            @Override
            public void onResponse(JSONArray response) {
                Log.d(SEARCH_TAG, "Response: " + response.toString());
                Type organzationListType = new TypeToken<ArrayList<Organization>>() {
                }.getType();

                Gson gson = Organization.getGson();
                final List<Organization> availableOrganizations = gson.fromJson(response.toString(), organzationListType);

                updateOrganizationList(availableOrganizations);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.d(TAG, "Organization Retrieval Error: " + error.getMessage());

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
                Log.d(SEARCH_TAG, "Response: " + response.toString());
                Type siteListType = new TypeToken<ArrayList<Site>>() {
                }.getType();

                Gson gson = Site.getGson();
                final List<Site> availableSites = gson.fromJson(response.toString(), siteListType);

                updateSiteList(availableSites);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.d(TAG, "Site Retrieval Error: " + error.getMessage());

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
        Log.d(SEARCH_TAG, "Pulled available organizations: " + availableOrganizations);

        GenericPhotoApplication.getInstance().organizations = (ArrayList<Organization>) availableOrganizations;

        if (availableOrganizations.size() > 0) {
            GenericPhotoApplication.getInstance().setCurrentOrg(availableOrganizations.get(0));
        }

        mOptionsMenu.findItem(R.id.switchOrg).setVisible(availableOrganizations.size() > 1);

        updateImageList();
    }

    private void updateSiteList(List<Site> availableSites) {
        Log.d(SEARCH_TAG, "Pulled available sites: " + availableSites);

        GenericPhotoApplication.getInstance().sites = (ArrayList<Site>) availableSites;

        if (availableSites.size() > 0) {

            //If there is no currently set 'selected' site, display the site selection dialog
            Site currentSite = GenericPhotoApplication.getInstance().getCurrentSite();
            if (currentSite == null) {
                displaySiteSelectionDialog();
            }
        }

        mOptionsMenu.findItem(R.id.switchSite).setVisible(availableSites.size() > 1);

        updateImageList();
    }

    private void configureDeleteModeBehavior(final AbsListView imageCollectionView) {
        imageCollectionView.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {
            @Override
            public void onItemCheckedStateChanged(ActionMode actionMode, int item, long id, boolean checked) {
            }

            @Override
            public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
                getMenuInflater().inflate(R.menu.menu_multiselect, menu);
                scanButton.setImageResource(R.drawable.ic_delete_white_24dp);
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {

                SparseBooleanArray items = imageCollectionView.getCheckedItemPositions();

                final List<Image> images = new ArrayList<>(imageCollectionView.getCheckedItemCount());
                for (int i = 0; i < mAdapter.getCount(); i++) {
                    if (items.get(i)) {
                        images.add((Image) mAdapter.getItem(i));
                    }
                }

                if (menuItem.getItemId() == R.id.delete) {
                    deleteImages(images);
                } else if (menuItem.getItemId() == R.id.share) {
                    String message = "New Photos from PhotoTag:\n";

                    for (Image sharedImage : images) {
                        if (sharedImage.mongo_id != null) {
                            message += GenericPhotoApplication.getInstance().getWebAppImageUrl() + sharedImage.mongo_id + "\n";
                        }
                    }

                    Intent sendIntent = new Intent();
                    sendIntent.setAction(Intent.ACTION_SEND);
                    sendIntent.putExtra(Intent.EXTRA_TEXT, message);
                    sendIntent.setType("text/plain");
                    startActivity(sendIntent);
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode actionMode) {
                SDGLog.d(TAG, "Left mode.");
                scanButton.setImageResource(R.drawable.ic_barcode);
            }

        });
    }

    private void deleteImages(List<Image> images) {
        AlertDialog.Builder b = new AlertDialog.Builder(ThumbnailListActivity.this);
        b.setCancelable(true);
        b.setTitle("Delete photos?");
        b.setMessage("This operation will permanently delete " + images.size() + " photos. This operation cannot be undone. Continue?");
        b.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            volatile int deletions = 0;

            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                ImageDao dao = GenericPhotoApplication.getInstance().getDb().imageDao();
                ImageFileDao fileDao = GenericPhotoApplication.getInstance().getDb().imageFileDao();
                for (Image image : images) {
                    image.deleteLocalImage(dao, fileDao);

                    updateImageList();

                    if (image.mongo_id != null) {
                        deleteRemoteImage(image);
                    }
                }
            }

            private void deleteRemoteImage(Image image) {
                Synchronizer.sendString(getApplicationContext(),
                        SynchableConstants.UPLOAD_METHOD.UPLOAD_METHOD_DELETE,
                        GenericPhotoApplication.getInstance().getImageApiUrl() + image.mongo_id,
                        "", new IUploadCallback() {

                            @Override
                            public void OnSuccess(String s) {
                                synchronized (this) {
                                    deletions++;
                                    Log.d(TAG, "Deletions: " + deletions + "/" + images.size());
                                    if (deletions >= images.size()) {

                                        Log.d(TAG, "Refreshing photo metalist after deletion");

                                        updateImageList();
                                    }
                                }
                            }

                            @Override
                            public void OnFailure(String s) {
                                synchronized (this) {
                                    deletions++;
                                    Log.d(TAG, "Deletions: " + deletions + "/" + images.size());
                                    if (deletions >= images.size()) {

                                        Log.d(TAG, "Refreshing photo metalist after deletion failure");

                                        updateImageList();
                                    }
                                }
                            }

                            @Override
                            public HashMap<String, String> getHeaders() {
                                return GenericPhotoApplication.getInstance().getAuthHeaders();
                            }

                            @Override
                            public SynchableConstants.BODY_TYPE getBodyType() {
                                return SynchableConstants.BODY_TYPE.STRING;
                            }
                        });
            }
        });
        b.setNegativeButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });
        b.create().show();

    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        String action = intent.getAction() == null ? "" : intent.getAction();
        if ((Intent.ACTION_SEARCH.equals(action) || query != null) && !action.contains("nfc")) {
            SDGLog.d(SEARCH_TAG, "Getting photo list on new intent: " + intent.getAction());
            if (intent.getStringExtra(SearchManager.QUERY) != null) {
                query = intent.getStringExtra(SearchManager.QUERY);
            }
            SDGLog.d(SEARCH_TAG, "Searching for " + query);
            updateImageList();
        } else {
            super.onNewIntent(intent);
        }
    }

    protected void updateImageList() {
        SDGLog.d(SEARCH_TAG, "Updating image list");

        String tagQuery = null;
        String categoryQuery = null;

        if (query != null) {

            SDGLog.d(SEARCH_TAG, "Query wasn't null: " + query);

            for (ImageCategory category : GenericPhotoApplication.getInstance().categories) {
                if (category.name.toLowerCase().equals(query.toLowerCase())) {
                    categoryQuery = category.id;
                    break;
                }
            }

            if (categoryQuery == null) {
                tagQuery = query;
            }

            showBackButton(true);
            getSupportActionBar().setTitle(query);

            EditText et = (EditText) findViewById(R.id.search_src_text);

            if (et != null) {
                Log.d(SEARCH_TAG, "Setting query edit text...");
                et.setText(query);
            }
        } else {
            showBackButton(false);

            String title = getResources().getString(R.string.app_name);

            if (GenericPhotoApplication.getInstance().getCurrentOrg() != null) {
                title += " - " + GenericPhotoApplication.getInstance().getCurrentOrg().name;
            }

            if (GenericPhotoApplication.getInstance().getCurrentSite() != null) {
                getSupportActionBar().setSubtitle(GenericPhotoApplication.getInstance().getCurrentSite().name);
            }

            getSupportActionBar().setTitle(title);
        }

        GetPhotoMetaList getPhotoListAsyncTask = new GetPhotoMetaList();

        if (useExecutor) {
            getPhotoListAsyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, tagQuery, categoryQuery);
        } else {
            getPhotoListAsyncTask.execute(tagQuery, categoryQuery);
        }

        if (isLoggedIn()) {
            ImageFileUploader.sendAllPendingImages(getApplicationContext());
            ImageMetaDataUploader.sendAllPendingMetaDataObjects(getApplicationContext());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        mOptionsMenu = menu;

        // Associate searchable configuration with the SearchView
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        final MenuItem searchItem = menu.findItem(R.id.search);
        if (searchItem != null) {
            searchView = (SearchView) MenuItemCompat.getActionView(searchItem);
            searchView.setSearchableInfo(searchManager.getSearchableInfo(
                    new ComponentName(getApplicationContext(), ThumbnailListActivity.class)));
            MenuItemCompat.setOnActionExpandListener(searchItem,
                    new MenuItemCompat.OnActionExpandListener() {
                        @Override
                        public boolean onMenuItemActionExpand(MenuItem item) {
                            Log.d(SEARCH_TAG, "search exapanded");
                            return true;
                        }

                        @Override
                        public boolean onMenuItemActionCollapse(MenuItem item) {
                            Log.d(SEARCH_TAG, "search collapsed");
                            cancelSearchBar();
                            return true;
                        }
                    });

            // Get the search close button image view
            ImageView closeButton = (ImageView) searchView.findViewById(R.id.search_close_btn);

            // Set on click listener
            closeButton.setOnClickListener(v -> {
                SDGLog.d(SEARCH_TAG, "Search close button clicked");

                //Find EditText view
                EditText et = (EditText) findViewById(R.id.search_src_text);

                //Clear the text from EditText view
                et.setText("");

                cancelSearchBar();

                //Collapse the search widget
                searchItem.collapseActionView();
            });
        }

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return super.onPrepareOptionsMenu(menu);
    }

    private void cancelSearchBar() {
        SDGLog.d(SEARCH_TAG, "cancel searchbar");

        currentPage = 0;
        scrollListener.resetState();

        //Clear query
        searchView.setQuery("", false);
        //Collapse the action view
        searchView.onActionViewCollapsed();

        setTitle(getResources().getString(R.string.app_name));
        query = null;
        updateImageList();

        showBackButton(false);
    }

    private void showBackButton(boolean show) {
        getSupportActionBar().setDisplayHomeAsUpEnabled(show);
        getSupportActionBar().setHomeButtonEnabled(show);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            cancelSearchBar();
        } else if (item.getItemId() == R.id.scan) {
            triggerScanning(true);
        }
        return super.onOptionsItemSelected(item);
    }

    protected void setImageList(List<Image> images) {
        mAdapter.setImages(images);
        mAdapter.setISearchableActivityCallback(new ISearchableActivityCallback() {
            @Override
            public void search(final String query) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        triggerSearch(query);
                    }
                });
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();

        Log.d(TAG, "onPause called");

        if (myReceiverIsRegistered) {
            unregisterReceiver(myReceiver);
            myReceiverIsRegistered = false;
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        configureScanButton(scanButton);

        bottomNavigationView.setSelectedItemId(R.id.action_list);

        Log.d(TAG, "displaying login dialog from create: ");
        if (GenericPhotoApplication.getInstance().getBearerToken() == null) {
            displayLoginDialog();
        }

        GenericPhotoApplication.getInstance().requestLocationUpdates(false);

        if (!myReceiverIsRegistered) {
            registerReceiver(myReceiver, new IntentFilter(getResources().getString(R.string.UPDATE_IMAGE_INTENT)));
            myReceiverIsRegistered = true;
        }

        if (!cameraScanning) {
            updateImageList();
            initScanner(mScanningCallback);
        }

        closeOptionsMenu();
        closeContextMenu();

        if (mOptionsMenu != null) {
            mOptionsMenu.close();
        }
    }

    private void triggerSearch(String searchQuery) {

        //Reset the paging parameter
        currentPage = 0;
        scrollListener.resetState();

        if (searchQuery != null && !searchQuery.equals("null")) {
            Log.d(SEARCH_TAG, "setting query to " + searchQuery);
            query = searchQuery;
            searchView.setQuery(searchQuery, true);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.d(TAG, "Key Event: " + keyCode);
        switch (keyCode) {
            case KeyEvent.KEYCODE_MENU:
            case 104:
                //Got the secondary Trigger
                Utilities.captureImages(ThumbnailListActivity.this);
                return true;
            case KeyEvent.KEYCODE_BACK:
                // TODO: Move this code to onBackPressed()?
                if (searchView.isShown()) {
                    cancelSearchBar();
                    return true;
                } else {
                    return super.onKeyDown(keyCode, event);
                }
            case KeyEvent.KEYCODE_ESCAPE:
                finish();
                break;

        }
        return super.onKeyDown(keyCode, event);
    }

    public interface IOrgSelect {
        public void orgSelected();
    }

    public interface ISiteSelect {
        public void siteSelected();
    }

    public interface IScannerSelect {
        public void scannerSelected(int index);
    }

    private class ImageUpdateIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "got broadcast: " + intent.getAction());

            if (GenericPhotoApplication.getInstance().getBearerToken() == null) {
                displayLoginDialog();
            } else if (intent.getAction().equals(context.getResources().getString(R.string.UPDATE_IMAGE_INTENT))) {
                Log.d(TAG, "updating the image list due to intent");
                updateImageList();
            }
        }
    }

    private class GetPhotoMetaList extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(final String... searchParam) {

            SDGLog.d(SEARCH_TAG, "GetPhotoMetaList");

            RequestQueue q = ImageLoaderRequestQueue.getInstance(getApplicationContext()).getRequestQueue();

            String URL = GenericPhotoApplication.getInstance().getImagesUrl();
            String tagQuery = null;
            String categoryQuery = null;

            if (searchParam.length > 0) {

                if (searchParam[0] != null) {
                    tagQuery = searchParam[0];
                }

                if (searchParam.length > 1 && searchParam[1] != null) {
                    categoryQuery = searchParam[1];
                }
            }

            if (tagQuery != null) {
                URL = Utilities.addURLParam(URL, "tags", tagQuery);
            }

            if (categoryQuery != null) {
                URL = Utilities.addURLParam(URL, "categories", categoryQuery);
            }

            if (PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("PREF_SHOW_HIDDEN_IMAGES", false)) {
                URL = Utilities.addURLParam(URL, "showHidden", "true");
            }

            int limit = 30;
            //Currently, there isn't a great way to not just be 'additive'...
            URL = Utilities.addURLParam(URL, "limit", String.valueOf(limit * (currentPage + 1)));

            //URL = Utilities.addURLParam(URL, "skip", String.valueOf(currentPage-1));

            SDGLog.d(SEARCH_TAG, "Scrolling Query: " + URL + " " + GenericPhotoApplication.getInstance().getAuthHeaders());

            final String finalSearchQuery = tagQuery;

            if (isLoggedIn() && Utilities.isConnected(getApplicationContext()) && !offlineOnly) {

                AuthorizedJsonArrayRequest request = new AuthorizedJsonArrayRequest(URL, new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {

                        writeToFile(response.toString(), getApplicationContext());

                        SDGLog.d(SEARCH_TAG, "Response: " + response.toString());
                        Type photoListType = new TypeToken<ArrayList<Image>>() {
                        }.getType();

                        Gson gson = Image.getGson();
                        final List<Image> images = gson.fromJson(response.toString(), photoListType);

                        SDGLog.d(SEARCH_TAG, "Pulled remote images: " + images);

                        //Add local images that haven't been uploaded yet
                        images.addAll(0, getUnsentImages(finalSearchQuery));

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                setImageList(images);
                            }
                        });


                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        SDGLog.d(TAG, "Error: " + error.getMessage());

                        // no network response at all == no server at the far end
                        // 401 == unauthenticated, so try logging in again.
                        if (error.networkResponse == null || error.networkResponse.statusCode == 401) {
                            NetworkRequestHandler.LoginRequestWithCallback(new NetworkRequestHandler.ILoginRequestCallback() {
                                @Override
                                public void loginSucceeded() {
                                    handleConnection(true);
                                }

                                @Override
                                public void loginFailed(int statusCode, String errorMsg) {
                                    handleConnection(false);

                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            displayLoginDialog();
                                        }
                                    });
                                }
                            }, getApplicationContext());
                        }

                        //Add local images that haven't been uploaded yet
                        ArrayList<Image> unsentImages = getUnsentImages(finalSearchQuery);

                        setImageList(unsentImages);
                        Toast.makeText(ThumbnailListActivity.this, "Failed to get remote photos", Toast.LENGTH_SHORT).show();
                    }
                });

                q.add(request);
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setImageList(getUnsentImages(finalSearchQuery));
                    }
                });
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    SwipeRefreshLayout refreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipeRefresh);
                    if (refreshLayout != null) {
                        refreshLayout.setRefreshing(false);
                    }
                }
            });

            return null;
        }

        private void writeToFile(String data, Context context) {
            try {
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(context.openFileOutput("debug.txt", Context.MODE_APPEND));
                outputStreamWriter.write(data);
                outputStreamWriter.close();
            } catch (IOException e) {
                Log.e("Exception", "File write failed: " + e.toString());
            }
        }


        @NonNull
        private ArrayList<Image> getUnsentImages(String finalSearchQuery) {
            ArrayList<Image> unsentImages = new ArrayList<>();

            //ImageDao imageDao = GenericPhotoApplication.getInstance().getDb().imageDao();
            ImageFileDao imageFileDao = GenericPhotoApplication.getInstance().getDb().imageFileDao();
            List<ImageFile> unsentObjects = imageFileDao.getUnsent();

//            List<ImageFile> unsentObjects = (new Select()).from(ImageFile.class)
//                    .where("uploadStatus = ?", new Object[]{SynchableConstants.UPLOAD_STATUS_NOT_UPLOADED})
//                    .or("uploadStatus = ?", new Object[]{SynchableConstants.UPLOAD_STATUS_UPLOADING})
//                    .or("uploadStatus is null").execute();

            SDGLog.d(SEARCH_TAG, "Pulled " + unsentObjects.size() + " images that haven't been synced yet...");

            for (ImageFile fileObject : unsentObjects) {
                Image object = imageFileDao.getImage(fileObject.getImage_id());

                if (object == null) {
                    // It's a local image that was deleted
                    continue;
                }

                object.inflateFromDatabase();

                if (finalSearchQuery != null) {
                    if (object.tags != null) {
                        for (String tag : object.tags) {
                            if (tag.equals(finalSearchQuery)) {
                                object.pendingUpload = true;
                                unsentImages.add(0, object);
                                break;
                            }
                        }
                    }
                } else if (object != null) {
                    //Add at the end?
                    SDGLog.d(TAG, "Adding local image " + fileObject.filename);
                    object.pendingUpload = true;
                    unsentImages.add(0, object);
                }
            }
            return unsentImages;
        }
    }
}
