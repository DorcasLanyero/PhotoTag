package com.sdgsystems.collector.photos.ui.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Build;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.hardware.camera2.CameraDevice;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import androidx.core.view.ViewCompat;

import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import com.sdgsystems.blueloggerclient.SDGLog;
import com.sdgsystems.collector.photos.Constants;
import com.sdgsystems.collector.photos.GenericPhotoApplication;
import com.sdgsystems.collector.photos.Utilities;
import com.sdgsystems.collector.photos.data.AppDatabase;
import com.sdgsystems.collector.photos.data.dao.ImageDao;
import com.sdgsystems.collector.photos.data.model.Image;
import com.sdgsystems.collector.photos.data.model.ImageFile;
import com.sdgsystems.collector.photos.scanning.GenericScanningCallback;
import com.sdgsystems.collector.photos.R;
import com.sdgsystems.collector.photos.scanning.LocalScanManager;
import com.sdgsystems.collector.photos.ui.view.AutoFitTextureView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static com.sdgsystems.collector.photos.Constants.ScannerType.SCANNER_TYPE_RFID;
import static com.sdgsystems.collector.photos.Utilities.startTopLevelActivity;
import static com.sdgsystems.collector.photos.ui.activity.ImageSetupActivity.PREF_DEFAULT_DESCRIPTION;
import static com.sdgsystems.collector.photos.ui.activity.ImageSetupActivity.PREF_DEFAULT_TAGS;
import static com.sdgsystems.idengine.internal.Debug.debug;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class ImageCaptureActivity_camera2 extends ScanningActivity implements View.OnClickListener {

    private ArrayList<String> tags, categories;
    private String description;

    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "ImageCaptureActivity_c2";

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    /**
     * This is the output file for our picture.
     */
    private boolean startingSetupActivity = false;


    private String scannedTag = null;

    private FloatingActionButton imageThumbnailButton;
    private BottomNavigationView bottomNavigationView;

    private CameraCharacteristics mCameraCharacteristics;
    private int mCameraOrientation;

    private float mScaleFactor = 1.0f;
    float mMaxZoom = 1.0f;
    private ScaleGestureDetector mScaleDetector;
    private Context context;
    private View captureImageButton;
    private MeteringRectangle lastFocusRectangle;

    // Timestamp
    private long mLastPhaseStarted = 0;

    // Time limit on capture phases: if autofocus or autoexposure takes longer than this
    // to settle, just try moving on
    private static final long CAPTURE_PHASE_TIME_LIMIT = 2000;

    @Override
    public void onCreate(Bundle savedInstanceState) {

        context = this;
        tags = new ArrayList<>();
        categories = new ArrayList<>();
        description = "";

        super.onCreate(savedInstanceState);

        mScaleDetector = new ScaleGestureDetector(this, new ScaleListener());

        mFile = new File(getExternalFilesDir(null), "pic.jpg");

        setContentView(R.layout.activity_image_capture_camera2);
        setupTextureView();
        //requestGpsPermission();

        GenericPhotoApplication.getInstance().requestLocationUpdates(true);

        captureImageButton = findViewById(R.id.capture_image);
        captureImageButton.setOnClickListener(this);
        captureImageButton.setVisibility(View.INVISIBLE);
        findViewById(R.id.imageSetup).setOnClickListener(this);
        findViewById(R.id.restore_autofocus).setOnClickListener(this);
        imageThumbnailButton = (FloatingActionButton) findViewById(R.id.imageThumbnail);
        imageThumbnailButton.setOnClickListener(this);
        imageThumbnailButton.hide();

        updateTagLists();
        description = PreferenceManager.getDefaultSharedPreferences(this).getString(PREF_DEFAULT_DESCRIPTION, "");

        // getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        // getSupportActionBar().setHomeButtonEnabled(true);

        findViewById(R.id.restore_autofocus).setVisibility(View.INVISIBLE);

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
                    startTopLevelActivity(context, ThumbnailListActivity.class);
                } else if (id == R.id.action_camera) {
                } else if (id == R.id.action_tags) {
                    startTopLevelActivity(context, ImageSetupActivity.class);
                }
                return true;
            }
        });
        bottomNavigationView.setSelectedItemId(R.id.action_camera);
    }

    @Override
    public void onBackPressed() {
        // Finish the whole app
        finishAffinity();
    }

    private void updateTagLists() {
        //get all of the assigned tags and categories and display them
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        categories = new ArrayList<>(preferences.getStringSet(ImageSetupActivity.PREF_DEFAULT_CATEGORIES, new HashSet<String>()));
        tags = new ArrayList<>(preferences.getStringSet(PREF_DEFAULT_TAGS, new HashSet<String>()));

        if (scannedTag != null) {
            if (isSingleTagMode()) {
                tags.remove(getLastTagScanned());
            }
            if (!tags.contains(scannedTag)) {
                tags.add(scannedTag);
            }
            setLastTagScanned(scannedTag);

            SharedPreferences.Editor editor = preferences.edit();
            editor.putStringSet(PREF_DEFAULT_TAGS, new HashSet<String>(tags));
            editor.commit();

            scannedTag = null;
        }

        updateChips(R.id.tagCloud, "#aa81c784", "#aaffffff", tags, new IDeleteElementCallback() {
            @Override
            public void delete(String deleteString) {
                tags.remove(deleteString);
            }
        });

        updateChips(R.id.categoryCloud, "#aa64b5f6", "#aaffffff", categories, new IDeleteElementCallback() {
            @Override
            public void delete(String deleteString) {
                categories.remove(deleteString);
            }
        });
    }

    protected void updateChips(final int cloudLayout, final String backgroundColor,
                               final String textColor, final List<String> chipList,
                               final IDeleteElementCallback deleteCallback) {
        super.updateChips(cloudLayout, backgroundColor, textColor, chipList, getLastTagScanned(), null);
    }

    @Override
    public void onResume() {
        super.onResume();

        bottomNavigationView.setSelectedItemId(R.id.action_camera);

        startBackgroundThread();

        startingSetupActivity = false;

        initScanner(mScanningCallback);
        SDGLog.d(TAG, "initialized scanner after resume");

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (mTextureView.isAvailable()) {
            SDGLog.d(TAG, "Trying to open the camera directly");
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            SDGLog.d(TAG, "Starting the surface texture listener to open the camera");
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }

        // Reset zoom so the zoom level doesn't jump when we pinch.
        mScaleFactor = 1.0f;

        updateTagLists();
        description = PreferenceManager.getDefaultSharedPreferences(this).getString(PREF_DEFAULT_DESCRIPTION, "");

        ImageDao imageDao = GenericPhotoApplication.getInstance().getDb().imageDao();

        SDGLog.d(TAG, "resuming, checking for last captured image");
        if(mLastImage == null || imageDao.getImage((long) mLastImage.getUid()) == null) {
            imageThumbnailButton.hide();
        } else {
            SDGLog.d(TAG, "found the image hanging around: " + mLastImage.getUid());
        }
    }

    @Override
    public void onPause() {
        // this also returns state to STATE_PREVIEW
        unlockFocus();
        closeCamera();
        stopBackgroundThread();

        super.onPause();

        closeScanner();
    }


    @Override
    public void onClick(View view) {

        SDGLog.d(TAG, "onclick: " + view.getId());


        switch (view.getId()) {
            case R.id.capture_image: {

                SDGLog.d(TAG, "*** CAPTURE IMAGE");

                int limit = Utilities.getOfflineFileLimit(context);
                if(limit > 0) {
                    AppDatabase db = ((GenericPhotoApplication) context.getApplicationContext()).getDb();
                    int unsent = db.imageFileDao().countUnsent();
                    if(unsent >= limit) {
                        SDGLog.d(TAG, "Unsent images: " + unsent + "/" + limit);
                        Utilities.showText(context, "Too many offline photos! Connect to the Internet and log in to upload saved photos.");
                        break;
                    }
                }
                takePicture();

                break;
            }
            case R.id.imageSetup: {
                startActivity(new Intent(this, ImageSetupActivity.class));

                break;
            }
            case R.id.imageThumbnail: {
                SDGLog.d(TAG, "Clicked the thumbnail");

                Intent detail = new Intent(ImageCaptureActivity_camera2.this, PhotoDetailActivity.class);
                detail.putExtra(PhotoDetailActivity.IMAGE_DATABASE_ID, mLastImage.getUid());
                detail.putExtra(PhotoDetailActivity.IMAGE_MONGO_ID, mLastImage.mongo_id);
                startActivity(detail);
                break;
            }
            case R.id.restore_autofocus: {
                SDGLog.d(TAG, "*** RESTORE AUTOFOCUS");
                boolean error = false;
                try {
                    cancelAutofocus();
                    tryAutofocus(null);
                } catch (CameraAccessException e) {
                    error = true;
                } catch (IllegalStateException e) {
                    SDGLog.w(TAG, "Restored autofocus after camera closed");
                }

                if(!error) findViewById(R.id.restore_autofocus).setVisibility(View.INVISIBLE);
            }
        }
    }

    private void enableCameraPreview() {
        openCamera(0,0,500);
    }

    /** Barcode Scanning **/

    GenericScanningCallback mScanningCallback = new GenericScanningCallback() {
        @Override
        public void scanAvailable(final String scanData, final Constants.ScannerType scannerType) {
            SDGLog.d(TAG, "scanAvailable(" + scanData + ", " + scannerType);
            if (Utilities.shouldIgnoreScan(ImageCaptureActivity_camera2.this, scanData, scannerType)) return;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    SDGLog.d(TAG, "got scan: " + scanData);

                    displayScanOverlay(ImageCaptureActivity_camera2.this, false);

                    SDGLog.d(TAG, "updating list with: " + scanData);
                    setScannedTag(scanData);

                    //only 'loop' the scanner if we are scanning w/ RFID
                    //TODO: hide this behind a setting so that nfc + rfid works correctly
                    if(scannerType == Constants.ScannerType.SCANNER_TYPE_RFID) {
                        if (!cameraClosed) {
                            takePicture();
                        }

                        triggerScanning(true);
                    }
                }
            });
        }

        @Override
        public void statusMessage(String message, boolean error) {
            Toast.makeText(ImageCaptureActivity_camera2.this, message, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void cameraScanningStarted() {
            cameraScanning = true;
        }

        @Override
        public void scanningComplete() {
            //Log.d(TAG, "scanning complete");
            cameraScanning = false;
        }

        @Override
        public void scannerClosed() {
            SDGLog.d(TAG, "scanner closed");
        }

        @Override
        public void scannerConnected() {
            if (LocalScanManager.getInstance().getScannerType().equals(SCANNER_TYPE_RFID)) {
                //This implementation is specifically for continuous RFID scanning in the background, we do NOT
                //support barcode scanning in this manner...
                triggerScanning(true);
            }
        }

        @Override
        public boolean shouldUseCameraScanning() {
            return false;
        }
    };

    private void setScannedTag(String scanData) {
        scannedTag = scanData;
        updateTagLists();
    }

    private void openCamera(final int width, final int height, int delayMillis) {

        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                openCamera(width, height);
            }
        }, delayMillis);
    }

    private void openCamera(int width, int height) {
        SDGLog.d(TAG, "*** OPEN CAMERA");

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            requestCameraPermission();
            return;
        }
        setUpCameraOutputs(width, height);
        configureTransform(width, height);

        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
            mCameraCharacteristics = manager.getCameraCharacteristics(mCameraId);
            mCameraOrientation = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        } catch (@SuppressLint("NewApi") CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    boolean cameraClosed = false;

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        SDGLog.d(TAG, "*** CLOSE CAMERA");

        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
            // Hide the capture image button
            captureImageButton.setVisibility(View.INVISIBLE);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }


    private String getFlashMode(Context c) {
        return PreferenceManager.getDefaultSharedPreferences(c).getString(Constants.PREF_FLASH_MODE, Constants.PREF_FLASH_MODE_AUTO);
    }

    private void takePicture() {
        SDGLog.d(TAG, "*** TAKE PICTURE");
        captureImageButton.setVisibility(View.INVISIBLE);
        mLastPhaseStarted = System.currentTimeMillis();
        lockFocus();
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        SDGLog.d(TAG, "Key Event: " + keyCode);
        switch (keyCode) {
            case 103:
                if(!startingSetupActivity) {
                    startingSetupActivity = true;
                    startActivity(new Intent(this, ImageSetupActivity.class));
                }

                return true;
            case 104:
            case KeyEvent.KEYCODE_MENU:
                //Got the secondary Trigger
                if(cameraClosed) {
                    enableCameraPreview();
                } else {
                    takePicture();
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
                onBackPressed();
                return true;
        }
        return false;
    }

    //Camera2 support code:
    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final int REQUEST_LOCATION_PERMISSION = 1;

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    /**
     * Camera state: Showing camera preview.
     */
    private static final int STATE_PREVIEW = 0;

    /**
     * Camera state: Waiting for the focus to be locked.
     */
    private static final int STATE_WAITING_LOCK = 1;

    /**
     * Camera state: Waiting for the exposure to be precapture state.
     */
    private static final int STATE_WAITING_PRECAPTURE = 2;

    /**
     * Camera state: Waiting for the exposure state to be something other than precapture.
     */
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;

    /**
     * Camera state: Picture was taken.
     */
    private static final int STATE_PICTURE_TAKEN = 4;

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private TextureView.SurfaceTextureListener mSurfaceTextureListener;


    /**
     * ID of the current {@link CameraDevice}.
     */
    private String mCameraId;

    /**
     * An {@link AutoFitTextureView} for camera preview.
     */
    private AutoFitTextureView mTextureView;

    /**
     * A {@link CameraCaptureSession } for camera preview.
     */
    private CameraCaptureSession mCaptureSession;

    /**
     * A reference to the opened {@link CameraDevice}.
     */
    private CameraDevice mCameraDevice;

    /**
     * The {@link Size} of camera preview.
     */
    private Size mPreviewSize;

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;

            finish();
        }

    };

    /**
     * An {@link ImageReader} that handles still image capture.
     */
    private ImageReader mImageReader;

    /**
     * This is the output file for our picture.
     */
    private File mFile;

    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {

            SDGLog.d(TAG, "New image available!");

            runOnUiThread(() -> captureImageButton.setVisibility(View.VISIBLE));

            //mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage(), null, getApplicationContext(), tags, categories, description, imageSaverCallback, getExifOrientationForDeviceRotation(mOrientationRounded)));
        }

    };

    private Image mLastImage
            ;
    IImageSaverCallback imageSaverCallback = new IImageSaverCallback() {
        @Override
        public void imageSaved(final Image savedImage) {

            ImageDao imageDao = GenericPhotoApplication.getInstance().getDb().imageDao();

            mLastImage = savedImage;

            ImageFile lastImageFile = imageDao.getImageFile(mLastImage.getUid());
            final String filename = lastImageFile.filename;

            SDGLog.d(TAG, "Got image: " + filename);

            if(new File(filename).exists()) {

                Bitmap bm = BitmapFactory.decodeFile(filename);

                int height, width;

                // Use a rendered FAB to get width and height
                if(bm.getWidth() > bm.getHeight()) {
                    height = findViewById(R.id.capture_image).getHeight();
                    width = (height * bm.getWidth()) / bm.getHeight();
                }
                else {
                    width = findViewById(R.id.capture_image).getWidth();
                    height = (width * bm.getHeight()) / bm.getWidth();
                }

                Bitmap resized = Bitmap.createScaledBitmap(bm,
                                                           width,
                                                           height,
                                                           true);

                Bitmap rounded = Utilities.getRoundedRectBitmap(resized, height);
                final Bitmap roundedRotated = Utilities.rotateBitmap(rounded, savedImage.getExifRotation());

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        imageThumbnailButton.setImageBitmap(roundedRotated);
                        imageThumbnailButton.show();
                    }
                });

            }
        }
    };

    /**
     * {@link CaptureRequest.Builder} for the camera preview
     */
    private CaptureRequest.Builder mPreviewRequestBuilder;

    /**
     * {@link CaptureRequest} generated by {@link #mPreviewRequestBuilder}
     */
    private CaptureRequest mPreviewRequest;

    /**
     * The current state of camera state for taking pictures.
     *
     * @see #mCaptureCallback
     */
    private int mState = STATE_PREVIEW;

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * Whether the current camera device supports Flash or not.
     */
    private boolean mFlashSupported;

    /**
     * Orientation of the camera sensor
     */
    private int mSensorOrientation;
    private Sensor mDeviceOrientation;

    // Used only for the callback below.
    private boolean tryingAutofocus = false;
    private int inactiveCount = 0;

    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
     */
    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            Integer afState = null;
            afState = result.get(CaptureResult.CONTROL_AF_STATE);

            if(afState != null) {
                //Log.v(TAG, "*** AF STATE " + afState);
            }

            switch (mState) {
                case STATE_PREVIEW: {
                    // If AF state is inactive,
                    if(afState != null) {
                        if(afState == CaptureResult.CONTROL_AF_STATE_INACTIVE && (!tryingAutofocus || inactiveCount > 50)) {
                            try {
                                SDGLog.d(TAG, "RETURNING TO PREVIOUS RECT");
                                cancelAutofocus();
                                tryAutofocus(lastFocusRectangle);
                                tryingAutofocus = true;
                                inactiveCount = 0;
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }
                        else if(afState == CaptureResult.CONTROL_AF_STATE_INACTIVE) {
                            inactiveCount++;
                        }
                        else if(tryingAutofocus && (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED || afState == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED)) {
                            SDGLog.d(TAG, "NO LONGER TRYING AUTOFOCUS");
                            tryingAutofocus = false;
                        }

                        if(afState != CaptureResult.CONTROL_AF_STATE_INACTIVE) {
                            //Log.d(TAG, "Resetting inactive count");
                            inactiveCount = 0;
                        }

                        // It's passively focused, i.e. tap didn't take.
                        if(afState == CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN || afState == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED || afState == CaptureResult.CONTROL_AF_STATE_INACTIVE) {
                            //Log.d(TAG, "Hidng AF button");
                            runOnUiThread(() -> findViewById(R.id.restore_autofocus).setVisibility(View.GONE));
                        }
                        else if(afState == CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN || afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED) {
                            runOnUiThread(() -> findViewById(R.id.restore_autofocus).setVisibility(View.VISIBLE));
                        }
                    }
                    break;
                }
                case STATE_WAITING_LOCK: {
                    SDGLog.d(TAG, "*** WAITING FOR LOCK");
                    SDGLog.d(TAG, "setting autofocus state");
                    if (afState == null) {
                        SDGLog.d(TAG, "state waiting lock, autofocus was null, capturing still picture");
                        //captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState ||
                            (System.currentTimeMillis() - mLastPhaseStarted > CAPTURE_PHASE_TIME_LIMIT)
                    ) {

                        mLastPhaseStarted = System.currentTimeMillis();
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);

                        if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState = STATE_PICTURE_TAKEN;
                            SDGLog.d(TAG, "state waiting lock, capturing still picture (picture taken)");
                            captureStillPicture();
                        } else {
                            SDGLog.d(TAG, "ae state: " + aeState);
                            runPrecaptureSequence();
                        }
                    } else {
                        SDGLog.d(TAG, "waiting for AF lock to settle: " + afState);
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    SDGLog.d(TAG, "*** WAITING PRECAPTURE");
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    SDGLog.d(TAG, "*** WAITING NON PRECAPTURE");
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE || (System.currentTimeMillis() - mLastPhaseStarted > CAPTURE_PHASE_TIME_LIMIT)) {
                        mState = STATE_PICTURE_TAKEN;
                        SDGLog.d(TAG, "state waiting lock, capturing still picture: waiting nonprecapture");
                        captureStillPicture();
                    }
                    else {
                        SDGLog.v(TAG, "AE state: " + aeState);
                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @SuppressLint("NewApi")
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }

    };
    /**
     * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
     * is at least as large as the respective texture view size, and that is at most as large as the
     * respective max size, and whose aspect ratio matches with the specified value. If such size
     * doesn't exist, choose the largest one that is at most as large as the respective max size,
     * and whose aspect ratio matches with the specified value.
     *
     * @param choices           The list of sizes that the camera supports for the intended output
     *                          class
     * @param textureViewWidth  The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth          The maximum width that can be chosen
     * @param maxHeight         The maximum height that can be chosen
     * @param aspectRatio       The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = 0;
        int h = 0;
        w = aspectRatio.getWidth();
        h = aspectRatio.getHeight();

        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new ImageCaptureActivity_camera2.CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new ImageCaptureActivity_camera2.CompareSizesByArea());
        } else {
            SDGLog.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    /**
     * pinch-to-zoom
     */

    private Rect calcScaledSensorRect(float scaleFactor) {
        Rect zoom;
        try {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraId);
            Rect m = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);

            int minW = (int) (m.width() / mMaxZoom);
            int minH = (int) (m.height() / mMaxZoom);
            int newW = (int) (m.width() / scaleFactor);
            int newH = (int) (m.height() / scaleFactor);
            if (newW < minW) {
                newW = minW;
            }
            if (newH < minH) {
                newH = minH;
            }
            int centerX = (m.left + m.right) / 2;
            int centerY = (m.top + m.bottom) / 2;

            zoom = new Rect(centerX - (newW / 2),
                                 centerY - (newH / 2),
                                 centerX + (newW / 2),
                                 centerY + (newH / 2));

        } catch (CameraAccessException e) {
            throw new RuntimeException("Can't access Camera", e);
        }

        return zoom;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            mScaleFactor *= detector.getScaleFactor();

            // Don't let the object get too small or too large.
            mScaleFactor = Math.max(1.0f, Math.min(mScaleFactor, mMaxZoom));

            Rect zoom = calcScaledSensorRect(mScaleFactor);
            try {
                mPreviewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);
                mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(),
                                                    mCaptureCallback, mBackgroundHandler);
            } catch (CameraAccessException e) {
                throw new RuntimeException("can not access camera.", e);
            } catch (NullPointerException ex) {
                ex.printStackTrace();
            }

            return true;
        }
    }

    // The texture view just needs to answer taps for focus/exposure-setting.
    @SuppressLint("ClickableViewAccessibility")
    private void setupTextureView() {
        mTextureView = (AutoFitTextureView) findViewById(R.id.texture);

        mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {

            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
                SDGLog.d(TAG, "opening camera");
                openCamera(width, height, 500);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
                configureTransform(width, height);
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture texture) {
            }

        };

        mTextureView.setOnTouchListener(new View.OnTouchListener() {
            private float xOrigin, yOrigin;
            private MeteringRectangle autofocusArea;

            // We have to track touch events rather than clicks because
            // we need the location
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getPointerCount() != 1) {
                    // Handle pinch-to-zoom.
                    return mScaleDetector.onTouchEvent(event);
                }

                // Is a new touch starting?
                if(event.getAction() != MotionEvent.ACTION_UP) {
                    if(event.getAction() == MotionEvent.ACTION_DOWN) {
                        // Start a new touch
                        xOrigin = event.getX();
                        yOrigin = event.getY();
                    }
                    return true;
                }

                // From here on, the only thing left is ACTION_UP

                // discard swipes
                float xDiff = event.getX() - xOrigin;
                float yDiff = event.getY() - yOrigin;
                float squareDiff = xDiff*xDiff + yDiff*yDiff;
                float density = getResources().getDisplayMetrics().density;
                float threshold = 30 * density + 0.5f;
                if(squareDiff > threshold*threshold) {
                    return true;
                }

                // Generate the matrix for going from the camera to the preview,
                // which we'll invert to go the other way. We want a 2000x2000 square
                // with an origin in the middle, because that makes the matrix math
                // easier.
                //
                // cameraToPreview, then, assumes we're starting with the 2000x2000
                // square, and builds the operations in the opposite direction.
                Matrix cameraToPreview = new Matrix();

                // Correct for front-facingness
                boolean frontFacing = mCameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT;
                cameraToPreview.setScale(1, frontFacing ? -1 : 1);

                // Calculate the rotations required to get the camera and preview orientations to match
                int rotation = getWindowManager().getDefaultDisplay().getRotation();
                int degrees = 0;
                switch(rotation) {
                    case Surface.ROTATION_0: degrees = 0; break;
                    case Surface.ROTATION_90: degrees = 90; break;
                    case Surface.ROTATION_180: degrees = 180; break;
                    case Surface.ROTATION_270: degrees = 270; break;
                }
                int finalDegrees = (mCameraOrientation - degrees + 360) % 360;
                SDGLog.d(TAG, "Orientation of display to native/camera to screen: " + rotation + "/" + finalDegrees);

                // Do the required rotations and scales
                Utilities.convertToSquare(cameraToPreview, finalDegrees, mTextureView);

                // Inverting the matrix gets us the transform in the opposite direction
                Matrix previewToCamera = new Matrix();
                boolean inverted = cameraToPreview.invert(previewToCamera);
                if(!inverted) {
                    // Can't progress from here
                    SDGLog.e(TAG, "Failed to invert matrix");
                    return false;
                }

                //Rect sensorSize = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                Rect sensorSize = calcScaledSensorRect(mScaleFactor);
                SDGLog.d(TAG, "Active array size: " + sensorSize);

                MeteringRectangle r = getMeteringRectangle(event.getX(), event.getY(), previewToCamera, sensorSize);

                if(r == null) {
                    SDGLog.w(TAG, "Failed to get metering rect");
                }

                try {
                    cancelAutofocus();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }

                try {
                    tryAutofocus(r);
                    findViewById(R.id.restore_autofocus).setVisibility(View.VISIBLE);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }

                return false;
            }
        });
    }

    private MeteringRectangle getMeteringRectangle(float eventX, float eventY, Matrix transform, Rect sensorSize) {
        float[] event = {eventX, eventY};

        SDGLog.d(TAG, "pre-transform: " + eventX + "," + eventY);

        transform.mapPoints(event);

        float xTransformed = event[0];
        float yTransformed = event[1];

        SDGLog.d(TAG, "transformed: " + xTransformed + "," + yTransformed);

        // x and y are in the -1000,-1000,1000,1000 coordinate system, so convert
        // by calculating what percentage from the top/left of the coordinate
        // system they are and multiplying by the sensor size.
        double xFactor = (xTransformed + 1000) / 2000.0;
        double yFactor = (yTransformed + 1000) / 2000.0;

        int x = (int)(sensorSize.left + xFactor * (sensorSize.width() - 1));
        int y = (int)(sensorSize.top + yFactor * (sensorSize.height() - 1));

        SDGLog.d(TAG, "post-transform: " + x + "," + y);

        int focusRadius = 50;
        int top = y - focusRadius;
        int bottom = y + focusRadius;
        int left = x - focusRadius;
        int right = x + focusRadius;

        if(top < sensorSize.top) top = sensorSize.top;
        if(bottom > sensorSize.bottom) bottom = sensorSize.bottom;
        if(left < sensorSize.left) left = sensorSize.left;
        if(right > sensorSize.right) right = sensorSize.right;

        Rect r = new Rect(left, top, right, bottom);
        SDGLog.d(TAG, "Rect: " + r);

        if(r.top < 0 || r.bottom < 0 || r.left < 0 || r.right < 0) {
            return null;
        }

        return new MeteringRectangle(r, MeteringRectangle.METERING_WEIGHT_MAX);
    }

    private void cancelAutofocus() throws CameraAccessException {
        SDGLog.d(TAG, "*** CANCEL TAP AUTOFOCUS");
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
        mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);

        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
        mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
    }

    private void tryAutofocus(MeteringRectangle rectangle) throws CameraAccessException {
        SDGLog.d(TAG, "*** TRY AUTOFOCUS");
        tryingAutofocus = true;
        lastFocusRectangle = rectangle;
        if(rectangle != null) {
            MeteringRectangle[] regions = {rectangle};

            if(mCameraCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) > 0) {
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, regions);
            }
            else {
                SDGLog.d(TAG, "Camera doesn't support autofocus regions");
            }

            if(mCameraCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) > 0) {
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, regions);
            }
            else {
                SDGLog.d(TAG, "Camera doesn't support autoexposure regions");
            }

            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
        }
        else { // If we don't have a rectangle, go back to continuous autofocus
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, null);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, null);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
        }
    }

    private void requestGpsPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},REQUEST_LOCATION_PERMISSION
        );
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                REQUEST_CAMERA_PERMISSION);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Snackbar.make(ImageCaptureActivity_camera2.this.findViewById(R.id.photo_content_container), "Camera Permissions not granted", Snackbar.LENGTH_SHORT).show();
            } else if(grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            }
        } else if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                SDGLog.d(TAG, "Permissions not granted...");
            } else if(grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                GenericPhotoApplication.getInstance().requestLocationUpdates(true);
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }

    }


    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private void setUpCameraOutputs(int width, int height) {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }
                long desiredResolution = Utilities.getDesiredResolution(this);
                SDGLog.d(TAG, String.format("Desired resolution = %d (%d MP)", desiredResolution, desiredResolution / 1000000));
                long bestResolutionDifference = Integer.MAX_VALUE;
                Size bestSize = null;
                for (Size res : map.getOutputSizes(ImageFormat.JPEG)) {
                    int numPixels = res.getWidth() * res.getHeight();
                    long thisDifference = Math.abs(numPixels - desiredResolution);
                    if (thisDifference < bestResolutionDifference) {
                        bestResolutionDifference = thisDifference;
                        bestSize = res;
                    }
                }
                SDGLog.e(TAG, String.format("Final resolution = %d x %d = %d MP",
                        bestSize.getWidth(), bestSize.getHeight(), bestSize.getWidth() * bestSize.getHeight() / 1000000));

                mImageReader = ImageReader.newInstance(bestSize.getWidth(), bestSize.getHeight(),
                        ImageFormat.JPEG, 2);

                SDGLog.d(TAG, "setting image available listener");
                mImageReader.setOnImageAvailableListener(
                        mOnImageAvailableListener, mBackgroundHandler);

                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
                int displayRotation = getWindowManager().getDefaultDisplay().getRotation();
                SDGLog.d(TAG, "Display rotation: " + displayRotation + " activity rotation: " + mOrientationRounded);

                if(displayRotation != mOrientationRounded) {
                    displayRotation = mOrientationRounded;
                    orientationChanged();
                }

                //noinspection ConstantConditions
                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

                mMaxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);

                boolean swappedDimensions = false;
                switch (displayRotation) {
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                            swappedDimensions = true;
                        }
                        break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                            swappedDimensions = true;
                        }
                        break;
                    default:
                        SDGLog.e(TAG, "Display rotation is invalid: " + displayRotation);
                }

                Point displaySize = new Point();
                getWindowManager().getDefaultDisplay().getSize(displaySize);
                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;
                int maxPreviewWidth = displaySize.x;
                int maxPreviewHeight = displaySize.y;

                if (swappedDimensions) {
                    rotatedPreviewWidth = height;
                    rotatedPreviewHeight = width;
                    maxPreviewWidth = displaySize.y;
                    maxPreviewHeight = displaySize.x;
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                        maxPreviewHeight, bestSize);

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                int orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    mTextureView.setAspectRatio(
                            mPreviewSize.getWidth(), mPreviewSize.getHeight());
                } else {
                    mTextureView.setAspectRatio(
                            mPreviewSize.getHeight(), mPreviewSize.getWidth());
                }

                // Check if the flash is supported.
                Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                mFlashSupported = available == null ? false : available;

                mCameraId = cameraId;
                return;
            }
        } catch (@SuppressLint("NewApi") CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            e.printStackTrace();
        }
    }

    @Override
    protected void orientationChanged() {
        super.orientationChanged();

        updateFabIconOrientation();
    }

    private void updateFabIconOrientation() {

        SDGLog.d(TAG, "Orientation: " + mOrientationRounded);

        getSupportActionBar().setShowHideAnimationEnabled(false);

        if(mOrientationRounded == LANDSCAPE_LEFT) {
            getSupportActionBar().hide();

            rotateFabClockwise((FloatingActionButton) findViewById(R.id.capture_image));
            rotateFabClockwise((FloatingActionButton) findViewById(R.id.imageSetup));
            rotateFabClockwise((FloatingActionButton) findViewById(R.id.restore_autofocus));
        } else if(mOrientationRounded == LANDSCAPE_RIGHT) {
            getSupportActionBar().hide();

            rotateFabCounterClockwise((FloatingActionButton) findViewById(R.id.capture_image));
            rotateFabCounterClockwise((FloatingActionButton) findViewById(R.id.imageSetup));
            rotateFabCounterClockwise((FloatingActionButton) findViewById(R.id.restore_autofocus));
        } else {
            getSupportActionBar().show();

            rotateFabToZero((FloatingActionButton) findViewById(R.id.capture_image));
            rotateFabToZero((FloatingActionButton) findViewById(R.id.imageSetup));
            rotateFabToZero((FloatingActionButton) findViewById(R.id.restore_autofocus));
        }
    }

    private void rotateFabClockwise(FloatingActionButton fab) {
        ViewCompat.animate(fab)
                .rotation(90.0F)
                .withLayer()
                .setDuration(700L)
                .setInterpolator(new OvershootInterpolator(0.0F))
                .start();
    }

    private void rotateFabCounterClockwise(FloatingActionButton fab) {
        ViewCompat.animate(fab)
                .rotation(-90.0F)
                .withLayer()
                .setDuration(700L)
                .setInterpolator(new OvershootInterpolator(0.0F))
                .start();
    }

    private void rotateFabToZero(FloatingActionButton fab) {
        ViewCompat.animate(fab)
                .rotation(0.0F)
                .withLayer()
                .setDuration(700L)
                .setInterpolator(new OvershootInterpolator(0.0F))
                .start();
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    private void createCameraPreviewSession() {
        SDGLog.d(TAG, "*** CREATE PREVIEW SESSION");
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            mPreviewRequestBuilder.addTarget(surface);

            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == mCameraDevice) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;

                            // Auto focus should be continuous for camera preview.
                            try {

                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                                // Flash is automatically enabled when necessary.
                                setAutoFlash(mPreviewRequestBuilder);

                                // Finally, we start displaying the camera preview.
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                        mCaptureCallback, mBackgroundHandler);

                                // Now we can capture images
                                runOnUiThread(() -> captureImageButton.setVisibility(View.VISIBLE));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // showToast("Failed");
                            SDGLog.e(TAG, "onConfigureFailed: Error configuring capture session");
                        }
                    }, null
            );


        } catch (@SuppressLint("NewApi") CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Shows a {@link Toast} on the UI thread.
     *
     * @param text The message to show
     */
    private void showToast(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ImageCaptureActivity_camera2.this, text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Configures the necessary {@link Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        if (null == mTextureView || null == mPreviewSize) {
            return;
        }
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private void lockFocus() {
        SDGLog.d(TAG, "*** LOCK FOCUS");
        if (mPreviewRequestBuilder == null) {
            SDGLog.e(TAG, "lockFocus: mPreviewRequestBuilder == null!");
            return;
        }
        if (mCaptureSession == null) {
            SDGLog.e(TAG, "lockFocus: mCaptureSession == null!");
            return;
        }
        try {
            // This is how to tell the camera to lock focus.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);

            // Tell #mCaptureCallback to wait for the lock.
            mState = STATE_WAITING_LOCK;

            SDGLog.d(TAG, "calling 'capture' on the capture session...");
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (Exception e) {
            SDGLog.e(TAG, "Error in lockFocus:", e);
        }
    }

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in {@link #mCaptureCallback} from {@link #lockFocus()}.
     */
    private void runPrecaptureSequence() {
        SDGLog.d(TAG, "*** RUN PRECAPTURE");
        try {
            // This is how to tell the camera to trigger.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_PRECAPTURE;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * {@link #mCaptureCallback} from both {@link #runPrecaptureSequence()}.
     */
    private void captureStillPicture() {
        SDGLog.d(TAG, "*** CAPTURE STILL PICTURE");

        try {
            if (null == mCameraDevice) {
                return;
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            setAutoFlash(captureBuilder);

            // Orientation
            int rotation = getWindowManager().getDefaultDisplay().getRotation();

            SDGLog.d(TAG, "Display rotation: " + rotation + " activity rotation: " + mOrientationRounded);
            rotation = mOrientationRounded;

            if(rotation != mOrientationRounded) {
                rotation = mOrientationRounded;
                orientationChanged();
            }

            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));

            Rect zoom = calcScaledSensorRect(mScaleFactor);
            captureBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);

            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {

                    Utilities.playShutterClick(getApplicationContext());

                    //Snackbar.make(ImageCaptureActivity_camera2.this.findViewById(R.id.photo_content_container), "Captured image", Snackbar.LENGTH_SHORT).show();
                    showToast("Captured image");

                    SDGLog.d(TAG, "capturing file: " + mFile.toString());
                    unlockFocus();
                }
            };

            mCaptureSession.stopRepeating();
            mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves the JPEG orientation from the specified screen rotation.
     *
     * @param rotation The screen rotation.
     * @return The JPEG orientation (one of 0, 90, 270, and 360)
     */
    private int getOrientation(int rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate_left JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate_left the JPEG 180 degrees.
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private void unlockFocus() {
        SDGLog.d(TAG, "*** UNLOCK FOCUS");
        try {
            // Reset the auto-focus trigger
            if(mPreviewRequestBuilder == null || mCaptureSession == null) {
                SDGLog.w(TAG, "Unlocked focus prior to camera setup");
                return;
            }

            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            setAutoFlash(mPreviewRequestBuilder);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);

            // After this, the camera will go back to the normal state of preview.
            mState = STATE_PREVIEW;
            mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (RuntimeException e) {
            // camera already closed
        }
    }

    /**
     * Use this method if you're going to call capture() or setRepeatingRequest() yourself
     * @param requestBuilder
     */
    private void setAutoFlash(CaptureRequest.Builder requestBuilder) {
        if (mFlashSupported) {
            SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            int flashMode = Utilities.getCamera2FlashModeForPref(getFlashMode(getApplicationContext()));
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, flashMode);
        }
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
                // We cast here to ensure the multiplications won't overflow
                return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                        (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_image_capture, menu);
        return true;
    }
}
