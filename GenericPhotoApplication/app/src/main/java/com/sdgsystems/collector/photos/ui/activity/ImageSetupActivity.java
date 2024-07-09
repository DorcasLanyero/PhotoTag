package com.sdgsystems.collector.photos.ui.activity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.Observer;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import com.sdgsystems.blueloggerclient.SDGLog;
import com.sdgsystems.collector.photos.Constants;
import com.sdgsystems.collector.photos.GenericPhotoApplication;
import com.sdgsystems.collector.photos.R;
import com.sdgsystems.collector.photos.Utilities;
import com.sdgsystems.collector.photos.data.model.ImageCategory;
import com.sdgsystems.collector.photos.scanning.GenericScanningCallback;
import com.sdgsystems.collector.photos.tasks.TagListCleaner;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import static com.sdgsystems.collector.photos.Utilities.startTopLevelActivity;
import static com.sdgsystems.idengine.internal.Debug.debug;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

public class ImageSetupActivity extends ScanningActivity implements ILoginCallback {
    public static final String IMAGE_DATABASE_ID = "com.sdgsystems.EXTRA_IMAGE_ID";
    private static final String TAG = "ImageSetupActivity";

    private BottomNavigationView bottomNavigationView;

    private ArrayList<String> mImageTags;
    private ArrayList<String> mImageCategories;
    private String mDescription;

    public static final String PREF_DEFAULT_TAGS = "PREF_DEFAULT_TAGS";
    public static final String PREF_DEFAULT_CATEGORIES = "PREF_DEFAULT_CATEGORIES";
    public static final String PREF_DEFAULT_DESCRIPTION = "PREF_DEFAULT_DESCRIPTION";
    private ExtendedFloatingActionButton scanButton;
    private SharedPreferences sharedPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_image_setup);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        final TextInputEditText txtTagName = (TextInputEditText) findViewById(R.id.txtTagName);
        txtTagName.setImeOptions(EditorInfo.IME_ACTION_DONE);
        txtTagName.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if( i == EditorInfo.IME_ACTION_DONE && !textView.getText().toString().trim().isEmpty()) {
                    SDGLog.d(TAG, "adding tag " + textView.getText().toString());
                    String tagName = textView.getText().toString().trim();
                    if (!mImageTags.contains(tagName)) {
                        mImageTags.add(tagName);
                        updateTags();
                        TagListCleaner.INSTANCE.setTagActivity();
                    }
                    txtTagName.getText().clear();

                    // Don't close soft keyboard because the user may want to enter another tag
                    // InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                    // imm.hideSoftInputFromWindow(textView.getWindowToken(), 0);

                    return true;
                }

                return false;
            }
        });

        scanButton = findViewById(R.id.scanTag);
        configureScanButton(scanButton);
        scanButton.setOnClickListener(v -> {
            triggerScanning(true);
        });

        bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                SDGLog.d(TAG, "Clicked " + item.getTitle());
                int prevSelectedId = bottomNavigationView.getSelectedItemId();
                int id = item.getItemId();
                if (id == prevSelectedId) {
                    debug(TAG, item.getTitle() + " is already selected");
                    return false;
                }
                if (id == R.id.action_list) {
                    startTopLevelActivity(ImageSetupActivity.this, ThumbnailListActivity.class);
                } else if (id == R.id.action_camera) {
                    Utilities.captureImages(ImageSetupActivity.this);
                } else if (id == R.id.action_tags) {
                }
                return true;
            }
        });
        bottomNavigationView.setSelectedItemId(R.id.action_tags);

        // kick off timer, if needed
        TagListCleaner.INSTANCE.setTagActivity();
        TagListCleaner.INSTANCE.getGetClearTags().observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean tagsCleared) {
                if (tagsCleared && mImageTags != null) {
                    // clear the local instance array and update the chips
                    mImageTags.clear();
                    updateTags();
                }
            }
        });
    }

    @Override
    protected void onKeyboardHidden() {
        bottomNavigationView.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onKeyboardShown() {
        bottomNavigationView.setVisibility(View.GONE);
    }

    @Override
    public void onBackPressed() {
        // Finish the whole app
        super.onBackPressed();
        finishAffinity();
    }

    @Override
    public void onPause() {
        super.onPause();
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(ImageSetupActivity.this).edit();
        editor.putStringSet(PREF_DEFAULT_TAGS, new HashSet<String>(mImageTags));
        editor.putStringSet(PREF_DEFAULT_CATEGORIES, new HashSet<String>(mImageCategories));
        editor.putString(PREF_DEFAULT_DESCRIPTION, mDescription);
        editor.commit();
    }

    private void updateMetaData() {
        updateTags();
        updateOwners();
        updateCategories();
    }

    /*
    private void retrieveCategories() {
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
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                SDGLog.d(TAG, "Category Retrieval Error: " + error.getMessage());
                Toast.makeText(ImageSetupActivity.this, "Failed to get image categories", Toast.LENGTH_SHORT).show();
            }
        });

        RequestQueue q = ImageLoaderRequestQueue.getInstance(getApplicationContext()).getRequestQueue();
        q.add(request);
    } */

    private void updateTags() {
        updateChips(R.id.tagCloud, "#aa81c784", "#aa000000", mImageTags, getLastTagScanned(),
                new IDeleteElementCallback() {
                    @Override
                    public void delete(String deleteString) {
                        mImageTags.remove(deleteString);
                    }
                });
    }

    private void updateOwners() {
        /*
        updateChips(R.id.ownerCloud, "#aa81c784", "#aa666666", mImageOwners, new IDeleteElementCallback() {
            @Override
            public void delete(String deleteString) {
                mImage.owners.remove(deleteString);
            }
        });
        */
    }

    private void updateCategories() {
        updateChips(R.id.categoryCloud, "#aa64b5f6", "#aa000000", mImageCategories, null,
                new IDeleteElementCallback() {
                    @Override
                    public void delete(String deleteString) {
                        mImageCategories.remove(deleteString);
                    }
                });
    }

   private final ReentrantLock lock = new ReentrantLock();

    /**
     * Enter into edit mode
     */
    private void setupEditMode() {
        TextInputLayout addTag = (TextInputLayout) findViewById(R.id.layoutTagName);
        TextInputLayout addOwner = (TextInputLayout) findViewById(R.id.txtOwnerLayout);
        TextInputLayout addCategory = (TextInputLayout) findViewById(R.id.txtCategoryLayout);

        //Show the 'enter...' fields
        addTag.setVisibility(View.VISIBLE);
        addOwner.setVisibility(View.VISIBLE);
        addCategory.setVisibility(View.VISIBLE);

       /* txtCategory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SDGLog.d(TAG, "Clicked category...");
               // addCategory(view);
                new CategoriesActivity().addCats(view, mImageCategories, updateImageCategories);
            }
        }); */


        ArrayAdapter<String> categoriesAdapter = new ArrayAdapter<>(this, R.layout.list_item, getCategoriesAdapter());

        AutoCompleteTextView txtCategoryAutocomplete = findViewById(R.id.txtCategoryAutocomplete);

        txtCategoryAutocomplete.setAdapter(categoriesAdapter);

        txtCategoryAutocomplete.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapter, View view, int position,
                                    long arg3) {
                ImageCategory cat = null;
                String SelectedCategory = adapter.getItemAtPosition(position).toString();
                SDGLog.d(TAG, "Selected Category " + SelectedCategory);

                for (ImageCategory category: GenericPhotoApplication.getInstance().categories) {
                    if (SelectedCategory.equals(category.name)){
                        cat = category;
                    }
                }

                if(cat!=null && !mImageCategories.contains(cat.name)) {
                    mImageCategories.add((cat.name));
                    categoriesAdapter.clear();
                    categoriesAdapter.addAll(getCategoriesAdapter());
                    categoriesAdapter.notifyDataSetChanged();
                    runOnUiThread(() -> updateMetaData());
                }
            }
        });

        final TextInputEditText txtAnnotation = (TextInputEditText) findViewById(R.id.txtAnnotation);
        txtAnnotation.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                SDGLog.d(TAG, "Comment: " + s);
                mDescription = s.toString();
            }
        });

        txtAnnotation.setText(mDescription);

        //initScanner(mScanningCallback);

        updateMetaData();
    }


    public List<String> getCategoriesAdapter(){
        List<ImageCategory> availableCategories = new ArrayList<>();
        for (ImageCategory category : GenericPhotoApplication.getInstance().categories) {
            if (!mImageCategories.contains(category.name)) {
                availableCategories.add(category);
            }
        }

        List<String> items = new ArrayList<>();
        for (ImageCategory category : availableCategories) {
            items.add(category.name);
        }

        return items;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_image_setup, menu);
        setupEditMode();
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();

        setLoginCallback(ImageSetupActivity.this);
        if(GenericPhotoApplication.getInstance().getBearerToken() == null && loginDialogFragment == null) {
            displayLoginDialog();
        }

       new CategoriesActivity().retrieveCategories(ImageSetupActivity.this);

        mImageCategories = new ArrayList<>(PreferenceManager.getDefaultSharedPreferences(this).getStringSet(PREF_DEFAULT_CATEGORIES, new HashSet<String>()));
        mImageTags = new ArrayList<>(PreferenceManager.getDefaultSharedPreferences(this).getStringSet(PREF_DEFAULT_TAGS, new HashSet<String>()));
        mDescription = PreferenceManager.getDefaultSharedPreferences(this).getString(PREF_DEFAULT_DESCRIPTION, "");

        updateMetaData();

        configureScanButton(scanButton);

        bottomNavigationView.setSelectedItemId(R.id.action_tags);

        if(!cameraScanning) {
            initScanner(mScanningCallback);
            SDGLog.d(TAG, "initialized scanner after resume");
        }
    }

    GenericScanningCallback mScanningCallback = new GenericScanningCallback() {
            @Override
            public void scanAvailable(String scanData, Constants.ScannerType scannerType) {

                SDGLog.d(TAG, "scanAvailable(" + scanData + ", " + scannerType);
                if (Utilities.shouldIgnoreScan(ImageSetupActivity.this, scanData, scannerType)) return;
                scanData = (scanData != null) ? scanData.trim() : "";


                if (isSingleTagMode()) {
                    if (singleTagRemovesAll()) {
                        mImageTags.clear();
                    }
                    else {
                        mImageTags.remove(getLastTagScanned());
                    }
                }
                if (!mImageTags.contains(scanData)) {
                    mImageTags.add(scanData);
                }

                // For scanners which can deliver results while this activity is paused, save tags when a scan is received
                // rather than onPause, so that they aren't overwritten when we load from preferences in onResume.
                if(scannerType == Constants.ScannerType.SCANNER_TYPE_NFC || scannerType == Constants.ScannerType.SCANNER_TYPE_CAMERA) {
                    SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(ImageSetupActivity.this).edit();
                    editor.putStringSet(PREF_DEFAULT_TAGS, new HashSet<String>(mImageTags));
                    editor.commit();
                }
                setLastTagScanned(scanData);
                updateTags();
                TagListCleaner.INSTANCE.setTagActivity();
            }

            @Override
            public void statusMessage(String message, boolean error) {
                Toast.makeText(ImageSetupActivity.this, message, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void cameraScanningStarted() {
                cameraScanning = true;
            }

            @Override
            public void scanningComplete() {
                cameraScanning = false;
            }

            @Override
            public void scannerClosed() {
                configureScanButton(scanButton);
            }

            @Override
            public void scannerConnected() {
                configureScanButton(scanButton);
            }

            @Override
            public boolean shouldUseCameraScanning() {
                return false;
            }
        };

    /*
    public void addCategory(View view) {

        AlertDialog dialog;

        AlertDialog.Builder builder = new AlertDialog.Builder(ImageSetupActivity.this);
        builder.setTitle("Choose a Category...");
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.cancel();
            }
        });

        CharSequence[] items = new CharSequence[GenericPhotoApplication.getInstance().categories.size()];
        int index = 0;
        for(ImageCategory category : GenericPhotoApplication.getInstance().categories) {
            items[index] = category.name;
            index++;
        }

        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                String categoryName = GenericPhotoApplication.getInstance().categories.get(i).name;
                if(!mImageCategories.contains(categoryName)) {
                    mImageCategories.add(categoryName);
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateMetaData();
                    }
                });

                dialogInterface.dismiss();
            }
        });

        dialog = builder.create();
        dialog.show();
    }
     */

    Function1<? super  ImageCategory, Unit> updateImageCategories = new Function1<ImageCategory, Unit>() {
        @Override
        public Unit invoke(ImageCategory category) {
            if(!mImageCategories.contains(category.name)) {
                mImageCategories.add((category.name));
                runOnUiThread(() -> updateMetaData());
            }
            return null;
        }
    };

    public void addOwner(View view) {
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        SDGLog.d(TAG, "Key Event: " + keyCode);
        switch (keyCode) {
            case 104:
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_BACK:
                return true;
        }
        return false;
    }

    @Override
    public void loginAttempted(boolean success) {

    }

    @Override
    public void dataChanged() {

    }
}
