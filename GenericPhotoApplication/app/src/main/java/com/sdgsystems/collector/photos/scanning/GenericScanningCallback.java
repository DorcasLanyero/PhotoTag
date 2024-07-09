package com.sdgsystems.collector.photos.scanning;

import com.sdgsystems.collector.photos.Constants;

/**
 * Created by bfriedberg on 8/1/17.
 */

public interface GenericScanningCallback {
    public void scanAvailable(String scanData, Constants.ScannerType scannerType);
    public void statusMessage(String message, boolean error);
    public void cameraScanningStarted();
    public void scanningComplete();
    public void scannerClosed();
    public void scannerConnected();
    public boolean shouldUseCameraScanning();
}
