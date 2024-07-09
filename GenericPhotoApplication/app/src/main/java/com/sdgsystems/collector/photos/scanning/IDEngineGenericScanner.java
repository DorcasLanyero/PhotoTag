package com.sdgsystems.collector.photos.scanning;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.util.Log;

import com.sdgsystems.blueloggerclient.SDGLog;
import com.sdgsystems.collector.photos.Constants;
import com.sdgsystems.idengine.api.Device;
import com.sdgsystems.idengine.api.DeviceCallback;
import com.sdgsystems.idengine.api.DeviceError;
import com.sdgsystems.idengine.api.DeviceException;
import com.sdgsystems.idengine.api.DeviceInfo;
import com.sdgsystems.idengine.api.DeviceManager;
import com.sdgsystems.idengine.api.DeviceResponse;
import com.sdgsystems.idengine.api.ScanFailedResponse;
import com.sdgsystems.idengine.api.ScanStoppedResponse;
import com.sdgsystems.idengine.api.Setting;
import com.sdgsystems.idengine.api.SettingGroup;
import com.sdgsystems.idengine.barcode.api.BarcodeData;
import com.sdgsystems.idengine.rfid.api.RfidTag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.sdgsystems.collector.photos.Constants.ScannerType.SCANNER_TYPE_BARCODE;
import static com.sdgsystems.collector.photos.Constants.ScannerType.SCANNER_TYPE_BLE;
import static com.sdgsystems.collector.photos.Constants.ScannerType.SCANNER_TYPE_NONE;
import static com.sdgsystems.collector.photos.Constants.ScannerType.SCANNER_TYPE_RFID;
import static com.sdgsystems.idengine.api.Constants.DeviceTypes.BARCODE_SCANNER;
import static com.sdgsystems.idengine.api.Constants.DeviceTypes.BLE_ADAPTER;
import static com.sdgsystems.idengine.api.Constants.DeviceTypes.RFID_READER;
import static com.sdgsystems.idengine.api.Constants.SettingGroups.DEVICE;
import static com.sdgsystems.idengine.api.Constants.Settings.START_SCAN;
import static com.sdgsystems.idengine.api.DeviceInfo.FLAG_USES_CAMERA;
import static com.sdgsystems.idengine.internal.Debug.debug;
import static com.sdgsystems.idengine.internal.InternalUtilities.eq;
import static com.sdgsystems.idengine.rfid.api.RfidConstants.Settings.DUPLICATE_TAG_REPORTING;

/**
 * Created by bfriedberg on 11/3/17.
 */

public class IDEngineGenericScanner implements IScanner {
    private static final String TAG = "IDEngineGenericScanner";
    private final Context mContext;
    private GenericScanningCallback genericScanningCallback;
    private DeviceManager mDeviceManager;
    private Device mScanner;
    private boolean mUsingCamera;
    private long mSelectedScannerId = -1;
    private Boolean savedShowBatchMode, savedBatchMode;

    private boolean mCloseOnScan = true;

    IDEngineGenericScanner(Context context) {
        mContext = context;
    }

    @Override
    public void init() {
        SDGLog.d(TAG, "Init, connecting to device manager");
        if (mDeviceManager == null)
            mDeviceManager = new DeviceManager(mContext);
        mDeviceManager.setCallback(mStubApiCallback);
    }

    @Override
    public void deinit() {
        close();
        if (mDeviceManager != null) mDeviceManager.disconnect();
        mDeviceManager = null;
        mSelectedScannerId = -1;
    }

    @Override
    public boolean isBarcode() {
        return getScannerType().equals(SCANNER_TYPE_BARCODE);
    }

    @Override
    public Constants.ScannerType getScannerType() {
        if (!isScannerReady()) {
            SDGLog.d(TAG, "getScannerType: Returning SCANNER_TYPE_NONE because scanner not ready");
            return SCANNER_TYPE_NONE;
        }
        if (mScanner != null && mScanner.getDeviceInfo() != null) {
            String type = mScanner.getDeviceInfo().getDeviceType();
            if (BARCODE_SCANNER.equals(type)) return SCANNER_TYPE_BARCODE;
            if (RFID_READER.equals(type)) return SCANNER_TYPE_RFID;
            if (BLE_ADAPTER.equals(type)) return SCANNER_TYPE_BLE;
        }
        return SCANNER_TYPE_NONE;
    }

    @Override
    public boolean hasSettingsActivity() {
        return true;
    }

    @Override
    public void startSettingsActivity() {
        if (mScanner != null) {
            try {
                mScanner.startDeviceSettingsActivity();
            } catch (ActivityNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void triggerScanning() {
        triggerScanning(true);
    }

    @Override
    public void triggerScanning(boolean closeOnScan) {
        mCloseOnScan = closeOnScan;
        startScan();
    }

    @Override
    public void cancelScanning() {
        if(mScanner != null) {
            try {
                mScanner.setValue(DEVICE, START_SCAN, false);
            } catch (DeviceException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean open() {
        if (mScanner != null && mScanner.isAvailable()) {
            return true;
        } else if (mSelectedScannerId != -1) {
            if (devices.size() == 0) {
                enumerateScanners();
            }
            for (int i = 0; i < devices.size(); i++) {
                DeviceInfo deviceInfo = devices.get(i);
                if (deviceInfo != null && deviceInfo.getId() == mSelectedScannerId) {
                    openIDEngineDevice(devices.get(i));
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void close() {
        SDGLog.d(TAG, "Attempting device close");
        if (mScanner != null) mScanner.close();
        mScanner = null;
        mUsingCamera = false;
        genericScanningCallback.scannerClosed();
    }

    private List<DeviceInfo> devices = Collections.emptyList();

    @Override
    public void setScannerIndex(int index) {
        // Note: We are using the last device list that enumerateScanners()
        // produced so that the index will match.
        if (index >= 0 && index < devices.size()) {
            openIDEngineDevice(devices.get(index));
        }
    }

    /** Returns the index of the currently selected scanner, or -1 if no scanner is selected. */
    @Override
    public int getScannerIndex() {
        // Note: We are using the last device list that enumerateScanners()
        // produced so that the index will match.
        for(int index = 0; index < devices.size(); index++) {
            DeviceInfo device = devices.get(index);
            if (device != null && device.getId() == mSelectedScannerId)
                return index;
        }
        return -1;
    }

    @Override
    public List<ScannerOption> enumerateScanners() {
        List<ScannerOption> scanners = new ArrayList<ScannerOption>();
        devices = mDeviceManager.getAvailableDevices();
        for(int index = 0; index < devices.size(); index++) {
            DeviceInfo device = devices.get(index);
            ScannerOption option = new ScannerOption();
            option.name = device.getName();
            option.index = index;
            scanners.add(option);
        }
        return scanners;
    }

    public void setCallback(GenericScanningCallback c) {

        SDGLog.d(TAG, "Setting callback");

        genericScanningCallback = c;
    }

    public boolean isScanRunning() {
        return (mScanner != null) &&
                (Boolean) mScanner.getValue(DEVICE, START_SCAN, false);
    }

    public boolean isScannerReady() {
        if (mDeviceManager == null || !mDeviceManager.isConnected()) return false;
        // This should never happen since Core includes a camera driver
        if (mDeviceManager.getAvailableDevices().size() == 0) return false;
        // This should never happen, because we try to keep a scanner open at all times.
        if (mScanner == null) return false;
        if (mScanner.isClosed() || !mScanner.isAvailable()) return false;
        return true;
    }

    private void startScan() {
        SDGLog.d(TAG, "startScan()");
        if (isScanRunning()) {
            SDGLog.e(TAG, "startScan(): scan already started!");
            return;
        }

        if (!isScannerReady()) {
            SDGLog.e(TAG, "startScan: No scanner device is open and ready!");
            return;
        }

        if(mUsingCamera) {
            genericScanningCallback.cameraScanningStarted();
        }

        try {

            SDGLog.d(TAG, "starting scan procedure");

            //saveBatchModes();

            SDGLog.d(TAG, "saved batch modes");

            if (isBarcode()) {
                // Don't change batch mode, but if Camera show batch mode setting

                SDGLog.d(TAG, "settings batch modes for scanner");

                //setBatchModes(false, false);
            } else {
                // For RFID readers enable batch mode in ScanNow activity

                SDGLog.d(TAG, "settings batch modes for rfid");

                if (mCloseOnScan) {
                    //setBatchModes(false, null);
                    setValueIfPossible(DEVICE, DUPLICATE_TAG_REPORTING, false);
                } else {
                    //setBatchModes(true, null);
                    setValueIfPossible(DEVICE, DUPLICATE_TAG_REPORTING, true);
                }
            }


            SDGLog.d(TAG, "Calling START_SCAN");
            mScanner.setValue(DEVICE, START_SCAN, true);
        } catch (DeviceException e) {
            DeviceError err = e.getDeviceError();
            String msg = (err != null) ? err.getDescription() : e.toString();
            SDGLog.d(TAG, "Device exception when scanning: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private DeviceCallback mScannerCallback = new DeviceCallback() {

        @Override
        public void onDeviceResponse(DeviceResponse deviceStatus) {

            debug(TAG, "onDeviceResponse()");

            if (deviceStatus instanceof BarcodeData) {
                onScanStatus((BarcodeData) deviceStatus);
            } else if (deviceStatus instanceof RfidTag) {
                RfidTag rr = (RfidTag) deviceStatus;

                debug(TAG, "onDeviceResponse(RfidTag)");
                final String barcode = ((RfidTag) rr).getDataString();
                String symbology = ((RfidTag) rr).getTagProtocol().getShortDescription();

                genericScanningCallback.scanAvailable(barcode, Constants.ScannerType.SCANNER_TYPE_RFID);

                if(mCloseOnScan) {
                    try {
                        mScanner.setValue(DEVICE, START_SCAN, false);
                    } catch (DeviceException e) {
                        e.printStackTrace();
                    }
                }

            } else if(deviceStatus instanceof ScanFailedResponse) {
                debug(TAG, "Scan Failed");

                genericScanningCallback.scanningComplete();
            } else if(deviceStatus instanceof ScanStoppedResponse) {
                debug(TAG, "Scan Stopped");

                genericScanningCallback.scanningComplete();
            }
        }

        public void onScanStatus(BarcodeData scanStatus) {

            debug(TAG, "onScanStatus()");

            debug(TAG, "onScanStatus(ACTION_BARCODE_SCANNED)");

            final String barcode = scanStatus.getBarcode();
            String symbology = scanStatus.getSymbology().getDescription();

            if(mUsingCamera) {
                genericScanningCallback.scanAvailable(barcode, Constants.ScannerType.SCANNER_TYPE_CAMERA);
            }
            else {
                genericScanningCallback.scanAvailable(barcode, SCANNER_TYPE_BARCODE);
            }

            if(mCloseOnScan && !mUsingCamera) {
                try {
                    mScanner.setValue(DEVICE, START_SCAN, false);
                } catch (DeviceException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    boolean setValueIfPossible(SettingGroup g, Setting s, Object value) {
        try {
            mScanner.setValue(g, s, value);
            return true;
        } catch (Exception e) {
            SDGLog.w(TAG, "Error setting value: " + e);
            return false;
        }
    }

    DeviceManager.ApiCallback mStubApiCallback = new DeviceManager.ApiCallback() {

        @Override
        public void onConnected(boolean isConnected) {
            SDGLog.d(TAG, "onConnected(" + isConnected + ")");
        }

        @Override
        public void onError(DeviceError error) {
            SDGLog.d(TAG, "onError(" + error + ")");
        }

        @Override
        public void onDevicesAvailable(List<DeviceInfo> scanners) {
            SDGLog.d(TAG, "onScannersAvailable(" + scanners + ")");

            DeviceInfo backupCameraScanner = null;
            if (mSelectedScannerId == -1 && scanners != null) {

                for (DeviceInfo scanner : scanners) {
                    SDGLog.d(TAG, "testing " + scanner.getName().toLowerCase());

                    if (usesCamera(scanner) && backupCameraScanner == null) {
                        backupCameraScanner = scanner;
                    } else if (!scanner.getName().toLowerCase().contains("ble") && mSelectedScannerId == -1) {
                        SDGLog.d(TAG, "Choosing " + scanner.getName() + " (the first non-camera scanner)");
                        openIDEngineDevice(scanner);

                        return;
                    }
                }

                if (mSelectedScannerId == -1 && backupCameraScanner != null) {
                    openIDEngineDevice(backupCameraScanner);

                    return;
                }
            }
        }
    };

    private static boolean usesCamera(DeviceInfo device) {
        return device != null && (device.getFlags() & FLAG_USES_CAMERA) != 0;
    }

    private void openIDEngineDevice(DeviceInfo scannerInfo) {
        SDGLog.d(TAG, "openScanner(" + scannerInfo + ")");
        if (mScanner != null) {
            if (scannerInfo.getId() == mSelectedScannerId) {
                debug(TAG, "Scanner " + scannerInfo + " already open");
                return;
            }
            // Close the previously-opened scanner
            mScanner.close();
            mScanner = null;
            mUsingCamera = false;
            mSelectedScannerId = -1;
        }
        try {
            mScanner = mDeviceManager.openDevice(scannerInfo);
            mSelectedScannerId = scannerInfo.getId();
            mScanner.setCallback(mScannerCallback);
            mUsingCamera = usesCamera(scannerInfo);
            genericScanningCallback.scannerConnected();
        } catch (DeviceException e) {
            String err = "Could not connect to " + scannerInfo + ": " + e;
            SDGLog.e(TAG, err);
        }
    }
}
