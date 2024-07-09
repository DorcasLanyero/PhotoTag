package com.sdgsystems.collector.photos.scanning;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import androidx.annotation.NonNull;

import com.sdgsystems.blueloggerclient.SDGLog;
import com.sdgsystems.collector.photos.Constants;

import java.util.ArrayList;
import java.util.List;

import static com.sdgsystems.collector.photos.Constants.ScannerType.SCANNER_TYPE_BARCODE;

/**
 * Created by bfriedberg on 1/8/18.
 */

public class EF500BarcodeScanner implements IScanner {

    public static final String ACTION_BARCODE_OPEN = "kr.co.bluebird.android.bbapi.action.BARCODE_OPEN";
    public static final String ACTION_BARCODE_CLOSE = "kr.co.bluebird.android.bbapi.action.BARCODE_CLOSE";
    public static final String ACTION_BARCODE_SET_TRIGGER = "kr.co.bluebird.android.bbapi.action.BARCODE_SET_TRIGGER";
    public static final String ACTION_BARCODE_SET_DEFAULT_PROFILE = "kr.co.bluebird.android.bbapi.action.BARCODE_SET_DEFAULT_PROFILE";
    public static final String ACTION_BARCODE_SETTING_CHANGED = "kr.co.bluebird.android.bbapi.action.BARCODE_SETTING_CHANGED";
    public static final String ACTION_BARCODE_CALLBACK_REQUEST_SUCCESS = "kr.co.bluebird.android.bbapi.action.BARCODE_CALLBACK_REQUEST_SUCCESS";
    public static final String ACTION_BARCODE_CALLBACK_REQUEST_FAILED = "kr.co.bluebird.android.bbapi.action.BARCODE_CALLBACK_REQUEST_FAILED";
    public static final String ACTION_BARCODE_CALLBACK_DECODING_DATA = "kr.co.bluebird.android.bbapi.action.BARCODE_CALLBACK_DECODING_DATA";
    public static final String ACTION_MDM_BARCODE_SET_SYMBOLOGY = "kr.co.bluebird.android.bbapi.action.MDM_BARCODE_SET_SYMBOLOGY";
    public static final String ACTION_MDM_BARCODE_SET_MODE = "kr.co.bluebird.android.bbapi.action.MDM_BARCODE_SET_MODE";
    public static final String ACTION_MDM_BARCODE_SET_DEFAULT = "kr.co.bluebird.android.bbapi.action.MDM_BARCODE_SET_DEFAULT";

    //default profile setting done.
    public static final String ACTION_BARCODE_CALLBACK_DEFAULT_PROFILE_SETTING_COMPLETE = "kr.co.bluebird.android.bbapi.action.BARCODE_DEFAULT_PROFILE_SETTING_COMPLETE";
    //request barcode status
    public static final String ACTION_BARCODE_GET_STATUS = "kr.co.bluebird.android.action.BARCODE_GET_STATUS";
    //repond barcode status
    public static final String ACTION_BARCODE_CALLBACK_GET_STATUS = "kr.co.bluebird.android.action.BARCODE_CALLBACK_GET_STATUS";

    //barcode status
    public static final int BARCODE_CLOSE = 0;
    public static final int BARCODE_OPEN = 1;
    public static final int BARCODE_TRIGGER_ON = 2;

    public static final String EXTRA_BARCODE_BOOT_COMPLETE = "EXTRA_BARCODE_BOOT_COMPLETE";
    public static final String EXTRA_BARCODE_PROFILE_NAME = "EXTRA_BARCODE_PROFILE_NAME";
    public static final String EXTRA_BARCODE_TRIGGER = "EXTRA_BARCODE_TRIGGER";
    public static final String EXTRA_BARCODE_DECODING_DATA = "EXTRA_BARCODE_DECODING_DATA";
    public static final String EXTRA_HANDLE = "EXTRA_HANDLE";
    public static final String EXTRA_INT_DATA2 = "EXTRA_INT_DATA2";
    public static final String EXTRA_STR_DATA1 = "EXTRA_STR_DATA1";
    public static final String EXTRA_INT_DATA3 = "EXTRA_INT_DATA3";

    public static final int ERROR_FAILED = -1;
    public static final int ERROR_NOT_SUPPORTED = -2;
    public static final int ERROR_NO_RESPONSE = -4;
    public static final int ERROR_BATTERY_LOW = -5;
    public static final int ERROR_BARCODE_DECODING_TIMEOUT = -6;
    public static final int ERROR_BARCODE_ERROR_USE_TIMEOUT = -7;
    public static final int ERROR_BARCODE_ERROR_ALREADY_OPENED = -8;

    public static final int MDM_MSR_MODE__SET_READING_TIMEOUT = 0;

    private static final int SEQ_BARCODE_OPEN = 100;
    private static final int SEQ_BARCODE_CLOSE = 200;
    private static final int SEQ_BARCODE_GET_STATUS = 300;
    private static final int SEQ_BARCODE_SET_TRIGGER_ON = 400;
    private static final int SEQ_BARCODE_SET_TRIGGER_OFF = 500;
    private static final String TAG = "EF500Scanner";
    private final Context mReceiverContext;

    private GenericScanningCallback callback;

    private String mCurrentStatus;

    private boolean mRegisteredForBroadcasts;

    private static final String STATUS_CLOSE = "STATUS_CLOSE";
    private static final String STATUS_OPEN = "STATUS_OPEN";
    private static final String STATUS_TRIGGER_ON = "STATUS_TRIGGER_ON";
    private boolean mIsOpened = false;

    public EF500BarcodeScanner(@NonNull Context context) {
        mReceiverContext = context;
    }

    @Override
    public void setScannerIndex(int index) {

    }

    @Override
    public int getScannerIndex() {
        return mIsOpened ? 0 : -1;
    }

    @Override
    public List<ScannerOption> enumerateScanners() {
        ArrayList<ScannerOption> list = new ArrayList<>();
        list.add(new ScannerOption(0, "EF500"));
        return list;
    }

    @Override
    public void setCallback(GenericScanningCallback c) {
        callback = c;
    }

    @Override
    public void triggerScanning() {
        triggerScanning(true);
    }

    @Override
    public void triggerScanning(boolean closeOnScan) {
        if(mIsOpened) {
            Intent intent = new Intent();
            intent.setAction(ACTION_BARCODE_SET_TRIGGER);
            intent.putExtra(EXTRA_HANDLE, mBarcodeHandle);
            intent.putExtra(EXTRA_INT_DATA2, 1);
            intent.putExtra(EXTRA_INT_DATA3,
                    SEQ_BARCODE_SET_TRIGGER_ON);
            mReceiverContext.sendBroadcast(intent);
        } else {
            callback.statusMessage("Scanner not open", true);
        }
    }

    @Override
    public void cancelScanning() {
        if(mIsOpened) {
            Intent intent = new Intent();
            intent.setAction(ACTION_BARCODE_SET_TRIGGER);
            intent.putExtra(EXTRA_HANDLE, mBarcodeHandle);
            intent.putExtra(EXTRA_INT_DATA2, 0);
            intent.putExtra(EXTRA_INT_DATA3,
                    SEQ_BARCODE_SET_TRIGGER_OFF);
            mReceiverContext.sendBroadcast(intent);
        } else {
            callback.statusMessage("Scanner not open", true);
        }
    }

    @Override
    public boolean open() {
        return true;
    }

    @Override
    public void close() {

    }

    @Override
    public void init() {
        if (!mRegisteredForBroadcasts) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_BARCODE_CALLBACK_DECODING_DATA);
            filter.addAction(ACTION_BARCODE_CALLBACK_REQUEST_SUCCESS);
            filter.addAction(ACTION_BARCODE_CALLBACK_REQUEST_FAILED);
            filter.addAction(ACTION_BARCODE_CALLBACK_GET_STATUS);

            mReceiverContext.registerReceiver(mReceiver, filter);
            mRegisteredForBroadcasts = true;
        }

        Intent intent = new Intent();
        intent.setAction(ACTION_BARCODE_OPEN);
        if (mIsOpened)
            intent.putExtra(EXTRA_HANDLE, mBarcodeHandle);
        intent.putExtra(EXTRA_INT_DATA3, SEQ_BARCODE_OPEN);
        mReceiverContext.sendBroadcast(intent);
        mIsOpened = true;

    }

    @Override
    public void deinit() {
        if(mRegisteredForBroadcasts && mReceiver != null) {
            try {
                mRegisteredForBroadcasts = false;
                mReceiverContext.unregisterReceiver(mReceiver);
            } catch(IllegalArgumentException ex) {
                SDGLog.w(TAG, "Illegal argument, receiver PROBABLY wasn't registered or something dumb happened lifecycle-wise...");
            }
        }

        Intent intent = new Intent();
        intent.setAction(ACTION_BARCODE_CLOSE);
        intent.putExtra(EXTRA_HANDLE, mBarcodeHandle);
        intent.putExtra(EXTRA_INT_DATA3, SEQ_BARCODE_CLOSE);
        mReceiverContext.sendBroadcast(intent);
        mIsOpened = false;
    }

    @Override
    public boolean isScannerReady() {
        return mIsOpened;
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

    private int mBarcodeHandle;
    private int mCount = 0;

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            int seq = intent.getIntExtra(EXTRA_INT_DATA3, 0);

            if (action.equals(ACTION_BARCODE_CALLBACK_DECODING_DATA)) {
                int handle = intent.getIntExtra(EXTRA_HANDLE, 0);
                byte[] data = intent
                        .getByteArrayExtra(EXTRA_BARCODE_DECODING_DATA);
                String result = "[BarcodeDecodingData handle : " + handle
                        + " / count : " + mCount + " / seq : " + seq + "]\n";
                if (data != null)
                    result += "[Data] : " + new String(data);

                callback.scanAvailable(new String(data), SCANNER_TYPE_BARCODE);

                SDGLog.d(TAG,result);
                mCount++;
            } else if (action
                    .equals(ACTION_BARCODE_CALLBACK_REQUEST_SUCCESS)) {
                mBarcodeHandle = intent.getIntExtra(EXTRA_HANDLE, 0);

                SDGLog.d(TAG,"Success : " + seq);

                if (seq == SEQ_BARCODE_OPEN) {
                    mCurrentStatus = STATUS_OPEN;
                    callback.scannerConnected();
                } else if (seq == SEQ_BARCODE_CLOSE) {
                    mCurrentStatus = STATUS_CLOSE;
                    callback.scannerClosed();
                }

            } else if (action
                    .equals(ACTION_BARCODE_CALLBACK_REQUEST_FAILED)) {
                int result = intent.getIntExtra(EXTRA_INT_DATA2, 0);
                if (result == ERROR_BARCODE_DECODING_TIMEOUT) {
                    SDGLog.d(TAG,"Failed result : " + "Decode Timeout"
                            + " / seq : " + seq);
                } else if (result == ERROR_NOT_SUPPORTED) {
                    SDGLog.d(TAG,"Failed result : " + "Not Supoorted" + " / seq : " + seq);
                } else if (result == ERROR_BARCODE_ERROR_USE_TIMEOUT) {
                    mCurrentStatus = STATUS_CLOSE;
                    SDGLog.d(TAG,"Failed result : " + "Use Timeout" + " / seq : " + seq);
                } else {
                    SDGLog.d(TAG,"Failed result : " + result + " / seq : " + seq);
                }

                callback.statusMessage("request failed: " + result, true);
            } else if (action
                    .equals(ACTION_BARCODE_CALLBACK_GET_STATUS)) {
                int status = intent.getIntExtra(EXTRA_INT_DATA2, 0);

                switch (status) {
                    case 0:
                        mCurrentStatus = STATUS_CLOSE;
                        break;
                    case 1:
                        mCurrentStatus = STATUS_OPEN;
                        break;
                    case 2:
                        mCurrentStatus = STATUS_TRIGGER_ON;
                        break;
                }

                callback.statusMessage("Status: " + mCurrentStatus, false);
                SDGLog.d(TAG,mCurrentStatus);
            }
        }
    };
}
