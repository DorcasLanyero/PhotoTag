package com.sdgsystems.collector.photos;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.MediaActionSound;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.exifinterface.media.ExifInterface;

import android.util.Log;
import android.util.Size;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.lifecycle.Lifecycle;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.sdgsystems.blueloggerclient.LogUploadClient;
import com.sdgsystems.blueloggerclient.SDGLog;
import com.sdgsystems.collector.photos.data.AppDatabase;
import com.sdgsystems.collector.photos.data.model.Image;
import com.sdgsystems.collector.photos.sync.NetworkRequestHandler;
import com.sdgsystems.collector.photos.ui.activity.ImageCaptureActivity;
import com.sdgsystems.collector.photos.ui.activity.ImageCaptureActivity_camera2;
import com.sdgsystems.collector.photos.ui.activity.ImageCaptureActivity_camerax;
import com.sdgsystems.collector.photos.ui.activity.ThumbnailListActivity;

import org.apache.commons.io.FileUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.sdgsystems.collector.photos.Constants.PREF_CAMERA_RESOLUTION;
import static com.sdgsystems.collector.photos.Constants.ScannerType.SCANNER_TYPE_RFID;

/**
 * Created by bfriedberg on 8/1/17.
 */

public class Utilities {
    private static final String TAG = "Utilities";

    public static boolean isZebraDevice() {
        if(android.os.Build.MANUFACTURER.contains("Zebra Technologies") ||
            android.os.Build.MANUFACTURER.contains("Motorola Solutions") ){
            return true;
        } else {
            return false;
        }
    }

    public static boolean isBluebirdDevice() {
        if(android.os.Build.MANUFACTURER.toLowerCase().contains("bluebird") ){
            return true;
        } else {
            return false;
        }
    }

    public static boolean isPointMobileDevice() {
        return android.os.Build.MANUFACTURER.toLowerCase().contains("pointmobile");
    }

    public static boolean isHoneywellDevice() {
        return Build.MANUFACTURER.toLowerCase().contains("honeywell") && Build.MODEL.contains("CT60");
    }

    public static void printStatement(String TAG, String message) {
        SDGLog.d(TAG, message);
    }

    public static boolean isEmailValid(String email) {
        boolean isValid = false;

        if (email.length() > 0) {
            String expression = "^[\\w\\.-]+@([\\w\\-]+\\.)+[A-Z]{2,4}$";
            CharSequence inputStr = email;
            Pattern pattern = Pattern.compile(expression, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(inputStr);
            if (matcher.matches()) {
                isValid = true;
            }
        }

        return isValid;
    }

    public static void showText(Context context, String message)
    {
        if(message != null && message.trim().length() > 0) {
            Toast toast = Toast.makeText(context, message, Toast.LENGTH_SHORT); toast.setGravity(Gravity.TOP, 0, 200); toast.show();
        }
    }

    public static String addURLParam(String currentURL, String paramName, String paramValue) {

        if(!currentURL.contains("?")) {
            currentURL += "?";
        } else if(!currentURL.endsWith("&")){
            currentURL += "&";
        }

        currentURL += paramName + "=" + paramValue;

        return currentURL;
    }


    private static void outputNetworkInfo(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();

        String message = "Network state: ";

        if(isConnected) {
            message += "Connected: ";
            message += Utilities.getIPAddress(true) + "\n";
        } else {
            message += "Not Connected\n";
        }

        if(activeNetwork != null)
            message += activeNetwork;

        SDGLog.d(TAG, message);
        SDGLog.d("ipstate", message);
    }


    public static Bitmap getRoundedRectBitmap(Bitmap bitmap, int pixels) {
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
            canvas.drawCircle(pixels/2, pixels/2, (pixels/2), paint);
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            canvas.drawBitmap(bitmap, null, rect, paint);

        } catch (NullPointerException e) {
        } catch (OutOfMemoryError o) {
        }
        return result;
    }


    //Method to convert response stream into a string object
    public static String convertStreamToString(InputStream is)
    {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line = null;
        try
        {
            while ((line = reader.readLine()) != null)
            {
                sb.append(line + "\n");
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            try
            {
                is.close();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        return sb.toString();
    }

    //Method to create alert dialog with a message
    public static void simpleMessageDialog(Context context, String msg)
    {
        simpleMessageDialog(context, msg, null);
    }
    //Method to create alert dialog with a message
    public static void simpleMessageDialog(Context context, String msg, String title)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle((title == null) ? "" : title);
        builder.setMessage(msg);
        builder.setPositiveButton("Ok", null);
        builder.setCancelable(true);
        builder.create().show();
    }

    public static boolean isNullOrBlank(String s)
    {
        return (s == null || s.trim().equals(""));
    }


    public static String getLogFileSize() {
        String size = "";

        File Root = Environment.getExternalStorageDirectory();
        FilenameFilter filter = new FilenameFilter() {

            @Override
            public boolean accept(File dir, String filename) {
                if(filename.toLowerCase().contains("sdgphotos"))
                    return true;
                else
                    return false;
            }
        };

        File[] files = Root.listFiles(filter);

        long totalSize = 0;

        for(File file: files) {
            if(file.exists() && file.isFile())
                totalSize += file.length();
        }

        return String.valueOf(totalSize / 1000);
    }


    public static void clearLogs() {
        File Root = Environment.getExternalStorageDirectory();
        FilenameFilter filter = new FilenameFilter() {

            @Override
            public boolean accept(File dir, String filename) {
                if(filename.toLowerCase().contains("sdgphotos"))
                    return true;
                else
                    return false;
            }
        };

        File[] files = Root.listFiles(filter);

        for(File file: files) {
            if(file.exists() && file.isFile())
                file.delete();
        }
    }

    public static void playShutterClick(Context context) {
        if(PreferenceManager.getDefaultSharedPreferences(context).getBoolean("PREF_SHUTTER_SOUND", true)) {
            MediaActionSound sound = new MediaActionSound();
            sound.play(MediaActionSound.SHUTTER_CLICK);
        }
    }

    public static String getCamera1FlashModeForPref(String prefValue) {
        switch(prefValue) {
            case Constants.PREF_FLASH_MODE_ON: return Camera.Parameters.FLASH_MODE_ON;
            case Constants.PREF_FLASH_MODE_OFF: return Camera.Parameters.FLASH_MODE_OFF;
            default: return Camera.Parameters.FLASH_MODE_AUTO;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static int getCamera2FlashModeForPref(String prefValue) {
        switch(prefValue) {
            case Constants.PREF_FLASH_MODE_ON: return CameraCharacteristics.CONTROL_AE_MODE_ON_ALWAYS_FLASH;
            case Constants.PREF_FLASH_MODE_OFF: return CameraCharacteristics.CONTROL_AE_MODE_ON; // without flash control
            default: return CameraCharacteristics.CONTROL_AE_MODE_ON_AUTO_FLASH;
        }
    }

    public static boolean useCamera1(Context context) {

        return false;

//        It's CameraX's problem now

//        boolean useCamera1 = false;
//
//
//        if(isZebraDevice()) {
//            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
//                SDGLog.d(TAG, "Android older than L: using Camera 1");
//                useCamera1 = true;
//            }
//        } else if(Build.MANUFACTURER.contains("Allwinner")) {
//            SDGLog.d(TAG, "Allwinner device: using Camera 1");
//            useCamera1 = true;
//        }
//
//        if(useCamera1 == false && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
//
//            if(isZebraDevice()) {
//                if(Build.DEVICE.toLowerCase().contains("tc20")) {
//                    SDGLog.d(TAG, "TC20: using Camera 1");
//                    return false;
//                }
//            }
//
//            CameraManager manager = null;
//            manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
//
//            try {
//                for (String cameraId : manager.getCameraIdList()) {
//
//                    CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
//
//                    // We don't use a front facing camera.
//                    Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
//                    if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
//                        continue;
//                    }
//
//                    int hwLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
//
//                    SDGLog.d(TAG, "Hardware level: (not zero-based)" + hwLevel);
//                    if(hwLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY ||
//                            hwLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED) {
//                        SDGLog.d(TAG, "Camera supports legacy only: using Camera 1");
//                        useCamera1 = true;
//                    }
//                }
//            } catch (CameraAccessException e) {
//                SDGLog.d(TAG, "Exception accessing camera: using camera 1", e);
//                useCamera1 = true;
//            }
//        }
//
//        SDGLog.d(TAG, "Final camera1 selection: " + useCamera1);
//        return useCamera1;

    }

    /**
     * Convert to a square with a coordinate origin in the center.
     *
     * This yields a coordinate system with squashed pixels, but
     * it's much easier to reason about with respect to rotation and
     * scaling, and going from squashed-pixel-square coordinates to
     * one of the actual physical coordinate systems is easier.
     *
     * @param matrix
     * @param rotationDegrees
     * @param container
     */
    public static void convertToSquare(Matrix matrix, int rotationDegrees, View container) {
        // Rotate according to the calculated need
        matrix.postRotate(rotationDegrees);

        // Scale to a square
        matrix.postScale(container.getWidth() / 2000f, container.getHeight() / 2000f);

        // Translate so that the coordinate origin is in the middle
        matrix.postTranslate(container.getWidth() / 2f, container.getHeight() / 2f);
    }

    public static long getDesiredResolution(Context context) {
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(context);
        long desiredResolution = Long.parseLong(p.getString(PREF_CAMERA_RESOLUTION, "0"));
        if (desiredResolution == 0) desiredResolution = Integer.MAX_VALUE;
        return desiredResolution;
    }

    // TODO: Turn "top level activities" into fragments
    public static void startTopLevelActivity(Context context, Class<?> cls) {
        final Intent intent = new Intent(context, cls);
        if (cls.equals(ThumbnailListActivity.class)) {
            // ThumbnailListActivity has launchMode "singleTask" which removes all the other
            // activities from the task when we switch to it, so we just need to "clear" the
            // task to make sure that ThumbnailListActivity is in list mode and not search mode.
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        } else {
            // For any other top-level activity, bring it to the front if it is already running
            intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        }
        context.startActivity(intent);
    }

    //Start the image capture activity, camera 1 for zebra, camera 2 for everyone else (Assuming that they aren't running 4.4...
    public static void captureImages(Context context) {

        GenericPhotoApplication.getInstance().requestLocationUpdates(true);

        if (useCamera1(context)) {
            startTopLevelActivity(context, ImageCaptureActivity.class);
        } else {
            startTopLevelActivity(context, ImageCaptureActivity_camerax.class);
        }
    }

    /**
     *
     * @param context
     * @return 0, for no limit, or the number of offline photos to allow.
     */
    public static int getOfflineFileLimit(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean limitPhotos = prefs.getBoolean(Constants.PREF_LIMIT_OFFLINE_PHOTOS, false);
        if(limitPhotos) return Integer.parseInt(prefs.getString(Constants.PREF_OFFLINE_PHOTO_LIMIT, "0"));
        else return 0;
    }

    public static boolean shouldIgnoreScan(ComponentActivity activity, String scanData, Constants.ScannerType scannerType) {
        SDGLog.d(TAG, "scanAvailable(" + scanData + ", " + scannerType + " in " + activity);
        // NFC and camera scans can come in while the activity is still paused, so we
        // have to allow them, but RFID tags should not come in when we are paused.
        // TODO: Maybe a better solution would be to let only one activity callback be
        // active at a time, and let the active callback receive all callbacks.
        if (scannerType == SCANNER_TYPE_RFID && activity.getLifecycle().getCurrentState() != Lifecycle.State.RESUMED) {
            SDGLog.e(TAG, "Ignoring RFID tag " + scanData + " while activity is not resumed");
            return true;
        }
        return false;
    }

    private static final String LOG_DEST = "https://sdglogs.phototag.app/api/files";
    private static final String LOG_KEY = "3c9b6994e6a0bbb70d3f5e59";

    public static void collectLogs(final Context c) {
        SDGLog.d(TAG, "LOGS COLLECTED HERE");
        SDGLog.d(TAG, "APP VERSION " + BuildConfig.VERSION_NAME + "-" + BuildConfig.FLAVOR);

        final LogUploadClient logger = new LogUploadClient(c);

        String tagString = "tags=";
        tagString += "android,PhotoTag,native";

        logger.getOptions().forceApiPath(LOG_DEST + "?" + tagString);
        logger.getOptions().setApiKey(LOG_KEY);
        logger.saveOptions();

        try {
            final String id = logger.collectLogs(c);
            logger.uploadLogs(id, new LogUploadClient.UploadCallback() {
                @Override
                public void onProgress(String s, double v) {

                }

                @Override
                public void onSuccess(String s) {
                    SDGLog.d(TAG, "Uploaded log identifier: " + s);
                    try {
                        Toast.makeText(c, "Logs uploaded", Toast.LENGTH_SHORT).show();
                    }
                    catch(Exception e) {/*no toast for you*/}

                    logger.deleteWaitingLog(s);
                }

                @Override
                public void onFailure(String s, Exception error) {
                    SDGLog.w(TAG, "Failed to upload log identifier: " + s);

                    String reason;
                    if(error instanceof VolleyError) {
                        VolleyError ve = (VolleyError) error;

                        if(ve.networkResponse != null) {
                            reason = "Unable to upload: " + ve.networkResponse.statusCode;
                            if(ve.networkResponse.data != null) {
                                reason += new String(ve.networkResponse.data);
                            }
                        }
                        else {
                            reason = ve.toString();
                        }
                    }
                    else {
                        reason = "Failed up upload log identifier " + s + ": " + error.toString();
                    }

                    String logText = logger.getLogContents(id);

                    showLogFailureDialog(c, reason, logText);
                }
            });
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }

    private static void showLogFailureDialog(Context c, String reason, String logText) {
        Dialog d = new Dialog(c);
        d.setTitle("Log upload failed!");

        TextView reasonView = new TextView(c);
        reasonView.setText(reason);

        TextView logView = new TextView(c);
        logView.setText(logText);

        ScrollView logScroll = new ScrollView(c);
        logScroll.addView(logView);

        LinearLayout layout = new LinearLayout(c);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(reasonView);
        layout.addView(logScroll);

        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        d.addContentView(layout, params);

        d.show();

        Window w = d.getWindow();
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    public static void copy(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dst);

        // Transfer bytes from in to out
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
    }

    /**
     * Get IP address from first non-localhost interface
     * @param useIPv4  true=return ipv4, false=return ipv6
     * @return  address or empty string
     */
    public static String getIPAddress(boolean useIPv4) {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        //boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr);
                        boolean isIPv4 = sAddr.indexOf(':')<0;

                        if (useIPv4) {
                            if (isIPv4)
                                return sAddr;
                        } else {
                            if (!isIPv4) {
                                int delim = sAddr.indexOf('%'); // drop ip6 zone suffix
                                return delim<0 ? sAddr.toUpperCase() : sAddr.substring(0, delim).toUpperCase();
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) { } // for now eat exceptions
        return "";
    }

    public static Bitmap decodeDatabaseImage(String encode) {
        byte[] byteArrayDecoded = android.util.Base64.decode(encode, android.util.Base64.DEFAULT);
        InputStream inputStream = new ByteArrayInputStream(byteArrayDecoded);
        return BitmapFactory.decodeStream(inputStream);
    }

    @NonNull
    public static String encodeDatabaseImage(Bitmap bitmap) {

        //if the image is in landcape, scale it to 70% of it's original size
        int height = bitmap.getHeight();
        int width = bitmap.getWidth();

        if(width > height) {
            Bitmap.createScaledBitmap(bitmap, (int)(width *.7), (int)(height * .7), false);
        }

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 0, stream);
        byte[] byteArray = stream.toByteArray();
        return android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP);
    }

    public static boolean isConnected(Context context) {
        ConnectivityManager connMan = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = connMan.getActiveNetworkInfo();
        if (netInfo != null && netInfo.isConnected()) {
                return true;
        }

        return false;
    }

    public static Bitmap rotateBitmap(Bitmap bitmap, Image metadata) {
        if(metadata == null) return bitmap;

        int orientation = metadata.getExifRotation();
        return rotateBitmap(bitmap, orientation);
    }

    public static Bitmap rotateBitmap(Bitmap bitmap, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_NORMAL:
                return bitmap;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setRotate(180);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90);
                break;
            default:
                return bitmap;
        }
        try {
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        }
        catch (OutOfMemoryError e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Size getSizeForMegapixels(int megapixels, boolean isLandscape) {

        Size landscape;
        switch(megapixels) {
            case 1:
                landscape = new Size(1153, 867);
                break;
            case 2:
                landscape = new Size(1632, 1224);
                break;
            case 3:
                landscape = new Size(1997, 1504);
                break;
            case 4:
                landscape = new Size(2307, 1734);
                break;
            case 5:
                landscape = new Size(2592, 1944);
                break;
            case 6:
                landscape = new Size(2825, 2124);
                break;
            case 8:
                landscape = new Size(3262, 2453);
                break;
            case 10:
                landscape = new Size(3672, 2747);
                break;
            case 12:
                landscape = new Size(3992, 3008);
                break;
            default: landscape = null;
        }

        if(landscape == null) return landscape;

        if(isLandscape) {
            return landscape;
        }
        else {
            return new Size(landscape.getHeight(), landscape.getWidth());
        }
    }
}
