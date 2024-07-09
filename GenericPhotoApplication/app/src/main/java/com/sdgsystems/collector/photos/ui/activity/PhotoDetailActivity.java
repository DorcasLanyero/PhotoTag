package com.sdgsystems.collector.photos.ui.activity;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.text.method.KeyListener;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.google.gson.Gson;
import com.sdgsystems.blueloggerclient.SDGLog;
import com.sdgsystems.collector.photos.Constants;
import com.sdgsystems.collector.photos.GenericPhotoApplication;
import com.sdgsystems.collector.photos.Utilities;
import com.sdgsystems.collector.photos.data.dao.ImageDao;
import com.sdgsystems.collector.photos.data.model.ImageCategory;
import com.sdgsystems.collector.photos.scanning.GenericScanningCallback;
import com.sdgsystems.collector.photos.sync.NetworkRequestHandler;
import com.sdgsystems.collector.photos.sync.authorizedVolleyRequests.AuthorizedJsonObjectRequest;
import com.sdgsystems.collector.photos.sync.ImageLoaderRequestQueue;
import com.sdgsystems.collector.photos.R;
import com.sdgsystems.collector.photos.data.model.Image;
import com.sdgsystems.collector.photos.sync.ImageMetaDataUploader;
import com.sdgsystems.synchableapplication.SynchableConstants;
import com.sdgsystems.synchableapplication.Synchronizer;
import com.sdgsystems.synchableapplication.callbacks.IUploadCallback;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;

import fisk.chipcloud.ChipCloud;
import fisk.chipcloud.ChipCloudConfig;
import fisk.chipcloud.ChipDeletedListener;
import fisk.chipcloud.ChipListener;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;

public class PhotoDetailActivity extends ScanningActivity {
    public static final String IMAGE_DATABASE_ID = "com.sdgsystems.EXTRA_IMAGE_ID";
    public static final String IMAGE_MONGO_ID = "com.sdgsystems.EXTRA_IMAGE_MONGO_ID";
    private static final String TAG = "PhotoDetailActivity";

    private Image mImage;
    private Image mImageOld;

    private String mImageMongoId = null;
    private Long mImageDatabaseId = null;
    private boolean mImageLoaded = false;
    private ImageView mImageView;
    private TextInputEditText mAnnotation;
    private TextView mTimeStamp;
    private TextView mUser;
    private TextView mMapLoc;

    private Menu mOptionsMenu;

    private boolean mEditMode = false;
    private boolean mRotateAttempted;
    private ExtendedFloatingActionButton scanButton;
    private View mCancelEditPanel;
    private KeyListener mSavedKeyListener;
    private FloatingActionButton editButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_photo_detail);

        mCancelEditPanel = findViewById(R.id.cancel_edit_panel);

        mImageMongoId = getIntent().getStringExtra(IMAGE_MONGO_ID);
        mImageDatabaseId = getIntent().getLongExtra(IMAGE_DATABASE_ID, -1);

        //Only try to get the image if we're not setting up a new one
        if ((mImageMongoId == null || mImageMongoId.isEmpty()) && savedInstanceState != null) {
            mImageMongoId = savedInstanceState.getString(IMAGE_MONGO_ID, null);
            mImageDatabaseId = savedInstanceState.getLong(IMAGE_DATABASE_ID, -1);
        }

        if (mImageDatabaseId == -1 && (mImageMongoId == null || mImageMongoId.isEmpty())) {
            return;
        }

        editButton = (FloatingActionButton) findViewById(R.id.edit);
        editButton.hide();

        editButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mImage == null) {
                    return;
                }
                mImageOld = new Image(mImage);

                toggleEdit(true);
            }
        });

        scanButton = (ExtendedFloatingActionButton) findViewById(R.id.scan);
        scanButton.hide();
        scanButton.setOnClickListener(v -> {
            triggerScanning(true);
        });

        mImageView = (ImageView) findViewById(R.id.image_detail_view);
        mImageView.setImageDrawable(getResources().getDrawable(R.drawable.ellipsis));

        mAnnotation = (TextInputEditText) findViewById(R.id.photo_annotation);
        mAnnotation.setEnabled(true); // The xml has it disabled, so fix it
        mAnnotation.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateButtonStates();
            }
            public void afterTextChanged(Editable s) { }
        });

        mSavedKeyListener = mAnnotation.getKeyListener();
        mTimeStamp = (TextView) findViewById(R.id.txtTimeStamp);

        mUser = (TextView) findViewById(R.id.txtUser);
        mMapLoc= (TextView) findViewById(R.id.txtLocation);

        SDGLog.d(TAG, "Setting imageview ellipsis width to " + mImageView.getWidth());

        //updateImageDetails();

        //getSupportActionBar().setDisplayHomeAsUpEnabled(true);
       // getSupportActionBar().setHomeButtonEnabled(true);

        toggleEdit(false);
    }

    private boolean mIsKeyboardShowing = false;

    @Override
    protected void onKeyboardShown() {
        mIsKeyboardShowing = true;
        updateButtonStates();
    }

    @Override
    protected void onKeyboardHidden() {
        mIsKeyboardShowing = false;
        updateButtonStates();
    }

    private void updateImageDetails() {
        updateImageDetails(false);
    }

    private void updateImageDetails(boolean reloadImage) {

        SDGLog.d(TAG, "update image details");

        // Always always always get from the database—that way, we check if the image is
        // uploaded whenever we resume the activity
        ImageDao imageDao = GenericPhotoApplication.getInstance().getDb().imageDao();
        Image i = imageDao.getImage(mImageDatabaseId);
        if(mImage == null && i != null) {
            mImage = i;
            mImage.inflateFromDatabase();
        }

        mImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mImageLoaded) {
                    Intent zoom = new Intent(PhotoDetailActivity.this, PhotoZoomActivity.class);
                    zoom.putExtra(PhotoZoomActivity.IMAGE_MONGO_ID, mImageMongoId);
                    zoom.putExtra(PhotoZoomActivity.IMAGE_DATABASE_ID, mImageDatabaseId);
                    zoom.putExtra(PhotoZoomActivity.IMAGE_EXIF_ROTATION, mImage.getExifRotation());
                    startActivity(zoom);
                }
            }
        });

        final String imageURL = GenericPhotoApplication.getInstance().getImageApiUrl() + mImageMongoId + "/meta";

        //Only pull the remote image once the layout has been properly built so that it scales properly
        mImageView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int i, int i1, int i2, int i3, int i4, int i5, int i6, int i7) {
                if(!mImageLoaded) {
                    retrieveImage(imageURL);
                }
            }
        });


        final TextInputEditText txtTagName = (TextInputEditText) findViewById(R.id.txtTagName);
        txtTagName.setImeOptions(EditorInfo.IME_ACTION_DONE);
        txtTagName.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i != EditorInfo.IME_ACTION_DONE) return false;
                String tag = textView.getText().toString().trim();
                if (!tag.isEmpty() && !mImage.tags.contains(tag)) {
                    SDGLog.d(TAG, "adding tag " + tag);
                    mImage.tags.add(textView.getText().toString());
                    updateTags();
                }
                txtTagName.getText().clear();
                return true;
            }
        });

        if(reloadImage) {
            retrieveImage(imageURL);
        }
    }

    private void retrieveImage(String imageURL) {
        ImageDao imageDao = GenericPhotoApplication.getInstance().getDb().imageDao();
        SDGLog.d(TAG, "retrieving image " + imageURL);

        Image i = imageDao.getImage(mImageDatabaseId);

        if(mImage == null && i != null) {
            mImage = i;
            mImage.inflateFromDatabase();
        }

        if(mImageMongoId == null || mImageMongoId.isEmpty() || !NetworkRequestHandler.isLoggedIn()) {
            editButton.setVisibility(View.VISIBLE); // TODO: Still valid?

            SDGLog.d(TAG, "Image data: " + mImage.json_representation);

            if(mImage.mongo_id != null) {
                //Check to see if the db has gotten the mongo id since the intent was called...
                mImageMongoId = mImage.mongo_id;
                updateImageDetails(true);
            } else {

                //This is a local file
                File imageFile = new File(imageDao.getImageFile(mImage.getUid()).filename + "_thumb");
                if (imageFile.exists()) {
                    Bitmap imageBitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
                    if (imageBitmap != null) {

                        if (mOptionsMenu != null) {
                            mOptionsMenu.findItem(R.id.menu_item_image_status).setVisible(true);
                        }
                        setImageViewBitmap(imageBitmap, mImage, true);
                    }
                }

                if (!mImageLoaded) {
                    mImageView.setImageDrawable(getResources().getDrawable(R.drawable.error));
                }

                updateMetaData();
            }
        } else {

            if(mOptionsMenu != null) {
                mOptionsMenu.findItem(R.id.menu_item_image_status).setVisible(false);
            } else {
                SDGLog.d(TAG, "Options menu hasn't been inflated yet...");
            }

            AuthorizedJsonObjectRequest metaRequest = new AuthorizedJsonObjectRequest(imageURL, null, new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    SDGLog.d(TAG, "Got metadata for image");
                    Gson imageGson = Image.getGson();
                    mImage = imageGson.fromJson(response.toString(), Image.class);
                    AuthorizedJsonObjectRequest userRequest = new AuthorizedJsonObjectRequest(GenericPhotoApplication.getInstance().getApiUrl() +"usernames/" + mImage.uploader, null, new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            try {
                                SDGLog.d(TAG, "Got username for image: " + response.getString("name"));
                                mImage.uploaderName = response.getString("name");
                                updateMetaData();
                            } catch (JSONException e) {
                                SDGLog.e(TAG, "Error getting name", e);
                            }
                        }}, new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {

                        }
                    });
                    RequestQueue q = ImageLoaderRequestQueue.getInstance(getApplicationContext()).getRequestQueue();
                    q.add(userRequest);
                    editButton.setVisibility(View.VISIBLE); // TODO: Still valid?

                    updateMetaData();

                    if(!mRotateAttempted) {
                        Drawable d = mImageView.getDrawable();
                        if(d instanceof BitmapDrawable) { // null check not required because null is not an instance of anything
                            BitmapDrawable b = (BitmapDrawable) d;
                            setImageViewBitmap(b.getBitmap(), mImage, true);
                        }
                    }

                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {

                }
            });


            RequestQueue q = ImageLoaderRequestQueue.getInstance(getApplicationContext()).getRequestQueue();
            q.add(metaRequest);

            ImageLoaderRequestQueue.getInstance(PhotoDetailActivity.this).getImageLoader().get(GenericPhotoApplication.getInstance().getImageApiUrl() + mImageMongoId + "/thumb", new ImageLoader.ImageListener() {
                @Override
                public void onResponse(final ImageLoader.ImageContainer response, boolean isImmediate) {
                    // if bitmap is null, it's a cache miss, and we want to keep the still-loading drawable
                    SDGLog.d(TAG, "Photo response: immediate? " + isImmediate);

                    if(response.getBitmap() != null) {
                        Bitmap bitmap = response.getBitmap();
                        setImageViewBitmap(bitmap, mImage, true);
                    }
                }

                @Override
                public void onErrorResponse(VolleyError error) {
                    mImageView.setImageDrawable(getResources().getDrawable(R.drawable.error));
                }
            });
        }
    }

    private void setImageViewBitmap(Bitmap bitmap, Image metadata, boolean shouldScale) {

        if(shouldScale) {

            int height = findViewById(R.id.image_detail_view_container).getHeight();
            SDGLog.d(TAG, "Setting height to " + height);
            int width = (height * bitmap.getWidth()) / bitmap.getHeight();
            SDGLog.d(TAG, "Setting height to " + height + " (" + bitmap.getWidth() + ", " + bitmap.getHeight() + ")");
            bitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
        }

        if(metadata != null) {
            bitmap = Utilities.rotateBitmap(bitmap, metadata);
            mRotateAttempted = true;
        }

        mImageView.setImageBitmap(bitmap);
        mImageLoaded = true;

        updateMetaData();
    }

    private void updateMetaData() {
        if(mImageLoaded && mImage != null) {

            if (mImage.annotation != ""){
                mAnnotation.setText(mImage.annotation);
            }

            mTimeStamp.setText(mImage.getFormattedTimestamp());

            DecimalFormat format = new DecimalFormat("0.00000");

            mUser.setText(mImage.uploaderName);
            mMapLoc.setPaintFlags(Paint.UNDERLINE_TEXT_FLAG);

            if(mImage.location != null && mImage.location.coordinates != null) {
                mMapLoc.setText(format.format(mImage.location.coordinates.get(1)) + ", " + format.format(mImage.location.coordinates.get(0)));
            }

            ImageView iconView = findViewById(R.id.image_uploading_icon_view);

            // TODO: for the icon to show up here, pendingUpload needs to be set correctly
            // It isn't right now, because mImage comes from the stored JSON representation
            // rather than the DB representation
            if(mImage.pendingUpload) {
                iconView.setVisibility(View.VISIBLE);
            }
            else {
                iconView.setVisibility(View.GONE);
            }

            updateTags();
            updateOwners();
            updateCategories();
        }
    }

    private void updateTags() {
        SDGLog.d(TAG, "Tag update: " + mImage.tags);
        updateChips(R.id.tagCloud, "#aa81c784", "#aa000000", mImage.tags, new IDeleteElementCallback() {
                    @Override
                    public void delete(String deleteString) {
                        mImage.tags.remove(deleteString);
                        updateButtonStates();
                    }
                });
        updateButtonStates();
    }

    private void updateOwners() {
        updateChips(R.id.ownerCloud, "#aa81c784", "#aa000000", mImage.owners, new IDeleteElementCallback() {
            @Override
            public void delete(String deleteString) {
                mImage.owners.remove(deleteString);
            }
        });
    }

    private void updateCategories() {

        ArrayList<String> categoryList = new ArrayList<String>();
        for(ImageCategory category : mImage.categories) {
            categoryList.add(category.name);
        }

        updateChips(R.id.categoryCloud, "#aa64b5f6", "#aa000000", categoryList, new IDeleteElementCallback() {
            @Override
            public void delete(String deleteString) {
                for(int index = 0; index < mImage.categories.size(); index++) {
                    if(mImage.categories.get(index).name.equals(deleteString)) {
                        mImage.categories.remove(index);
                        break;
                    }
                }
                updateButtonStates();
            }
        });
        updateButtonStates();
    }

    protected void updateChips(final int cloudLayout, final String backgroundColor,
                               final String textColor, final List<String> chipList,
                               final IDeleteElementCallback deleteCallback) {
        super.updateChips(cloudLayout, backgroundColor, textColor, chipList, null,
                mEditMode ? deleteCallback : null);
    }

    /**
     * Enter into edit mode
     */
    private void toggleEdit(boolean enterEditMode) {
        TextInputLayout addTag = (TextInputLayout) findViewById(R.id.layoutTagName);
        TextInputLayout addOwner = (TextInputLayout) findViewById(R.id.txtOwnerLayout);
        TextInputLayout comment = (TextInputLayout) findViewById(R.id.comment_outlined_text_input_layout);
        //labels
        TextView tagLabel =  (TextView) findViewById(R.id.tagLabel);
        TextView categoryLabel = (TextView)  findViewById(R.id.categoryLabel);
        TextView ownerLabel = (TextView)  findViewById(R.id.ownerLabel);
        TextView commentsLabel = findViewById(R.id.photoAnnotationLabel);

        //icons
        ImageView tagIcon = (ImageView) findViewById(R.id.tagIcon);
        ImageView categoryIcon =  (ImageView) findViewById(R.id.categoryIcon);
        ImageView commentIcon = (ImageView) findViewById(R.id.photoAnnotationIcon);
        ImageView ownerIcon = (ImageView) findViewById(R.id.ownerIcon);

        //divider
        View divider = findViewById(R.id.divider);

        final TextInputLayout addCategory = (TextInputLayout) findViewById(R.id.txtCategoryLayout);

        ConstraintLayout imageViewContainer = (ConstraintLayout) findViewById(R.id.image_detail_view_container);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(imageViewContainer.getLayoutParams());

        if(enterEditMode) {
            mEditMode = true;

            //Set the image to normal size
            layoutParams.weight = 0.5f;
            layoutParams.width = imageViewContainer.getWidth();
            imageViewContainer.setLayoutParams(layoutParams);

            //If there is a description, display it, otherwise, hide the field
            comment.setVisibility(View.VISIBLE);
            mAnnotation.setVisibility(View.VISIBLE);
            mAnnotation.setFocusable(true);
            mAnnotation.setFocusableInTouchMode(true);
            mAnnotation.setClickable(true);
            mAnnotation.setCursorVisible(true);
            mAnnotation.setKeyListener(mSavedKeyListener);
            mAnnotation.setText(mImage.annotation);

            //hide icons
            tagIcon.setVisibility(View.GONE);
            categoryIcon.setVisibility(View.GONE);
            commentIcon.setVisibility(View.GONE);
            ownerIcon.setVisibility(View.GONE);

            //show labels
            tagLabel.setVisibility(View.VISIBLE);
            categoryLabel.setVisibility(View.VISIBLE);
            ownerLabel.setVisibility(View.VISIBLE);

            commentsLabel.setVisibility(View.GONE);

            //show divider
            divider.setVisibility(View.VISIBLE);

            //Show the 'enter...' fields
            addTag.setVisibility(View.VISIBLE);
            //addOwner.setVisibility(View.VISIBLE);
            addCategory.setVisibility(View.VISIBLE);

            /*
            final TextInputEditText txtCategory = (TextInputEditText) findViewById(R.id.txtCategory);
            txtCategory.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    SDGLog.d(TAG, "Clicked category...");
                    //addCategory(view);

                    List<String> mImageCategories =  new ArrayList<>();
                    for (ImageCategory category: mImage.categories) {
                        mImageCategories.add(category.name);
                    }
                    new CategoriesActivity().addCat(view, mImageCategories, updateImageCategories);
                }
            }); */

            ArrayAdapter<String> categoriesAdapter = new ArrayAdapter<>(this, R.layout.list_item, getCategoriesAdapter());
            AutoCompleteTextView txtCategoryAutocomplete = findViewById(R.id.txtCategory);
            txtCategoryAutocomplete.setAdapter(categoriesAdapter);

            // Set an OnClickListener for the AutoCompleteTextView
            txtCategoryAutocomplete.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapter, View view, int position,
                                        long arg3) {
                    ImageCategory cat = null;
                    String SelectedCategory = adapter.getItemAtPosition(position).toString();
                    SDGLog.d(TAG, "Selected Category " + SelectedCategory);

                    for (ImageCategory categories: GenericPhotoApplication.getInstance().categories) {
                        if (SelectedCategory.equals(categories.name)){
                            cat = categories;
                        }
                    }

                    if(cat!=null && !mImage.categories.contains(cat)) {
                        mImage.categories.add((cat));
                        categoriesAdapter.clear();
                        categoriesAdapter.addAll(getCategoriesAdapter());
                        categoriesAdapter.notifyDataSetChanged();
                        runOnUiThread(() -> updateMetaData());
                    }
                }
            });

            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            getSupportActionBar().setHomeButtonEnabled(false);

            //Not the best way to do this
            getSupportActionBar()/* or getSupportActionBar() */.setTitle(Html.fromHtml("<font color=\"#E3E1EB\">" + getSupportActionBar().getTitle() + "</font>"));

            initScanner(mScanningCallback);
            openScanner();
        } else {
            mEditMode = false;
            hideKeyboard();
            updateButtonStates();

            //show icons
            tagIcon.setVisibility(View.VISIBLE);
            categoryIcon.setVisibility(View.VISIBLE);
            commentIcon.setVisibility(View.VISIBLE);
            ownerIcon.setVisibility(View.VISIBLE);

            //hide labels
            tagLabel.setVisibility(View.GONE);
            categoryLabel.setVisibility(View.GONE);
            ownerLabel.setVisibility(View.GONE);

            commentsLabel.setVisibility(View.VISIBLE);

            //hide divider
            divider.setVisibility(View.GONE);

            //Set the image to normal size
            layoutParams.weight = 0.5f;
            imageViewContainer.setLayoutParams(layoutParams);

            //Disable editing of the comment
            comment.setVisibility(View.GONE);
            mAnnotation.setFocusable(false);
            mAnnotation.setFocusableInTouchMode(false);
            mAnnotation.setClickable(false);
            mAnnotation.setCursorVisible(false);
            mAnnotation.setKeyListener(null);

            commentsLabel.setFocusable(false);
            commentsLabel.setFocusableInTouchMode(false);
            commentsLabel.setClickable(false);
            commentsLabel.setCursorVisible(false);
            commentsLabel.setKeyListener(null);


            if (mImage != null && mImage.annotation != null && !mImage.annotation.trim().isEmpty()) {
                commentsLabel.setVisibility(View.VISIBLE);
                commentsLabel.setText(mImage.annotation);
            } else {
                mAnnotation.setVisibility(View.GONE);
            }

            //Hide the 'enter...' fields
            addTag.setVisibility(View.GONE);
            //addOwner.setVisibility(View.GONE);
            addCategory.setVisibility(View.GONE);

            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);

            getSupportActionBar()/* or getSupportActionBar() */.setTitle(Html.fromHtml("<font color=\"#E3E1EB\">" + getSupportActionBar().getTitle() + "</font>"));

            closeScanner();
        }

        updateMetaData();
    }

    public List<String> getCategoriesAdapter(){
        List<ImageCategory> availableCategories = new ArrayList<>();
        for (ImageCategory category : GenericPhotoApplication.getInstance().categories) {
            if (!mImage.categories.contains(category)) {
                availableCategories.add(category);
            }
        }

        List<String> items = new ArrayList<>();
        for (ImageCategory category : availableCategories) {
            items.add(category.name);
        }

        return items;
    }

    private void updateButtonStates() {
        if (mEditMode) {
            if (hasEditedImageChanged())
                mCancelEditPanel.setVisibility(View.VISIBLE);
            else
                mCancelEditPanel.setVisibility(View.INVISIBLE);
            //Show the 'edit mode' fab
            editButton.setVisibility(View.GONE);
            if (mIsKeyboardShowing) scanButton.hide();
            else configureScanButton(scanButton);
        } else {
            mCancelEditPanel.setVisibility(View.GONE);
            //Show the 'edit mode' fab
            editButton.show();
            scanButton.hide();
        }
    }

    /**
     * save and close
     */
    private void save() {
        if(mImage.tags == null) {
            mImage.tags = new ArrayList<String>();
        }

        if(mImage.categories == null) {
            mImage.categories = new ArrayList<ImageCategory>();
        }

        mImage.annotation = mAnnotation.getText().toString();

        ImageDao imageDao = GenericPhotoApplication.getInstance().getDb().imageDao();

        //Check for the image having been uploaded already and merge in the mongo id if it has
        if(mImageDatabaseId != -1 && mImage != null && (mImage.getUploadStatus() == null || !mImage.getUploadStatus().equals(SynchableConstants.UPLOAD_STATUS_UPLOADED))) {
            Image tmpImage = imageDao.getImage(mImageDatabaseId);
            if(tmpImage.mongo_id != null && !tmpImage.mongo_id.isEmpty()) {
                mImage.mongo_id = tmpImage.mongo_id;
            }
        }

        mImage.setUploadStatus(SynchableConstants.UPLOAD_STATUS_NOT_UPLOADED);
        mImage.compressToDatabase();

        if(mImage.getUid() != null && mImage.getUid() > 0) {
            imageDao.update(mImage);
        } else {
            imageDao.insert(mImage);
        }

        Toast.makeText(PhotoDetailActivity.this, "Image Saved", Toast.LENGTH_SHORT).show();

        ImageMetaDataUploader.sendAllPendingMetaDataObjects(getApplicationContext());

        toggleEdit(false);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(IMAGE_MONGO_ID, mImageMongoId);
        outState.putLong(IMAGE_DATABASE_ID, mImageDatabaseId);

        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        SDGLog.d(TAG, "inflating options menu");

        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_photo_detail, menu);

        mOptionsMenu = menu;

        if(mImageDatabaseId == -1) {
            mOptionsMenu.findItem(R.id.menu_item_image_status).setVisible(false);
        }

        updateMetaData();

        return true;
    }

    private boolean hasEditedImageChanged() {
        // Make a copy of the current image
        Image test1 = new Image(mImage);
        // Apply edits
        test1.annotation = mAnnotation.getText().toString();
        Image test2 = new Image(mImageOld);
        if (!test1.annotation.equals(test2.annotation)) return true;
        Collections.sort(test1.tags);
        Collections.sort(test2.tags);
        if (!test1.tags.equals(test2.tags)) {
            return true;
        }
        Collections.sort(test1.categories);
        Collections.sort(test2.categories);
        if (!test1.categories.equals(test2.categories)) return true;
        return false;
    }

    @Override
    public void onBackPressed() {
        if (!mEditMode) {
            super.onBackPressed();
            return;
        }
        if (!hasEditedImageChanged()) {
            // Just exit edit mode
            toggleEdit(false);
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Save changes?");
        builder.setNegativeButton("No", (dialog, which) -> {
            cancelEdit(null);
            dialog.dismiss();
            toggleEdit(false);
        });
        builder.setPositiveButton("Yes", (dialog, which) -> {
            save();
            dialog.dismiss();
            toggleEdit(false);
        });
        builder.show();
    }

    /** See if the user changed the tags, categories, or annotation. */
    private boolean isSameImage(Image test1, Image test2) {
        if (test1 == test2) return true;
        if (!test1.tags.equals(test2.tags)) {
            SDGLog.e(TAG, "ZZZ tags are different");
            return false;
        }
        if (!test1.categories.equals(test2.categories)) return false;
        if (!test1.annotation.equals(test2.annotation)) return false;
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
       if(item.getItemId() == android.R.id.home) {
           SDGLog.d(TAG, "Heading back...");
           onBackPressed();
       } else if(item.getItemId() == R.id.menu_item_image_status) {
           Intent intent = new Intent(this, ImageStatus.class);

           intent.putExtra(PhotoDetailActivity.IMAGE_DATABASE_ID, mImageDatabaseId);
           intent.putExtra(PhotoDetailActivity.IMAGE_MONGO_ID, mImageMongoId);

           startActivity(intent);
       } else if(item.getItemId() == R.id.menu_item_delete) {
           AlertDialog dialog;

           AlertDialog.Builder builder = new AlertDialog.Builder(PhotoDetailActivity.this);

           builder.setTitle("Delete Image?");
           builder.setMessage("Do you wish to delete this image?");
           builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
               @Override
               public void onClick(DialogInterface dialogInterface, int i) {
                   GenericPhotoApplication.getInstance().getDb().imageDao().delete(mImage);

                   if(mImage.mongo_id != null) {
                       deleteRemoteImage(mImage);
                   } else {
                       onBackPressed();
                   }
               }
           });
           builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
               @Override
               public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.dismiss();
               }
           });

           dialog = builder.create();

           dialog.show();
       } else if(item.getItemId() == R.id.menu_item_share) {
           Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, "New photo from PhotoTag: " + GenericPhotoApplication.getInstance().getWebAppImageUrl() + mImageMongoId );
            sendIntent.setType("text/plain");
            startActivity(sendIntent);
       }

        return false;
    }



    @Override
    public void onPause() {
        super.onPause();

        SDGLog.d(TAG, "onPause called");
    }

    @Override
    public void onResume() {
        super.onResume();

        // TODO: Why do this here instead of onCreate() ?
        if(!mImageLoaded) {
            updateImageDetails();
        }

        if (mEditMode) {
            SDGLog.d(TAG, "Reinitializing scanner since we're editing");
            initScanner(mScanningCallback);
        }
    }

    /*
    public void addCategory(View view) {

        AlertDialog dialog;

        AlertDialog.Builder builder = new AlertDialog.Builder(PhotoDetailActivity.this);
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

        builder.setItems(items, (dialogInterface, i) -> {
            ImageCategory cat = GenericPhotoApplication.getInstance().categories.get(i);
            if (!mImage.categories.contains(cat)) {
                mImage.categories.add(cat);
                runOnUiThread(() -> updateMetaData());
            }
            dialogInterface.dismiss();
        });

        dialog = builder.create();
        SDGLog.d(TAG, "Showing dialog...");
        dialog.show();
    } */

    Function1<? super  ImageCategory, Unit> updateImageCategories = new Function1<Object, Unit>() {
        @Override
        public Unit invoke(Object category) {
            if(!mImage.categories.contains((ImageCategory) category)) {
                mImage.categories.add((ImageCategory) category);
                runOnUiThread(() -> updateMetaData());
            }
            return null;
        }

    };

    public void addOwner(View view) {
    }

    private void deleteRemoteImage(Image image) {
        Synchronizer.sendString(getApplicationContext(),
                SynchableConstants.UPLOAD_METHOD.UPLOAD_METHOD_DELETE,
                GenericPhotoApplication.getInstance().getImageApiUrl() + image.mongo_id,
                "", new IUploadCallback() {

                    @Override
                    public void OnSuccess(String s) {
                        onBackPressed();
                    }

                    @Override
                    public void OnFailure(String s) {
                        Toast.makeText(PhotoDetailActivity.this, "Could not delete remote image: " + s, Toast.LENGTH_SHORT).show();
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

    GenericScanningCallback mScanningCallback = new GenericScanningCallback() {
        @Override
        public void scanAvailable(String scanData, Constants.ScannerType scannerType) {
            SDGLog.d(TAG, "scanAvailable(" + scanData + ", " + scannerType);
            if (Utilities.shouldIgnoreScan(PhotoDetailActivity.this, scanData, scannerType)) return;
            scanData = (scanData != null) ? scanData.trim() : "";
            if (!mImage.tags.contains(scanData)) {
                mImage.tags.add(scanData);
                updateTags();
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    displayScanOverlay(PhotoDetailActivity.this, false);
                }
            });
        }

        @Override
        public void statusMessage(String message, boolean error) {
            Toast.makeText(PhotoDetailActivity.this, message, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void cameraScanningStarted() {
            cameraScanning = true;
        }

        @Override
        public void scanningComplete() {
            cameraScanning = false;
            displayScanOverlay(PhotoDetailActivity.this, false);
        }

        @Override
        public void scannerClosed() {
            updateButtonStates();
        }

        @Override
        public void scannerConnected() {
            updateButtonStates();
        }

        @Override
        public boolean shouldUseCameraScanning() {
            return false;
        }
    };

    public void cancelEdit(View view) {
        mImage = new Image(mImageOld);
        toggleEdit(false);
    }

    public void saveEdit(View view) {
        save();
    }
    public void openMapLoc(View view){
        Uri gmmIntentUri = Uri.parse("geo:0,0?q=" + mImage.location.coordinates.get(1)+","+mImage.location.coordinates.get(0)+"(Photo)");
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");
        if (mapIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(mapIntent);
        }
        else {
            Toast.makeText(this, "Unable to launch maps application.", Toast.LENGTH_SHORT).show();
        }
    }

    // Adapted from https://stackoverflow.com/a/17789187
    public void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        //Find the currently focused view, so we can grab the correct window token from it.
        View view = getCurrentFocus();
        //If no view currently has focus, create a new one, just so we can grab a window token from it
        if (view == null) {
            view = new View(this);
        }
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
}
