package com.sdgsystems.collector.photos.scanning;

import com.sdgsystems.collector.photos.Constants;

import java.util.List;

/**
 * Created by bfriedberg on 8/1/17.
 */

public interface IScanner {
    void setScannerIndex(int index);

    /** Returns the index for the current scanner, or -1 if no scanner is open. */
    int getScannerIndex();

    List<ScannerOption> enumerateScanners();
    void setCallback(GenericScanningCallback c);
    void triggerScanning();
    void triggerScanning(boolean closeOnScan);
    void cancelScanning();

    boolean open();
    void close();

    /**
     * setup() is called *once* at application startup
     */
    void init();

    void deinit();
    boolean isScannerReady();
    boolean isBarcode();

    Constants.ScannerType getScannerType();

    boolean hasSettingsActivity();
    void startSettingsActivity();
}
