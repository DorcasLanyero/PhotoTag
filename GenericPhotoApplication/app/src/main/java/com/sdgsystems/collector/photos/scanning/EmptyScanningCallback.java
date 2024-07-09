package com.sdgsystems.collector.photos.scanning;

import com.sdgsystems.collector.photos.Constants;

class EmptyScanningCallback implements GenericScanningCallback {
    @Override
    public void scanAvailable(String scanData, Constants.ScannerType scannerType) {

    }

    @Override
    public void statusMessage(String message, boolean error) {

    }

    @Override
    public void cameraScanningStarted() {

    }

    @Override
    public void scanningComplete() {

    }

    @Override
    public void scannerClosed() {

    }

    @Override
    public void scannerConnected() {

    }

    @Override
    public boolean shouldUseCameraScanning() {
        return false;
    }
}
