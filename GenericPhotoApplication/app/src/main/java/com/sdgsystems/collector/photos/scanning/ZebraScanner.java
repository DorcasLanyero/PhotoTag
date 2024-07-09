package com.sdgsystems.collector.photos.scanning;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.sdgsystems.blueloggerclient.SDGLog;
import com.sdgsystems.collector.photos.Constants;
import com.symbol.emdk.EMDKManager;
import com.symbol.emdk.EMDKResults;
import com.symbol.emdk.EMDKManager.EMDKListener;
import com.symbol.emdk.EMDKManager.FEATURE_TYPE;
import com.symbol.emdk.barcode.BarcodeManager;
import com.symbol.emdk.barcode.BarcodeManager.ConnectionState;
import com.symbol.emdk.barcode.BarcodeManager.ScannerConnectionListener;
import com.symbol.emdk.barcode.ScanDataCollection;
import com.symbol.emdk.barcode.Scanner;
import com.symbol.emdk.barcode.ScannerConfig;
import com.symbol.emdk.barcode.ScannerException;
import com.symbol.emdk.barcode.ScannerInfo;
import com.symbol.emdk.barcode.ScannerResults;
import com.symbol.emdk.barcode.ScanDataCollection.ScanData;
import com.symbol.emdk.barcode.Scanner.DataListener;
import com.symbol.emdk.barcode.Scanner.StatusListener;
import com.symbol.emdk.barcode.Scanner.TriggerType;
import com.symbol.emdk.barcode.StatusData.ScannerStates;
import com.symbol.emdk.barcode.StatusData;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.sdgsystems.collector.photos.Constants.ScannerType.SCANNER_TYPE_BARCODE;


/**
 * Created by bfriedberg on 8/1/17.
 */

public class ZebraScanner implements EMDKListener, DataListener, StatusListener, ScannerConnectionListener, IScanner{

    private static final String TAG = "SymbolZebraScanner";
    private GenericScanningCallback mCallback;
    private Context mContext;

    private TriggerType originalTrigger = null;
    private EMDKManager emdkManager = null;
    private BarcodeManager barcodeManager = null;
    private Scanner scanner = null;

    private boolean bContinuousMode = false;
    private boolean reconnect = false;

    private int scannerIndex = 0; // Keep the selected scanner
    private int defaultIndex = 0; // Keep the default scanner

    private String statusString = "";

    private List<ScannerInfo> deviceList = null;
    private boolean sendResults;

    public ZebraScanner(Context context) {
        mContext = context;
    }

    @Override
    public void init() {

    }

    @Override
    public void deinit() {

    }

    private void setup() {
        SDGLog.d(TAG, "setup");

        initializeZebraScannerManager();
    }

    private void teardown() {
        SDGLog.d(TAG, "close called from abstraction layer");

        // Restore original trigger type
        if(originalTrigger != null) setTrigger(originalTrigger);

        // De-initialize scanner
        deInitScanner();

        // Remove connection listener
        if (barcodeManager != null) {
            barcodeManager.removeConnectionListener(this);
            barcodeManager = null;
        }

        // Release all the resources
        if (emdkManager != null) {
            emdkManager.release();
            emdkManager = null;

        }

        mCallback.scannerClosed();

        if(reconnect) {
            reconnect = false;
            setup();
        }
    }

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


    public void initializeZebraScannerManager() {
        SDGLog.d(TAG, "initialize Zebra Scanner Manager");

        deviceList = new ArrayList<ScannerInfo>();
        EMDKResults results = EMDKManager.getEMDKManager(mContext, this);
    }

    private void initBarcodeManager() {
        barcodeManager = (BarcodeManager) emdkManager.getInstance(FEATURE_TYPE.BARCODE);

        // Add connection listener
        if (barcodeManager != null) {
            barcodeManager.addConnectionListener(this);
        }
        else {
            SDGLog.w(TAG, "Zebra barcode manager is null");
        }
    }

    private void initializeScanner() {
        initScanner();
        originalTrigger = getTrigger();
        setTrigger(TriggerType.HARD);
        setDecoders();
    }

    @Override
    public void setScannerIndex(int index) {
        if(barcodeManager == null) {
            initBarcodeManager();
        }

        if(barcodeManager != null) {
            if (index != scannerIndex) {
                scannerIndex = index;

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
                prefs.edit().putInt(Constants.PREF_DEVICE_ID, index).commit();

                deInitScanner();

                initializeScanner();
            }
        }
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
        if(!softScanStarted) triggerScanning(closeOnScan, false);
        else stopSoftScan();
    }

    private boolean softScanStarted = false;

    private void stopSoftScan() {
        if(scanner != null) {
            if(scanner.isReadPending()) {
                try {
                    scanner.cancelRead();
                }
                catch(ScannerException e) {
                    SDGLog.w(TAG, "Failed to cancel soft scan");
                }

                // go back to waiting for the hard trigger
                triggerScanning(true, true);
            }
        }
    }

    private void triggerScanning(boolean closeOnScan, boolean hardTrigger) {
        SDGLog.d(TAG, "enabling scanning via trigger");

        if(scanner == null) {
            initScanner();
        }

        if (scanner != null) {
            try {

                if(!scanner.isEnabled()) {
                    scanner.enable();
                    SDGLog.d(TAG, "Scanner not enabled; enabling it");
                }

                if(scanner.isEnabled())
                {
                    if(hardTrigger) setTrigger(TriggerType.HARD);
                    else setTrigger(TriggerType.SOFT_ONCE);

                    if(scanner.isReadPending()) {
                        scanner.cancelRead();
                    }

                    // Submit a new read.
                    scanner.read();
                    bContinuousMode = true;
                }
                else
                {
                    mCallback.statusMessage("Status: Scanner is not enabled", true);
                }

            } catch (ScannerException e) {
                SDGLog.d(TAG, "Status: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void cancelScanning() {
        if (scanner != null) {
            try {
                if(scanner.isEnabled()) {
                    scanner.cancelRead();
                }
             } catch (ScannerException ex) {
                ex.printStackTrace();
             }
        }
    }

    @Override
    public boolean open() {
        reconnect = true;
        teardown();

        sendResults = true;

        return true;
    }

    @Override
    public void close() {
        sendResults = false;
        teardown();
    }

    @Override
    public void onOpened(EMDKManager emdkManager) {

        SDGLog.d(TAG, "opened zebra scanner");

        this.emdkManager = emdkManager;

        // Acquire the barcode manager resources
        initBarcodeManager();

        // Enumerate scanner devices
        enumerateScanners();

        //Set the scanning index automatically
        setScannerIndex(getScannerIndex());

        //Start listening for the hardware trigger immediately
        SDGLog.d(TAG, "starting scan...");
        triggerScanning(true, true);
    }

    @Override
    public void onClosed() {
        SDGLog.d(TAG, "closed zebra scanner");

        if (emdkManager != null) {

            // Remove connection listener
            if (barcodeManager != null){
                barcodeManager.removeConnectionListener(this);
                barcodeManager = null;
            }

            // Release all the resources
            emdkManager.release();
            emdkManager = null;
        }
        mCallback.statusMessage("Status: " + "EMDK closed unexpectedly! Please close and restart the application.", true);

        mCallback.scannerClosed();
    }

    @Override
    public void onConnectionChange(ScannerInfo scannerInfo, ConnectionState connectionState) {
        String status;
        String scannerName = "";

        String statusExtScanner = connectionState.toString();
        String scannerNameExtScanner = scannerInfo.getFriendlyName();

        if (deviceList.size() != 0) {
            scannerName = deviceList.get(scannerIndex).getFriendlyName();
        }

        SDGLog.d(TAG, "connection change");

        if (scannerName.equalsIgnoreCase(scannerNameExtScanner)) {

            switch(connectionState) {
                case CONNECTED:
                    SDGLog.d(TAG, "bouncing scanner based on connection change");

                    deInitScanner();
                    initializeScanner();

                    SDGLog.d(TAG, "triggering scan based on bounce");
                    triggerScanning(true, true);

                    break;
                case DISCONNECTED:
                    SDGLog.d(TAG, "disconnected, deinitting scanning");

                    deInitScanner();
                    break;
            }

            status = scannerNameExtScanner + ":" + statusExtScanner;
            mCallback.statusMessage(status, false);
        }
        else {
            status =  statusString + " " + scannerNameExtScanner + ":" + statusExtScanner;
            mCallback.statusMessage(status, false);
        }
    }

    @Override
    public void onData(ScanDataCollection scanDataCollection) {
        SDGLog.d(TAG, "ondata");

        if (sendResults && (scanDataCollection != null) && (scanDataCollection.getResult() == ScannerResults.SUCCESS)) {
            ArrayList <ScanData> scanData = scanDataCollection.getScanData();
            for(ScanData data : scanData) {
                String dataString =  data.getData();

                mCallback.scanAvailable(dataString, Constants.ScannerType.SCANNER_TYPE_BARCODE);
            }
        }
    }

    boolean scanningFired = false;

    @Override
    public void onStatus(StatusData statusData) {
        ScannerStates state = statusData.getState();

        SDGLog.d(TAG, "state: " + state + " scanningFired: " + scanningFired);

        switch(state) {
            case IDLE:
                statusString = statusData.getFriendlyName()+" is enabled and idle...";
                if (bContinuousMode) {
                    try {
                        // An attempt to use the scanner continuously and rapidly (with a delay < 100 ms between scans)
                        // may cause the scanner to pause momentarily before resuming the scanning.
                        // Hence add some delay (>= 100ms) before submitting the next read.
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        if(scanner != null) scanner.read();
                    } catch (ScannerException e) {
                        statusString = e.getMessage();
                        mCallback.statusMessage(statusString, true);
                    }
                }

                if(scanningFired) {
                    SDGLog.d(TAG, "Scanning complete " + state);
                    mCallback.scanningComplete();
                    scanningFired = false;
                } else {
                    SDGLog.d(TAG, "wasn't Scanning");
                }

                SDGLog.d(TAG, statusString);
                softScanStarted = false;

                break;
            case WAITING:

                if(scanningFired) {
                    mCallback.scanningComplete();
                    SDGLog.d(TAG, "Scanning complete " + state);
                    softScanStarted = false;

                    scanningFired = false;
                } else {
                    SDGLog.d(TAG, "wasn't Scanning");
                }

                statusString = "Scanner is waiting for trigger press...";
                SDGLog.d(TAG, statusString);
                break;
            case SCANNING:
                statusString = "Scanning...";
                scanningFired = true;
                SDGLog.d(TAG, statusString);
                break;
            case DISABLED:
                statusString = statusData.getFriendlyName()+" is disabled.";
                SDGLog.d(TAG, statusString);
                break;
            case ERROR:
                statusString = "An error has occurred.";
                SDGLog.d(TAG, statusString);
                mCallback.statusMessage(statusString, true);
                break;
            default:
                break;
        }
    }

    private void initScanner() {

        SDGLog.d(TAG, "setup scanner");

        if(barcodeManager != null) {
            if (scanner == null) {

                if ((deviceList != null) && (deviceList.size() != 0)) {
                    //Check the callback to see if we should use camera or hardware scanning
                    scanner = barcodeManager.getDevice(deviceList.get(mCallback.shouldUseCameraScanning() ? defaultIndex : scannerIndex));
                } else {
                    // We'll catch it later.
                    mCallback.statusMessage("Status: " + "Failed to get the specified scanner device! Please close and restart the application.", true);
                    return;
                }

                if (scanner != null) {

                    scanner.addDataListener(this);
                    scanner.addStatusListener(this);

                    try {
                        scanner.enable();
                    } catch (ScannerException e) {

                        mCallback.statusMessage("345 Status: " + e.getMessage(), true);
                    }
                } else {
                    mCallback.statusMessage("Status: " + "Failed to initialize the scanner device.", true);
                }
            }
        }
        else {
            SDGLog.w(TAG, "Barcode manager still null!");
        }
    }

    private void deInitScanner() {

        SDGLog.d(TAG, "teardown scanner");

        if (scanner != null) {

            try {

                scanner.cancelRead();
                scanner.disable();

            } catch (Exception e) {
                mCallback.statusMessage(e.getMessage(), true);
            }

            try {
                scanner.removeDataListener(this);
                scanner.removeStatusListener(this);

            } catch (Exception e) {
                mCallback.statusMessage(e.getMessage(), true);
            }

            try{
                scanner.release();
            } catch (Exception e) {
                mCallback.statusMessage(e.getMessage(), true);
            }

            scanner = null;
        }
    }

    @Override
    public List<ScannerOption> enumerateScanners() {
        List<ScannerOption> friendlyNameList = new ArrayList<ScannerOption>();

        if(barcodeManager == null) {
            initBarcodeManager();
        }

        if (barcodeManager != null) {

            int spinnerIndex = 0;

            deviceList = barcodeManager.getSupportedDevicesInfo();

            if ((deviceList != null) && (deviceList.size() != 0)) {

                Iterator<ScannerInfo> it = deviceList.iterator();
                while(it.hasNext()) {
                    ScannerInfo scnInfo = it.next();

                    ScannerOption option = new ScannerOption();
                    option.name = scnInfo.getFriendlyName();
                    option.index = spinnerIndex;

                    //Don't add bluetooth scanners for now...
                    if(!option.name.toLowerCase().contains("bluetooth")) {
                        friendlyNameList.add(option);
                        if (scnInfo.isDefaultScanner()) {
                            defaultIndex = spinnerIndex;
                        }
                        ++spinnerIndex;
                    }
                }
            }
            else {
                mCallback.statusMessage("Status: " + "Failed to get the list of supported scanner devices! Please close and restart the application.", true);
            }
        }

        return friendlyNameList;
    }

    private TriggerType getTrigger() {
        if (scanner == null) {
            initScanner();
        }

        if (scanner != null) {
            SDGLog.d(TAG, "Old trigger type: " + scanner.triggerType);
            return scanner.triggerType;
        }
        else {
            SDGLog.w(TAG, "Unable to get trigger type: scanner is null");
        }
        return null;
    }


    private void setTrigger(TriggerType type) {
        if (scanner == null) {
            initScanner();
        }

        if (scanner != null) {
            scanner.triggerType = type;
            SDGLog.d(TAG, "Set trigger type to " + type);
        }
        else {
            SDGLog.w(TAG, "Unable to set trigger type: scanner is null");
        }
    }

    private void setDecoders() {

        if (scanner == null) {
            initScanner();
        }

        if ((scanner != null) && (scanner.isEnabled())) {
            try {

                ScannerConfig config = scanner.getConfig();

                //Enable all symbologies
                config.decoderParams.ean8.enabled = true;
                config.decoderParams.ean13.enabled = true;
                config.decoderParams.code39.enabled = true;
                config.decoderParams.code128.enabled = true;

                scanner.setConfig(config);

            } catch (ScannerException e) {
                //mCallback.statusMessage("451 Status: " + e.getMessage(), true);
            }
        }
    }

    public int getScannerIndex() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        return prefs.getInt(Constants.PREF_DEVICE_ID, defaultIndex);
    }
}
