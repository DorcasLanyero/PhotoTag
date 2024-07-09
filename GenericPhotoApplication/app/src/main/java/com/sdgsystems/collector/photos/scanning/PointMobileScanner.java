package com.sdgsystems.collector.photos.scanning;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;

import com.sdgsystems.collector.photos.Constants;
import com.sdgsystems.collector.photos.GenericPhotoApplication;

import java.util.ArrayList;
import java.util.List;

import device.common.DecodeResult;
import device.common.ScanConst;
import device.sdk.ScanManager;

import static com.sdgsystems.collector.photos.Constants.ScannerType.SCANNER_TYPE_BARCODE;

class PointMobileScanner implements IScanner {
    private ScanManager scanManager = new ScanManager();
    private DecodeResult decodeResult = new DecodeResult();
    private GenericScanningCallback appCallback;
    private BroadcastReceiver scanReceiver;

    private int scannerIndex;
    private int previousResultType;

    @Override
    public void setScannerIndex(int index) {
        if(index != scannerIndex) {
            scannerIndex = index;

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(GenericPhotoApplication.getInstance().getApplicationContext());
            prefs.edit().putInt(Constants.PREF_DEVICE_ID, index).commit();
        }
    }

    @Override
    public int getScannerIndex() {
        return scannerIndex;
    }

    @Override
    public List<ScannerOption> enumerateScanners() {
        ScannerOption o = new ScannerOption();
        o.name = "Built-In Scanner";
        o.index = 0;
        List<ScannerOption> result = new ArrayList<>(1);
        result.add(o);
        return result;
    }

    @Override
    public void setCallback(GenericScanningCallback c) {
        appCallback = c;
    }

    @Override
    public void triggerScanning() {
        scanManager.aDecodeSetTriggerOn(1);

        Handler h = new Handler(Looper.getMainLooper());
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                cancelScanning();
            }
        }, 5000); // time out after 5 seconds
    }

    @Override
    public void triggerScanning(boolean closeOnScan) {
        triggerScanning();
    }

    @Override
    public void cancelScanning() {
        scanManager.aDecodeSetTriggerOn(0);
    }

    @Override
    public boolean open() {
        Context c = GenericPhotoApplication.getInstance().getApplicationContext();
        scannerIndex = PreferenceManager.getDefaultSharedPreferences(c).getInt(Constants.PREF_DEVICE_ID, 0);

        scanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                scanManager.aDecodeGetResult(decodeResult.recycle());
                String result = new String(decodeResult.decodeValue);
                if (result.equals("READ_FAIL")) return;

                appCallback.scanAvailable(result, Constants.ScannerType.SCANNER_TYPE_BARCODE);
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction("device.common.USERMSG");
        c.registerReceiver(scanReceiver, f);

        previousResultType = scanManager.aDecodeGetResultType();
        scanManager.aDecodeSetResultType(ScanConst.ResultType.DCD_RESULT_USERMSG);

        if (appCallback != null) appCallback.scannerConnected();
        return true;
    }

    @Override
    public void close() {
        Context c = GenericPhotoApplication.getInstance().getApplicationContext();

        if(scanReceiver != null) {
            try {
                c.unregisterReceiver(scanReceiver);
            }
            catch(Exception e) {
                // saw an exception thrown for unregistering an unregistered receiver
            }
        }

        scanManager.aDecodeSetResultType(previousResultType);

        if(appCallback != null) appCallback.scannerClosed();
    }

    @Override
    public void init() {
    }

    @Override
    public void deinit() {}

    @Override
    public boolean isScannerReady() {
        return true;
    }

    @Override
    public boolean isBarcode() {
        return true;
    }

    @Override
    public Constants.ScannerType getScannerType() {
        return SCANNER_TYPE_BARCODE;
    }

    @Override
    public boolean hasSettingsActivity() {
        return false;
    }

    @Override
    public void startSettingsActivity() {}


}
