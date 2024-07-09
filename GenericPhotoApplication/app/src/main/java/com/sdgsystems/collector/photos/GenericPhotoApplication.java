package com.sdgsystems.collector.photos;

import android.Manifest;
import android.app.Application;
import androidx.room.Room;
import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.Volley;
import com.google.firebase.FirebaseApp;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.crashlytics.internal.common.CrashlyticsCore;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sdgsystems.blueloggerclient.SDGLog;
import com.sdgsystems.collector.photos.data.AppDatabase;
import com.sdgsystems.collector.photos.data.model.ImageCategory;
import com.sdgsystems.collector.photos.data.model.Organization;
import com.sdgsystems.collector.photos.data.model.Site;
import com.sdgsystems.collector.photos.receivers.WifiStateChangedReceiver;
import com.sdgsystems.collector.photos.scanning.LocalScanManager;
import com.sdgsystems.collector.photos.sync.ImageLoaderRequestQueue;
import com.sdgsystems.collector.photos.sync.authorizedVolleyRequests.AuthorizedJsonArrayRequest;
import com.sdgsystems.collector.photos.ui.activity.ImageSetupActivity;

import org.json.JSONArray;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Created by jay on 5/26/17.
 */

public class GenericPhotoApplication extends Application {

    public static final String TAG = GenericPhotoApplication.class.getSimpleName();

    private RequestQueue mRequestQueue;
    private ImageLoader mImageLoader;

    private static GenericPhotoApplication mInstance;

    private LocalScanManager mScanManager = null;

    LocationManager locationManager;
    private Location mLastKnownLocation = null;
    private LocationListener locationListener;

    public ArrayList<ImageCategory> categories;
    public ArrayList<Organization> organizations;
    public ArrayList<Site> sites;
    private AppDatabase db;

    WifiStateChangedReceiver wifiReceiver = new WifiStateChangedReceiver();

    public void onCreate() {
        super.onCreate();
        mInstance = this;
        mInstance.categories = new ArrayList<>();

        LocalScanManager.init(this);

        FirebaseApp.initializeApp(this);

        // Leave time for Crashlytics to set itself up, since it's async
        final ScheduledThreadPoolExecutor c = new ScheduledThreadPoolExecutor(1);
        c.schedule(() -> {
            SDGLog.d(TAG, "Setting up SDG logger");
            final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();

            Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
                handleUncaughtException(thread, e);

                if(defaultHandler != null) {
                    defaultHandler.uncaughtException(thread, e);
                }
            });
        }, 5, TimeUnit.SECONDS);


        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        // Define a listener that responds to location updates
        // Called when a new location is found by the network location provider.
        locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                // Called when a new location is found by the network location provider.
                makeUseOfNewLocation(location);
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            public void onProviderEnabled(String provider) {
            }

            public void onProviderDisabled(String provider) {
            }
        };

        organizations = new ArrayList<>();
        sites = new ArrayList<>();

        initDB();

        registerReceiver(wifiReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    /*
    public void retrieveCategories() {
        AuthorizedJsonArrayRequest request = new AuthorizedJsonArrayRequest(GenericPhotoApplication.getInstance().getImageCategoryUrl(), new Response.Listener<JSONArray>() {
            @Override
            public void onResponse(JSONArray response) {
                SDGLog.d(TAG, "Response: " + response.toString());
                Type categoryListType = new TypeToken<ArrayList<ImageCategory>>() {
                }.getType();

                Gson gson = ImageCategory.getGson();
                List<ImageCategory> imageCategories = gson.fromJson(response.toString(), categoryListType);

                SDGLog.d(TAG, "Pulled remote categories: " + imageCategories);

                List<ImageCategory> filteredImageCategories = new ArrayList<ImageCategory>();

                for(ImageCategory category : imageCategories) {
                    if(!category.hidden) {
                        filteredImageCategories.add(category);
                    }
                }

                GenericPhotoApplication.getInstance().categories = (ArrayList<ImageCategory>) filteredImageCategories;
                SDGLog.d(TAG, "Loaded " + categories.size() + " categories");
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                SDGLog.d(TAG, "Category Retrieval Error: " + error.getMessage());
            }
        });

        RequestQueue q = ImageLoaderRequestQueue.getInstance(getApplicationContext()).getRequestQueue();
        q.add(request);
    } */

    public void onDestroy() {
        unregisterReceiver(wifiReceiver);
        LocalScanManager.deinit();
    }


        private void initDB() {
        db = Room.databaseBuilder(getApplicationContext(),
                AppDatabase.class, "testSynchableApp").allowMainThreadQueries()
                .build();
    }

    public AppDatabase getDb() {
        return db;
    }

    public void requestLocationUpdates(boolean start) {
        boolean gps_enabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        if (!gps_enabled) {
            SDGLog.d(TAG, "No gps location provider enabled...");
        }


        if (start) {
            SDGLog.d(TAG, "requesting location updates");

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.

                SDGLog.d(TAG, "location permissions not granted...");

                return;
            }

            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
            } catch (IllegalArgumentException ex) {
                SDGLog.d(TAG, "Couldn't enable the gps provider...");
            }
        } else {
            locationManager.removeUpdates(locationListener);
        }
    }

    private void makeUseOfNewLocation(Location location) {
        if (mLastKnownLocation != null && isBetterLocation(location, mLastKnownLocation)) {
            SDGLog.d(TAG, "Setting location to " + location.getLatitude() + " " + location.getLongitude());

            mLastKnownLocation = location;
        } else {
            mLastKnownLocation = location;
        }
    }

    public Location getLastKnownLocation() {
        if (mLastKnownLocation == null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                SDGLog.d(TAG, "Permission not granted for fine locations");
                return null;
            } else {
                return locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
        }

        return mLastKnownLocation;
    }

    private static final int TWO_MINUTES = 1000 * 60 * 2;

    /** Determines whether one Location reading is better than the current Location fix
     * @param location  The new Location that you want to evaluate
     * @param currentBestLocation  The current Location fix, to which you want to compare the new one
     */
    protected boolean isBetterLocation(Location location, Location currentBestLocation) {
        if (currentBestLocation == null) {
            // A new location is always better than no location
            return true;
        }

        // Check whether the new location fix is newer or older
        long timeDelta = location.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
        boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
        boolean isNewer = timeDelta > 0;

        // If it's been more than two minutes since the current location, use the new location
        // because the user has likely moved
        if (isSignificantlyNewer) {
            return true;
            // If the new location is more than two minutes older, it must be worse
        } else if (isSignificantlyOlder) {
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(),
                currentBestLocation.getProvider());

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }
        return false;
    }

    /** Checks whether two providers are the same */
    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }

    public static synchronized GenericPhotoApplication getInstance() {
        return mInstance;
    }

    public synchronized RequestQueue getRequestQueue() {
        if (mRequestQueue == null) {
            // getApplicationContext() is key, it keeps you from leaking the
            // Activity or BroadcastReceiver if someone passes one in.

            mRequestQueue = Volley.newRequestQueue(getApplicationContext());
        }

        return mRequestQueue;
    }

    public <T> void addToRequestQueue(Request<T> req, String tag) {
        // set the default tag if tag is empty
        req.setTag(TextUtils.isEmpty(tag) ? TAG : tag);
        getRequestQueue().add(req);
    }

    public <T> void addToRequestQueue(Request<T> req) {
        req.setTag(TAG);
        getRequestQueue().add(req);
    }

    public void cancelPendingRequests(Object tag) {
        if (mRequestQueue != null) {
            mRequestQueue.cancelAll(tag);
        }
    }

    public void handleUncaughtException (Thread thread, Throwable e)
    {
        SDGLog.e(TAG, "Uncaught exception: " + e);
        e.printStackTrace(); // not all Android versions will print the stack trace automatically

        // TODO: This always hangs, so comment it out for now
        GenericPhotoApplication.captureLogFiles(getApplicationContext());

        /*
        Intent intent = new Intent (getApplicationContext(), ShowError.class);
        intent.putExtra("EXCEPTION", e);
        intent.setFlags (Intent.FLAG_ACTIVITY_NEW_TASK); // required when starting from Application
        startActivity (intent);
        */

        System.exit(1); // kill off the crashed app
    }

    public static void captureLogFiles(final Context context) {
        Utilities.collectLogs(context);
    }

    public String getMainUrl() {

        String url = "";

        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String subdomain = p.getString(Constants.PREF_SUBDOMAIN, "");

        if(!subdomain.isEmpty()) url += subdomain + ".";

        String serverName = p.getString(Constants.PREF_DOMAIN, "");

        url += serverName;
        return url;
    }


    public String getUrlRoot() {

        String urlRoot = getMainUrl();

        // http is allowed for the localNoAuth case
        if(!urlRoot.startsWith("http") && !BuildConfig.FLAVOR.contains("localNoAuth")) {
            urlRoot = "https://" + urlRoot;
        }

        if(!urlRoot.endsWith("/")) {
            urlRoot += "/";
        }

        return urlRoot;
    }

    public String getWebAppImageUrl() {
        return getUrlRoot() + "photos/#/image/";
    }

    public String getApiUrl() {
        if(BuildConfig.FLAVOR.contains("localNoAuth")) return getUrlRoot();
        else return getUrlRoot() + "api/";
    }

    public String getImagesUrl() {
        return getApiUrl() + "images";
    }

    public String getImageApiUrl() {
        return getApiUrl() + "images/";
    }

    public String getImageCategoryUrl() {
        return getApiUrl() + "image-categories";
    }

    public String getOrganizationsUrl() {
        return getApiUrl() + "organizations/me";
    }

    public String getSitesUrl() {
        return getApiUrl() + "sites";
    }

    public String getAuthUrl() {
        return getUrlRoot() + "auth/";
    }

    @NonNull
    public HashMap<String, String> getAuthHeaders() {

        SDGLog.d(TAG, "Calling for auth headers: " + getBearerToken());

        HashMap<String, String> headers = new HashMap<String, String>();
        //headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + getBearerToken());

        if(mCurrentOrg != null) {
            headers.put("Organization", mCurrentOrg.id);
        }

        if(BuildConfig.FLAVOR.contains("localNoAuth")) {
            headers.put("Organization", "787878787878787878787878");
        }

        if(getCurrentSite() != null) {
            headers.put("Site", getCurrentSite().id);
        }

        return headers;
    }

    private String mBearerToken = null;

    public String getBearerToken() {
        return mBearerToken;
    }

    public void setBearerToken(String bearerToken) {
        mBearerToken = bearerToken;
    }

    private Organization mCurrentOrg = null;

    public Organization getCurrentOrg() {
        return mCurrentOrg;
    }

    public void setCurrentOrg(Organization organization) {
        mCurrentOrg = organization;
    }

    public static String PREF_CURRENT_SITE_ID;

    public Site getCurrentSite() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String currentId = prefs.getString(PREF_CURRENT_SITE_ID, null);

        //Search through the currently available sites and return the current one if the mongo ids match
        for(Site tmpSite : sites) {
            if(tmpSite.id != null && tmpSite.id.equals(currentId)) {
                return tmpSite;
            }
        }

        return null;
    }

    public void setCurrentSite (Site site) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        prefs.edit().putString(PREF_CURRENT_SITE_ID, site.id).commit();
    }
}
