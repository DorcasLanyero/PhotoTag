package com.sdgsystems.collector.photos.scanning;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import com.sdgsystems.blueloggerclient.SDGLog;
import com.sdgsystems.collector.photos.Constants;
import com.sdgsystems.collector.photos.Utilities;

/**
 * Created by bfriedberg on 8/1/17.
 */

public class LocalScanManager {
    private static final String TAG = LocalScanManager.class.getSimpleName();
    private static GenericScanningCallback sCallback = new EmptyScanningCallback();
    private static IScanner sInstance = null;
    @SuppressLint("StaticFieldLeak")
    private static Context sContext;

    private LocalScanManager() { } // Don't allow this static class to be instantiated

    public static void init(Context context) {
        sContext = context.getApplicationContext();
        getInstance(context).init();
    }

    public static void deinit() {
        getInstance(sContext).deinit();
        sContext = null;
    }

    public static void dataScanned(String data, Constants.ScannerType scannerType) {
        SDGLog.d(TAG, "dataScanned: Calling " + sCallback + "(" + data + ", " + scannerType + ")");
        sCallback.scanAvailable(data, scannerType);
    }

    public static IScanner getInstance() {
        return getInstance(sContext);
    }

    public static IScanner getInstance(Context context) {
        if (sInstance == null) {
            if (Utilities.isZebraDevice()) {
                ZebraScanner scanner = new ZebraScanner(context);
                scanner.setCallback(sCallback);
                //scanner.initializeZebraScannerManager();
                sInstance = scanner;
            } else if(Utilities.isBluebirdDevice()) {
                EF500BarcodeScanner scanner = new EF500BarcodeScanner(context);
                scanner.setCallback(sCallback);
                sInstance = scanner;
            } else if(Utilities.isPointMobileDevice()) {
                PointMobileScanner scanner = new PointMobileScanner();
                scanner.setCallback(sCallback);
                sInstance = scanner;
            }
            else if(Utilities.isHoneywellDevice()) {
                HoneywellScanner scanner = new HoneywellScanner();
                scanner.setCallback(sCallback);
                sInstance = scanner;
            }
            else {
                sInstance = new IDEngineGenericScanner(context);
                sInstance.setCallback(sCallback);
            }

            return sInstance;
        } else {
            sInstance.setCallback(sCallback);

            return sInstance;
        }
    }

    public static void setCallback(GenericScanningCallback callback) {
        SDGLog.d(TAG, "Setting callback to " + callback);
        sInstance.setCallback(callback);
        sCallback = callback;
    }
}
