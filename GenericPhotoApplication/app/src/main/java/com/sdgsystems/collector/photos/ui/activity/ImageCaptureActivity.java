package com.sdgsystems.collector.photos.ui.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.hardware.Camera;
import android.preference.PreferenceManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.sdgsystems.blueloggerclient.SDGLog;
import com.sdgsystems.collector.photos.Constants;
import com.sdgsystems.collector.photos.GenericPhotoApplication;
import com.sdgsystems.collector.photos.Utilities;
import com.sdgsystems.collector.photos.data.dao.ImageDao;
import com.sdgsystems.collector.photos.data.model.Image;
import com.sdgsystems.collector.photos.scanning.GenericScanningCallback;
import com.sdgsystems.collector.photos.R;
import com.sdgsystems.collector.photos.scanning.LocalScanManager;
import com.sdgsystems.collector.photos.ui.view.CameraPreview;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static com.sdgsystems.collector.photos.Constants.ScannerType.SCANNER_TYPE_RFID;
import static com.sdgsystems.collector.photos.Utilities.startTopLevelActivity;
import static com.sdgsystems.collector.photos.ui.activity.ImageSetupActivity.PREF_DEFAULT_DESCRIPTION;
import static com.sdgsystems.collector.photos.ui.activity.ImageSetupActivity.PREF_DEFAULT_TAGS;
import static com.sdgsystems.idengine.internal.Debug.debug;

public class ImageCaptureActivity extends ScanningActivity implements View.OnClickListener {

    private ArrayList<String> tags, categories;
    private String description;

    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "ImageCaptureActivity";

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

    @Override
    public void onCreate(Bundle savedInstanceState) {

        tags = new ArrayList<>();
        categories = new ArrayList<>();
        description = "";

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_image_capture);

        GenericPhotoApplication.getInstance().requestLocationUpdates(true);

        findViewById(R.id.capture_image).setOnClickListener(this);
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
                    startTopLevelActivity(ImageCaptureActivity.this, ThumbnailListActivity.class);
                } else if (id == R.id.action_camera) {
                } else if (id == R.id.action_tags) {
                    startTopLevelActivity(ImageCaptureActivity.this, ImageSetupActivity.class);
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
        categories = new ArrayList<>(PreferenceManager.getDefaultSharedPreferences(this).getStringSet(ImageSetupActivity.PREF_DEFAULT_CATEGORIES, new HashSet<String>()));
        tags = new ArrayList<>(PreferenceManager.getDefaultSharedPreferences(this).getStringSet(PREF_DEFAULT_TAGS, new HashSet<String>()));

        if (scannedTag != null) {
            if (isSingleTagMode()) {
                tags.remove(getLastTagScanned());
            }
            if (!tags.contains(scannedTag)) {
                tags.add(scannedTag);
            }
            setLastTagScanned(scannedTag);

            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(ImageCaptureActivity.this).edit();
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

        SDGLog.d(TAG, "Using camera1 interface to open camera");
        openCamera(0, 0, 500);

        updateTagLists();

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
        closeCamera(false);
        stopBackgroundThread();
        super.onPause();
        closeScanner();


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


    @Override
    public void onClick(View view) {

        SDGLog.d(TAG, "onclick: " + view.getId());


        switch (view.getId()) {
            case R.id.capture_image: {

                SDGLog.d(TAG, "capture image clicked");

                if(cameraClosed ) {
                    SDGLog.d(TAG, "" + cameraClosed);
                    enableCameraPreview();
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

                Intent detail = new Intent(ImageCaptureActivity.this, PhotoDetailActivity.class);
                detail.putExtra(PhotoDetailActivity.IMAGE_DATABASE_ID, mLastImage.getUid());
                detail.putExtra(PhotoDetailActivity.IMAGE_MONGO_ID, mLastImage.mongo_id);
                startActivity(detail);
                break;
            }
            case R.id.restore_autofocus: {
                tryAutofocus(null);
                findViewById(R.id.restore_autofocus).setVisibility(View.INVISIBLE);
            }
        }
    }

    private void enableCameraPreview() {
        openCamera(0,0,500);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_image_capture, menu);
        return true;
    }

    /** Barcode Scanning **/

    GenericScanningCallback mScanningCallback = new GenericScanningCallback() {
        @Override
        public void scanAvailable(final String scanData, final Constants.ScannerType scannerType) {
            SDGLog.d(TAG, "scanAvailable(" + scanData + ", " + scannerType);
            if (Utilities.shouldIgnoreScan(ImageCaptureActivity.this, scanData, scannerType)) return;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    SDGLog.d(TAG, "got scan: " + scanData);

                    displayScanOverlay(ImageCaptureActivity.this, false);

                    if(!tags.contains(scanData)) {
                        SDGLog.d(TAG, "updating list with: " + scanData);
                        setScannedTag(scanData);
                    } else {
                        SDGLog.d(TAG, "tag already scanned...");
                    }

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
            Toast.makeText(ImageCaptureActivity.this, message, Toast.LENGTH_SHORT).show();
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


    /**
     *
     * Camera 1 stuff:
     */

    Camera c = null;

    /** A safe way to get an instance of the Camera object. */
    public Camera getCameraInstance(){

        if(c != null) {
           return c;
        }

        try {
            c = Camera.open(); // attempt to get a Camera instance
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
            SDGLog.d(TAG, "Couldn't open camera: " + e.getMessage());
            e.printStackTrace();
        }
        return c; // returns null if camera is unavailable
    }

    FrameLayout preview_frame;
    CameraPreview preview;

    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                REQUEST_CAMERA_PERMISSION);
    }

    private void openCamera(final int width, final int height, int delayMillis) {
        SDGLog.d(TAG, "Open camera");

        SDGLog.d(TAG,"Checking permission");
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            requestCameraPermission();
            return;
        }

        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                openCamera(width, height);
            }
        }, delayMillis);

        Camera c = getCameraInstance();
        Camera.Parameters params = c.getParameters();
        List<Camera.Size> sizes = params.getSupportedPictureSizes();

        long desiredResolution = Utilities.getDesiredResolution(this);
        SDGLog.d(TAG, String.format("Desired resolution = %d (%d MP)", desiredResolution, desiredResolution / 1000000));
        long bestResolutionDifference = Integer.MAX_VALUE;
        Camera.Size preferredSize = null;
        for (Camera.Size res : sizes) {
            int numPixels = res.width * res.height;
            long thisDifference = Math.abs(numPixels - desiredResolution);
            if (thisDifference < bestResolutionDifference) {
                bestResolutionDifference = thisDifference;
                preferredSize = res;
            }
        }
        SDGLog.e(TAG, String.format("Final resolution = %d x %d = %d MP",
                preferredSize.width, preferredSize.height, preferredSize.width * preferredSize.height / 1000000));

        params.setPictureSize(preferredSize.width, preferredSize.height);
        c.setParameters(params);
    }

    private void openCamera(int width, int height) {
            setupPreviewFrame(width, height);

            cameraClosed = false;
    }

    boolean cameraClosed = false;

    /**
     * Closes the current {@link Camera}.
     */
    private void closeCamera(boolean scheduleRestart) {

        if (c != null) {
            SDGLog.d(TAG, "Released camera");
            c.release();
        }

        c = null;

        if(preview_frame != null) {
            preview_frame.removeAllViews();
        }

        cameraClosed = true;

        if(scheduleRestart) openCamera(0, 0, 500);
    }

    // For tap-to-focus
    @SuppressLint("ClickableViewAccessibility")
    private void setupPreviewFrame(int width, int height) {
        preview_frame = (FrameLayout) findViewById(R.id.preview_frame);

        if(getCameraInstance() != null) {
            setCameraOrientation(this, getCameraInstance());

            preview = new CameraPreview(this, getCameraInstance());
            preview_frame.addView(preview);
        } else {
            SDGLog.d(TAG, "couldn't open the camera, setting it to closed for attempted restart");
            closeCamera(true);
        }

        preview_frame.setOnTouchListener(new View.OnTouchListener() {
            private float xOrigin, yOrigin;
            private Camera.Area autofocusArea;
            private Camera c = getCameraInstance();

            // We have to track touch events rather than clicks because
            // we need the location
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                //TODO: allow pinch-to-zoom with a gesture-detector?

                if(event.getPointerCount() != 1) {
                    // Ignore multi-touch events
                    return true;
                }

                if(c.getParameters().getMaxNumFocusAreas() < 1) {
                    // Focus areas not supported
                    return true;
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

                Camera.CameraInfo info =
                        new Camera.CameraInfo();

                Camera.getCameraInfo(0, info);

                // Generate the matrix for going from the camera to the preview,
                // which we'll invert to go the other way. We want a 2000x2000 square
                // with an origin in the middle, because that makes the matrix math
                // easier.
                //
                // cameraToPreview, then, assumes we're starting with the 2000x2000
                // square, and builds the operations in the opposite direction.
                Matrix cameraToPreview = new Matrix();

                // Correct for front-facingness
                boolean frontFacing = info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT;
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
                int finalDegrees = (info.orientation - degrees + 360) % 360;
                SDGLog.d(TAG, "Orientation of display to native/camera to screen: " + rotation + "/" + finalDegrees);

                // Do the required rotations and scales
                Utilities.convertToSquare(cameraToPreview, finalDegrees, preview_frame);

                // Inverting the matrix gets us the transform in the opposite direction
                Matrix previewToCamera = new Matrix();
                boolean inverted = cameraToPreview.invert(previewToCamera);
                if(!inverted) {
                    // Can't progress from here
                    SDGLog.e(TAG, "Failed to invert matrix");
                    return false;
                }

                float[] position = {event.getX(), event.getY()};

                SDGLog.d(TAG, "pre-transform: " + event.getX() + "," + event.getY());

                previewToCamera.mapPoints(position);

                float xTransformed = position[0];
                float yTransformed = position[1];

                SDGLog.d(TAG, "transformed: " + xTransformed + "," + yTransformed);

                autofocusArea = new Camera.Area(getRectFromCenter(xTransformed, yTransformed), 1000);

                tryAutofocus(null);

                List<Camera.Area> focusAreas = new ArrayList<>();
                focusAreas.add(autofocusArea);
                tryAutofocus(focusAreas);


                findViewById(R.id.restore_autofocus).setVisibility(View.VISIBLE);
                return false;
            }
        });
    }

    /**
     *
     * @param areas List of focus areas to try, or null to reset
     */
    private void tryAutofocus(List<Camera.Area> areas) {
        Camera c = getCameraInstance();
        Camera.Parameters p = c.getParameters();
        p.setFocusAreas(areas);
        c.setParameters(p);

        c.autoFocus(new Camera.AutoFocusCallback() {
            @Override
            public void onAutoFocus(boolean success, Camera camera) {
                SDGLog.d(TAG, "Focus complete");
            }
        });
    }

    private Rect getRectFromCenter(float x, float y) {
        int focusRadius = 30;
        int top = (int) (y - focusRadius);
        int bottom = (int) (y + focusRadius);
        int left = (int) (x - focusRadius);
        int right = (int) (x + focusRadius);

        return new Rect(left, top, right, bottom);
    }

    private void setCameraOrientation(Activity activity, Camera camera) {
        int result = getCameraRotationDegrees(activity);
        camera.setDisplayOrientation(result);
        setCameraFlashMode(this, getCameraInstance());

        Camera.Parameters params = camera.getParameters();
        params.setRotation(result);
        camera.setParameters(params);

    }

    private int getCameraRotationDegrees(Activity activity) {
        Camera.CameraInfo info =
                new Camera.CameraInfo();

        Camera.getCameraInfo(0, info);

        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        return result;
    }

    private void setCameraFlashMode(Activity activity, Camera camera) {
        Camera.Parameters params = camera.getParameters();
        params.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
        camera.setParameters(params);
    }

    private void takePicture() {
        SDGLog.d(TAG, "takePicture");

        if(getCameraInstance() != null) {
            SDGLog.d(TAG, "taking camera1 picture");
            try {
                getCameraInstance().takePicture(null, null, mPictureCallback);
            } catch (Exception e) {
                SDGLog.e(TAG, "got exception taking picture: " + e);
                // TODO What else should happen here? startPreview()?
            }
        }
    }

    private Camera.PictureCallback mPictureCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {

            SDGLog.d(TAG, "onPictureTaken");

            Utilities.playShutterClick(getApplicationContext());

            //Snackbar.make(ImageCaptureActivity.this.findViewById(R.id.photo_content_container), "Captured image", Snackbar.LENGTH_SHORT).show();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(ImageCaptureActivity.this, "Captured image", Toast.LENGTH_SHORT).show();
                }
            });

            //mBackgroundHandler.post(new ImageSaver(null, data, getApplicationContext(), tags, categories, description, imageSaverCallback, getExifOrientationForDeviceRotation(mOrientationRounded)));

            camera.startPreview();
        }
    };

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

    private Image mLastImage;

    IImageSaverCallback imageSaverCallback = new IImageSaverCallback() {
        @Override
        public void imageSaved(final Image savedImage) {

            mLastImage = savedImage;

            ImageDao imageDao = GenericPhotoApplication.getInstance().getDb().imageDao();
            final String filename = imageDao.getImageFile(mLastImage.getUid()).filename;

            SDGLog.d(TAG, "Got image: " + filename);

            if(new File(filename).exists()) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        Bitmap bm = BitmapFactory.decodeFile(filename);
                        int height;
                        int width;

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

                        Bitmap rounded = getRoundedRectBitmap(resized, height);

                        imageThumbnailButton.setImageBitmap(rounded);
                        imageThumbnailButton.show();
                    }
                });

            }
        }
    };

    public Bitmap getRoundedRectBitmap(Bitmap bitmap, int pixels) {
        Bitmap result = null;
        try {

            result = Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(result);

            int color = 0xff424242;
            Paint paint = new Paint();
            Rect rect = new Rect(0, 0, pixels, pixels);

            paint.setAntiAlias(true);
            canvas.drawARGB(0, 0, 0, 0);
            paint.setColor(color);
            canvas.drawCircle(pixels/2, pixels/2, (pixels/2)*.95f, paint);
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            canvas.drawBitmap(bitmap, rect, rect, paint);

        } catch (NullPointerException e) {
        } catch (OutOfMemoryError o) {
        }
        return result;
    }

    static class SizeAreaComparator implements java.util.Comparator<Camera.Size> {
        @Override
        public int compare(Camera.Size lhs, Camera.Size rhs) {
            return Long.signum((long) lhs.width * lhs.height -
                    (long) rhs.width * rhs.height);
        }
    }
}