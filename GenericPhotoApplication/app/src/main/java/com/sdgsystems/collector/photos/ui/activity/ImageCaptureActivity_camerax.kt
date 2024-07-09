package com.sdgsystems.collector.photos.ui.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.DisplayMetrics
import android.view.*
import android.view.animation.OvershootInterpolator
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.sdgsystems.blueloggerclient.SDGLog
import com.sdgsystems.collector.photos.Constants
import com.sdgsystems.collector.photos.GenericPhotoApplication
import com.sdgsystems.collector.photos.R
import com.sdgsystems.collector.photos.Utilities
import com.sdgsystems.collector.photos.Utilities.getRoundedRectBitmap
import com.sdgsystems.collector.photos.data.model.Image
import com.sdgsystems.collector.photos.images.ImageSaver
import com.sdgsystems.collector.photos.scanning.GenericScanningCallback
import com.sdgsystems.collector.photos.scanning.LocalScanManager
import com.sdgsystems.collector.photos.tasks.TagListCleaner
import com.sdgsystems.idengine.internal.Debug
import java.io.File
import java.lang.Math.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class ImageCaptureActivity_camerax : ScanningActivity(), View.OnClickListener, ILoginCallback {
    private var tags: ArrayList<String>? = null
    private var categories: ArrayList<String>? = null
    private var description: String? = null

    private var startingSetupActivity = false
    private var scannedTag: String? = null
    private lateinit var imageThumbnailButton: FloatingActionButton
    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var context: Context
    private lateinit var captureImageButton: View
    private var mLastImage: Image? = null

    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private lateinit var viewFinder: PreviewView
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var container: RelativeLayout

    public override fun onCreate(savedInstanceState: Bundle?) {
        context = this
        tags = ArrayList()
        categories = ArrayList()
        description = ""
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_capture_camerax)
        //requestGpsPermission();
        GenericPhotoApplication.getInstance().requestLocationUpdates(true)
        captureImageButton = findViewById(R.id.capture_image)
        captureImageButton.setOnClickListener(this)
        captureImageButton.setVisibility(View.INVISIBLE)

        container = findViewById(R.id.photo_content_container)

        findViewById<View>(R.id.imageSetup).setOnClickListener(this)
        findViewById<View>(R.id.restore_autofocus).setOnClickListener(this)
        imageThumbnailButton = findViewById<View>(R.id.imageThumbnail) as FloatingActionButton
        imageThumbnailButton.setOnClickListener(this)
        imageThumbnailButton.hide()
        updateTagLists()

        description = PreferenceManager.getDefaultSharedPreferences(this).getString(ImageSetupActivity.PREF_DEFAULT_DESCRIPTION, "")

        viewFinder = findViewById(R.id.camera_preview)

        // getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        // getSupportActionBar().setHomeButtonEnabled(true);
        findViewById<View>(R.id.restore_autofocus).visibility = View.INVISIBLE
        bottomNavigationView = findViewById(R.id.bottom_navigation)
        bottomNavigationView.setOnNavigationItemSelectedListener(BottomNavigationView.OnNavigationItemSelectedListener { item ->
            SDGLog.d(TAG, "Clicked " + item.title)
            val prevSelectedId = bottomNavigationView.getSelectedItemId()
            val id = item.itemId
            if (id == prevSelectedId) {
                Debug.debug(TAG, item.title.toString() + " is already selected")
                return@OnNavigationItemSelectedListener false
            }
            if (id == R.id.action_list) {
                Utilities.startTopLevelActivity(context, ThumbnailListActivity::class.java)
            } else if (id == R.id.action_camera) {
            } else if (id == R.id.action_tags) {
                Utilities.startTopLevelActivity(context, ImageSetupActivity::class.java)
            }
            true
        })
        bottomNavigationView.setSelectedItemId(R.id.action_camera)

        cameraExecutor = Executors.newSingleThreadExecutor()

        requestCameraPermission()

        pinchDetector = ScaleGestureDetector(context, pinchListener)

        // start the timer going when the app starts going
        TagListCleaner.setTagActivity()
        TagListCleaner.getClearTags.observe( this ) { changedTags ->
            // Do something with the updated tags list
            if (changedTags) {
                updateTagLists()
            }
        }
    }

    private val pinchListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val currentZoomRatio = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 0F

            val delta = detector.scaleFactor

            camera?.cameraControl?.setZoomRatio(currentZoomRatio * delta)
            return true
        }
    }
    lateinit var pinchDetector: ScaleGestureDetector

    var cameraSetUp = false

    override fun onBackPressed() {
        // Finish the whole app
        finishAffinity()
    }

    private fun updateTagLists() {
        //get all of the assigned tags and categories and display them
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        categories = ArrayList(preferences.getStringSet(ImageSetupActivity.PREF_DEFAULT_CATEGORIES, HashSet())!!)
        tags = ArrayList(preferences.getStringSet(ImageSetupActivity.PREF_DEFAULT_TAGS, HashSet())!!)
        if (scannedTag != null) {
            if (isSingleTagMode) {
                if (singleTagRemovesAll()) {
                    tags!!.clear()
                }
                else {
                    tags!!.remove(lastTagScanned)
                }
            }
            if (!tags!!.contains(scannedTag!!)) {
                tags!!.add(scannedTag!!)
            }
            lastTagScanned = scannedTag
            val editor = preferences.edit()
            editor.putStringSet(ImageSetupActivity.PREF_DEFAULT_TAGS, HashSet(tags))
            editor.commit()
            scannedTag = null
        }
        updateChips(R.id.tagCloud, "#aa81c784", "#aaffffff", tags) { deleteString -> tags!!.remove(deleteString) }
        updateChips(R.id.categoryCloud, "#aa64b5f6", "#aaffffff", categories) { deleteString -> categories!!.remove(deleteString) }
    }

    protected fun updateChips(cloudLayout: Int, backgroundColor: String?,
                              textColor: String?, chipList: List<String>?,
                              deleteCallback: IDeleteElementCallback?) {
        super.updateChips(cloudLayout, backgroundColor, textColor, chipList, lastTagScanned, null)
    }

    override fun onResume() {
        super.onResume()
        bottomNavigationView!!.selectedItemId = R.id.action_camera
        startingSetupActivity = false
        initScanner(mScanningCallback)
        setLoginCallback(this)
        if (GenericPhotoApplication.getInstance().bearerToken == null && loginDialogFragment == null) {
            displayLoginDialog()
        }
        SDGLog.d(TAG, "initialized scanner after resume")
        updateTagLists()
        description = PreferenceManager.getDefaultSharedPreferences(this).getString(ImageSetupActivity.PREF_DEFAULT_DESCRIPTION, "")
        val imageDao = GenericPhotoApplication.getInstance().db.imageDao()
        SDGLog.d(TAG, "resuming, checking for last captured image")
        if (mLastImage == null || imageDao.getImage(mLastImage!!.uid as Long) == null) {
            imageThumbnailButton.hide()
        } else {
            SDGLog.d(TAG, "found the image hanging around: " + mLastImage!!.uid)
        }

        if(!cameraSetUp) {
            viewFinder.post {setUpCamera() }
            cameraSetUp = true
        }
    }

    override fun onPause() {
        super.onPause()
        closeScanner()
    }

    override fun onClick(view: View) {
        SDGLog.d(TAG, "onclick: " + view.id)
        when (view.id) {
            R.id.capture_image -> {
                val flashMode = when (getFlashMode(context)) {
                    Constants.PREF_FLASH_MODE_ON -> ImageCapture.FLASH_MODE_ON
                    Constants.PREF_FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_OFF
                    else -> ImageCapture.FLASH_MODE_AUTO
                }
                imageCapture?.flashMode = flashMode

                SDGLog.d(TAG, "*** CAPTURE IMAGE")
                val limit = Utilities.getOfflineFileLimit(context)
                if (limit > 0) {
                    val db = (context.applicationContext as GenericPhotoApplication).db
                    val unsent = db.imageFileDao().countUnsent()
                    if (unsent >= limit) {
                        SDGLog.d(TAG, "Unsent images: $unsent/$limit")
                        Utilities.showText(context, "Too many offline photos! Connect to the Internet and log in to upload saved photos.")
                        return
                    }
                }
                takePicture()
            }
            R.id.imageSetup -> {
                startActivity(Intent(this, ImageSetupActivity::class.java))
            }
            R.id.imageThumbnail -> {
                SDGLog.d(TAG, "Clicked the thumbnail")
                val detail = Intent(this@ImageCaptureActivity_camerax, PhotoDetailActivity::class.java)
                detail.putExtra(PhotoDetailActivity.IMAGE_DATABASE_ID, mLastImage!!.uid)
                detail.putExtra(PhotoDetailActivity.IMAGE_MONGO_ID, mLastImage!!.mongo_id)
                startActivity(detail)
            }
            R.id.restore_autofocus -> {
                camera?.cameraControl?.cancelFocusAndMetering()

                findViewById<View>(R.id.restore_autofocus).visibility = View.GONE
            }
        }
    }

    private fun enableCameraPreview() {}

    /** Barcode Scanning  */
    var mScanningCallback: GenericScanningCallback = object : GenericScanningCallback {
        override fun scanAvailable(scanData: String, scannerType: Constants.ScannerType) {
            SDGLog.d(TAG, "scanAvailable($scanData, $scannerType")
            if (Utilities.shouldIgnoreScan(this@ImageCaptureActivity_camerax, scanData, scannerType)) return
            runOnUiThread {
                SDGLog.d(TAG, "got scan: $scanData")
                displayScanOverlay(this@ImageCaptureActivity_camerax, false)
                SDGLog.d(TAG, "updating list with: $scanData")
                setScannedTag(scanData)

                //only 'loop' the scanner if we are scanning w/ RFID
                //TODO: hide this behind a setting so that nfc + rfid works correctly
                if (scannerType == Constants.ScannerType.SCANNER_TYPE_RFID) {
                    triggerScanning(true)
                }
            }
        }

        override fun statusMessage(message: String, error: Boolean) {
            Toast.makeText(this@ImageCaptureActivity_camerax, message, Toast.LENGTH_SHORT).show()
        }

        override fun cameraScanningStarted() {
            cameraScanning = true
        }

        override fun scanningComplete() {
            //Log.d(TAG, "scanning complete");
            cameraScanning = false
        }

        override fun scannerClosed() {
            SDGLog.d(TAG, "scanner closed")
        }

        override fun scannerConnected() {
            if (LocalScanManager.getInstance().scannerType == Constants.ScannerType.SCANNER_TYPE_RFID) {
                //This implementation is specifically for continuous RFID scanning in the background, we do NOT
                //support barcode scanning in this manner...
                triggerScanning(true)
            }
        }

        override fun shouldUseCameraScanning(): Boolean {
            return false
        }
    }

    private fun setScannedTag(scanData: String) {
        scannedTag = scanData
        updateTagLists()
    }

    private fun getFlashMode(c: Context): String? {
        return PreferenceManager.getDefaultSharedPreferences(c).getString(Constants.PREF_FLASH_MODE, Constants.PREF_FLASH_MODE_AUTO)
    }


    /** Initialize CameraX, and prepare to bind the camera use cases  */
    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({

            // CameraProvider
            cameraProvider = cameraProviderFuture.get()

            // Select lensFacing depending on the available cameras
            lensFacing = when {
                hasBackCamera() -> CameraSelector.LENS_FACING_BACK
                hasFrontCamera() -> CameraSelector.LENS_FACING_FRONT
                else -> throw IllegalStateException("Back and front camera are unavailable")
            }

            // Enable or disable switching between cameras
            //updateCameraSwitchButton()

            // Build and bind the camera use cases
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private var isMultitouch = false
    /** Declare and bind preview, capture and analysis use cases */
    @SuppressLint("ClickableViewAccessibility")
    private fun bindCameraUseCases() {
        // Get screen metrics used to setup camera for full screen resolution
        val metrics = DisplayMetrics().also { viewFinder.display.getRealMetrics(it) }
        SDGLog.d(TAG, "Screen metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")

        val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
        SDGLog.d(TAG, "Preview aspect ratio: $screenAspectRatio")

        val rotation = viewFinder.display.rotation

        // Preview
        preview = Preview.Builder()
                // We request aspect ratio but no resolution
                .setTargetAspectRatio(screenAspectRatio)
                // Set initial target rotation
                .setTargetRotation(rotation)
                .build()

        viewFinder.setOnTouchListener { _, event ->
            pinchDetector.onTouchEvent(event)
            if(event.pointerCount > 1) {
                isMultitouch = true
                return@setOnTouchListener true
            }
            else {
                return@setOnTouchListener when {
                    (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_POINTER_UP) -> {
                        if(isMultitouch) {
                            isMultitouch = false
                        }
                        else {
                            val factory: MeteringPointFactory = viewFinder.meteringPointFactory
                            val autoFocusPoint = factory.createPoint(/*viewFinder.width.toFloat() - */event.x, event.y)
                            try {
                                camera?.cameraControl?.startFocusAndMetering(
                                        FocusMeteringAction.Builder(
                                                autoFocusPoint,
                                                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB
                                        ).apply {
                                            disableAutoCancel()
                                        }.build()
                                )
                                findViewById<View>(R.id.restore_autofocus).visibility = View.VISIBLE
                            } catch (e: CameraInfoUnavailableException) {
                                SDGLog.e(TAG, "Cannot access camera", e)
                            }
                        }
                        true
                    }
                    else -> true
                }
            }
        }

        viewFinder.post {
            buildImageCapture()
            rebindUseCases()
        }
    }

    private fun buildImageCapture() {
        val flashMode = when (getFlashMode(context)) {
            Constants.PREF_FLASH_MODE_ON -> ImageCapture.FLASH_MODE_ON
            Constants.PREF_FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_OFF
            else -> ImageCapture.FLASH_MODE_AUTO
        }

        val resolution = Utilities.getDesiredResolution(this).toInt() / 1000000

        val rotation = viewFinder.display.rotation
        val targetResolution = Utilities.getSizeForMegapixels(resolution, rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90)

        // ImageCapture
        val builder = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(flashMode)
                // Set initial target rotation, we will have to call this again if rotation changes
                // during the lifecycle of this use case
                .setTargetRotation(rotation)
                //.setTargetResolution() TODO

        if(targetResolution != null) {
            builder.setTargetResolution(targetResolution)
        }

        imageCapture = builder.build()
    }

    private fun rebindUseCases() {
        // CameraProvider
        val cameraProvider = cameraProvider

        if(cameraProvider == null) {
            SDGLog.w(TAG, "Camera init failed")
            return
        }

        // CameraSelector
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture)

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(viewFinder.surfaceProvider)

            captureImageButton.visibility = View.VISIBLE
        } catch (exc: Exception) {
            SDGLog.e(TAG, "Use case binding failed", exc)
        }
    }

    /**
     *  [androidx.camera.core.ImageAnalysisConfig] requires enum value of
     *  [androidx.camera.core.AspectRatio]. Currently it has values of 4:3 & 16:9.
     *
     *  Detecting the most suitable ratio for dimensions provided in @params by counting absolute
     *  of preview ratio to one of the provided values.
     *
     *  @param width - preview width
     *  @param height - preview height
     *  @return suitable aspect ratio
     */
    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    /** Returns true if the device has an available back camera. False otherwise */
    private fun hasBackCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
    }

    /** Returns true if the device has an available front camera. False otherwise */
    private fun hasFrontCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
    }

    private fun takePicture() {
        SDGLog.d(TAG, "*** TAKE PICTURE")

        val requireTag = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(Constants.PREF_REQUIRE_TAG, false)
        if (requireTag && tags!!.isEmpty()) {
            showToast("Add at least one tag")
            return
        }
        TagListCleaner.setTagActivity()

        imageCapture?.let { imageCapture ->

            imageCapture.targetRotation = viewFinder.display.rotation

            // Create output file to hold the image
            val photoFile = ThumbnailListActivity.createImageFile(this)

            // Setup image capture metadata
            val metadata = ImageCapture.Metadata().apply {

                // Mirror image when using the front camera
                isReversedHorizontal = lensFacing == CameraSelector.LENS_FACING_FRONT

            }

            // Create output options object which contains file + metadata
            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
                    .setMetadata(metadata)
                    .build()

            // Setup image capture listener which is triggered after photo has been taken
            imageCapture.takePicture(
                    outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    SDGLog.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    SDGLog.d(TAG, "*** Got image 1: ${photoFile.absolutePath} exists: ${photoFile.exists()} size ${photoFile.length() / 1024}")
                    cameraExecutor.run {
                        ImageSaver(photoFile, applicationContext, tags, categories, description, {
                            runOnUiThread {
                                setGalleryThumbnail(it)
                                Utilities.playShutterClick(applicationContext)
                            }
                        }, getExifOrientationForDeviceRotation(mOrientationRounded)).run()
                    }
                }
            })

            // We can only change the foreground Drawable using API level 23+ API
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                // Display flash animation to indicate that photo was captured
                container.postDelayed({
                    container.foreground = ColorDrawable(Color.WHITE)
                    container.postDelayed(
                            { container.foreground = null }, ANIMATION_FAST_MILLIS)
                }, ANIMATION_SLOW_MILLIS)
            }
        }
    }

    fun setGalleryThumbnail(savedImage: Image) {
        val imageDao = GenericPhotoApplication.getInstance().db.imageDao()

        mLastImage = savedImage

        val lastImageFile = imageDao.getImageFile(mLastImage!!.uid)
        val filename = lastImageFile.filename

        val file = File(filename)

        SDGLog.d(TAG, "*** Got image 2: $filename exists: ${file.exists()} size ${file.length() / 1024}")

        if (File(filename).exists()) {
            val bm = BitmapFactory.decodeFile(filename)
            val height: Int
            val width: Int

            // Use a rendered FAB to get width and height
            if (bm.width > bm.height) {
                height = findViewById<View>(R.id.capture_image).height
                width = height * bm.width / bm.height
            } else {
                width = findViewById<View>(R.id.capture_image).width
                height = width * bm.height / bm.width
            }
            val resized = Bitmap.createScaledBitmap(bm,
                    width,
                    height,
                    true)
            val rounded = getRoundedRectBitmap(resized, height)
            val roundedRotated = Utilities.rotateBitmap(rounded, savedImage.exifRotation)
            runOnUiThread {
                imageThumbnailButton.setImageBitmap(roundedRotated)
                imageThumbnailButton.show()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        SDGLog.d(TAG, "Key Event: $keyCode")
        when (keyCode) {
            103 -> {
                if (!startingSetupActivity) {
                    startingSetupActivity = true
                    startActivity(Intent(this, ImageSetupActivity::class.java))
                }
                return true
            }
            104, KeyEvent.KEYCODE_MENU -> {
                //Got the secondary Trigger
                takePicture()
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                onBackPressed()
                return true
            }
        }
        return false
    }

    private fun requestGpsPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION_PERMISSION
        )
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.size != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Snackbar.make(findViewById(R.id.photo_content_container), "Camera Permissions not granted", Snackbar.LENGTH_SHORT).show()
            } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setUpCamera()
            }
        } else if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.size != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                SDGLog.d(TAG, "Permissions not granted...")
            } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                GenericPhotoApplication.getInstance().requestLocationUpdates(true)
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun orientationChanged() {
        super.orientationChanged()
        updateFabIconOrientation()

        //val landscape = (mOrientationRounded == LANDSCAPE_LEFT || mOrientationRounded == LANDSCAPE_RIGHT)

        viewFinder.post {
            buildImageCapture()
            rebindUseCases()
        }
    }

    private fun updateFabIconOrientation() {
        SDGLog.d(TAG, "Orientation: $mOrientationRounded")

        //getSupportActionBar().setShowHideAnimationEnabled(false);
        if (mOrientationRounded == LANDSCAPE_LEFT) {
            supportActionBar!!.hide()
            rotateFabClockwise(findViewById<View>(R.id.capture_image) as FloatingActionButton)
            rotateFabClockwise(findViewById<View>(R.id.imageSetup) as FloatingActionButton)
            rotateFabClockwise(findViewById<View>(R.id.restore_autofocus) as FloatingActionButton)
        } else if (mOrientationRounded == LANDSCAPE_RIGHT) {
            supportActionBar!!.hide()
            rotateFabCounterClockwise(findViewById<View>(R.id.capture_image) as FloatingActionButton)
            rotateFabCounterClockwise(findViewById<View>(R.id.imageSetup) as FloatingActionButton)
            rotateFabCounterClockwise(findViewById<View>(R.id.restore_autofocus) as FloatingActionButton)
        } else {
            supportActionBar!!.show()
            rotateFabToZero(findViewById<View>(R.id.capture_image) as FloatingActionButton)
            rotateFabToZero(findViewById<View>(R.id.imageSetup) as FloatingActionButton)
            rotateFabToZero(findViewById<View>(R.id.restore_autofocus) as FloatingActionButton)
        }
    }

    private fun rotateFabClockwise(fab: FloatingActionButton) {
        ViewCompat.animate(fab)
                .rotation(90.0f)
                .withLayer()
                .setDuration(700L)
                .setInterpolator(OvershootInterpolator(0.0f))
                .start()
    }

    private fun rotateFabCounterClockwise(fab: FloatingActionButton) {
        ViewCompat.animate(fab)
                .rotation(-90.0f)
                .withLayer()
                .setDuration(700L)
                .setInterpolator(OvershootInterpolator(0.0f))
                .start()
    }

    private fun rotateFabToZero(fab: FloatingActionButton) {
        ViewCompat.animate(fab)
                .rotation(0.0f)
                .withLayer()
                .setDuration(700L)
                .setInterpolator(OvershootInterpolator(0.0f))
                .start()
    }

    /**
     * Shows a [Toast] on the UI thread.
     *
     * @param text The message to show
     */
    private fun showToast(text: String) {
        runOnUiThread { Toast.makeText(this@ImageCaptureActivity_camerax, text, Toast.LENGTH_SHORT).show() }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_image_capture, menu)
        return true
    }

companion object {

    private const val TAG = "ImageCaptureActivity_cx"
    private const val REQUEST_LOCATION_PERMISSION = 10541
    private const val REQUEST_CAMERA_PERMISSION = 10542
    private const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
    private const val PHOTO_EXTENSION = ".jpg"
    private const val RATIO_4_3_VALUE = 4.0 / 3.0
    private const val RATIO_16_9_VALUE = 16.0 / 9.0

    private const val ANIMATION_FAST_MILLIS = 250L
    private const val ANIMATION_SLOW_MILLIS = 1000L

    /** Helper function used to create a timestamped file */
    private fun createFile(baseFolder: File, format: String, extension: String) =
            File(baseFolder, SimpleDateFormat(format, Locale.US)
                    .format(System.currentTimeMillis()) + extension)
}

    override fun loginAttempted(success: Boolean) {
    }

    override fun dataChanged() {
    }
}
