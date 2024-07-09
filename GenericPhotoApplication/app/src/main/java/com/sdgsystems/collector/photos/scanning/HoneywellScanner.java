package com.sdgsystems.collector.photos.scanning;

import android.util.Log;

import com.sdgsystems.blueloggerclient.SDGLog;
import com.sdgsystems.collector.photos.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.honeywell.aidc.*;
import com.sdgsystems.collector.photos.GenericPhotoApplication;

public class HoneywellScanner implements IScanner {
    private AidcManager mManager;
    private BarcodeReader mReader;

    private boolean mConnectionComplete;
    private GenericScanningCallback mCallback;

    private BarcodeReader.BarcodeListener mListener = new BarcodeReader.BarcodeListener() {
        @Override
        public void onBarcodeEvent(BarcodeReadEvent barcodeReadEvent) {
            cancelTrigger();

            String data = barcodeReadEvent.getBarcodeData();
            if(mCallback != null) mCallback.scanAvailable(data, Constants.ScannerType.SCANNER_TYPE_BARCODE);
        }

        @Override
        public void onFailureEvent(BarcodeFailureEvent barcodeFailureEvent) {
            cancelTrigger();
        }
    };

    @Override
    public void setScannerIndex(int index) {

    }

    @Override
    public int getScannerIndex() {
        return 0;
    }

    @Override
    public List<ScannerOption> enumerateScanners() {
        ArrayList<ScannerOption> list = new ArrayList<>();
        list.add(new ScannerOption(0, "Honeywell"));
        return list;
    }

    @Override
    public void setCallback(GenericScanningCallback c) {
        mCallback = c;
    }

    @Override
    public void triggerScanning() {
        triggerScanning(true);
    }

    @Override
    public void triggerScanning(boolean closeOnScan) {
        try {
            mReader.softwareTrigger(true);
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void cancelScanning() {
        cancelTrigger();
    }

    @Override
    public boolean open() {
        try {
            if(mReader != null) {
                setupReader();
            }
        }
        catch(Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    @Override
    public void close() {
        if(mReader != null) {
            try {
                mReader.close();
            }
            catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void init() {
        AidcManager.create(GenericPhotoApplication.getInstance().getApplicationContext(), aidcManager -> {
            mManager = aidcManager;
            setupReader();
        });
    }

    private void setupReader() {
        if(mReader == null) {
            SDGLog.d("HoneywellScanner", "Attempted to set up null reader");
        }

        mReader = mManager.createBarcodeReader();

        Map<String, Object> properties = new HashMap<>();
        properties.put(BarcodeReader.PROPERTY_AZTEC_ENABLED, true);
        properties.put(BarcodeReader.PROPERTY_CODABAR_ENABLED, true);
        properties.put(BarcodeReader.PROPERTY_CODE_11_ENABLED, true);
        properties.put(BarcodeReader.PROPERTY_CODE_39_ENABLED, true);
        properties.put(BarcodeReader.PROPERTY_CODE_93_ENABLED, true);
        properties.put(BarcodeReader.PROPERTY_CODE_128_ENABLED, true);
        properties.put(BarcodeReader.PROPERTY_DATAMATRIX_ENABLED, true);
        properties.put(BarcodeReader.PROPERTY_EAN_8_ENABLED, true);
        properties.put(BarcodeReader.PROPERTY_EAN_13_ENABLED, true);
        properties.put(BarcodeReader.PROPERTY_INTERLEAVED_25_ENABLED, true);
        properties.put(BarcodeReader.PROPERTY_MICRO_PDF_417_ENABLED, true);
        properties.put(BarcodeReader.PROPERTY_PDF_417_ENABLED, true);
        properties.put(BarcodeReader.PROPERTY_QR_CODE_ENABLED, true);
        properties.put(BarcodeReader.PROPERTY_UPC_A_ENABLE, true);
        properties.put(BarcodeReader.PROPERTY_UPC_E_ENABLED, true);
        properties.put(BarcodeReader.PROPERTY_TRIGGER_CONTROL_MODE, BarcodeReader.TRIGGER_CONTROL_MODE_AUTO_CONTROL);

        mReader.setProperties(properties);

        try {
            mReader.claim();
        }
        catch (ScannerUnavailableException e) {
            e.printStackTrace();
            return;
        }

        mReader.addBarcodeListener(mListener);
        mConnectionComplete = true;
    }

    @Override
    public void deinit() {
        if(mReader != null) {
            mReader.removeBarcodeListener(mListener);
            mReader.close();
        }

        if(mManager != null) {
            mManager.close();
        }
    }

    @Override
    public boolean isScannerReady() {
        return mConnectionComplete;
    }

    @Override
    public boolean isBarcode() {
        return true;
    }

    @Override
    public Constants.ScannerType getScannerType() {
        return Constants.ScannerType.SCANNER_TYPE_BARCODE;
    }

    @Override
    public boolean hasSettingsActivity() {
        return false;
    }

    @Override
    public void startSettingsActivity() {

    }

    private void cancelTrigger() {
        if(mReader != null) {
            try {
                mReader.softwareTrigger(false);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
