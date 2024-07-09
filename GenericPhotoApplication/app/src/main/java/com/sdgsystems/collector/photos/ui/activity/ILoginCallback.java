package com.sdgsystems.collector.photos.ui.activity;

public interface ILoginCallback {
    void loginAttempted(boolean success);
    void dataChanged();
}
