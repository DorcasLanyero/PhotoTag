package com.sdgsystems.collector.photos;

/**
 * Created by bfriedberg on 7/24/17.
 */

public class Constants {
    public static final String PREF_DEVICE_ID = "pref_device_id";
    public static final String PREF_SELECTED_SCANNER_INDEX = "pref_selected_scanner_index";
    public static final String PREF_START_SCREEN = "pref_starting_screen";
    public static final String PREF_ADMIN_PIN = "pref_admin_pin";
    public static final String PREF_LAST_TAG_SCANNED = "PREF_LAST_TAG_SCANNED";

    public static int RETRY_DEFAULT_TIMEOUT_MS = 5000;
    public static int RETRY_DEFAULT_MAX_RETRIES = 3;
    public static float RETRY_DEFAULT_BACKOFF_MULT = 2.0F;

    //Auto logout in an hour
    public static long INTERACTION_TIMEOUT_SECONDS = (86400);

    public static final String ACTION_WIFI_CONNECTED = "com.sdgsystems.collector.photos.action_wifi_connected";
    public static final String ACTION_WIFI_DISCONNECTED = "com.sdgsystems.collector.photos.action_wifi_disconnected";

    public enum ScannerType {
        SCANNER_TYPE_BARCODE, SCANNER_TYPE_RFID, SCANNER_TYPE_NFC, SCANNER_TYPE_CAMERA,
        SCANNER_TYPE_BLE, SCANNER_TYPE_NONE
    }

    public static final String PREF_SUBDOMAIN = "pref_api_subdomain";
    public static final String PREF_DOMAIN = "pref_api_domain";
    public static final String PREF_USERNAME = "pref_api_username";
    public static final String PREF_PASSWORD = "pref_api_password";
    public static final String PREF_REMEMBER_PASSWORD = "pref_remember_password";
    public static final String PREF_CAMERA_RESOLUTION = "pref_camera_resolution";

    public static final String PREF_FLASH_MODE = "pref_flash_mode";
    public static final String PREF_FLASH_MODE_ON = "On";
    public static final String PREF_FLASH_MODE_AUTO = "Auto";
    public static final String PREF_FLASH_MODE_OFF = "Off";

    public static final String PREF_LIMIT_OFFLINE_PHOTOS = "pref_limit_offline_photos";
    public static final String PREF_OFFLINE_PHOTO_LIMIT = "pref_offline_photo_limit";

    /**
     * Points to bool preference, true if single-tag mode is enabled.
     */
    public static final String PREF_SINGLE_TAG_SCAN = "PREF_SCAN_SINGLE_TAG";

    /**
     * Points to bool preference, true if a tag must be set to capture a photo.
     */
    public static final String PREF_REQUIRE_TAG = "PREF_REQUIRE_TAG";

    /**
     * Working together, clear all tags if no tags have been updated after a
     * certain amount of time (secs)
     */
    public static final String PREF_ENABLE_CLEAR_TAGS = "PREF_ENABLE_CLEAR_TAGS";
    public static final String PREF_CLEAR_TAGS_TIMEOUT = "PREF_CLEAR_TAGS_TIMEOUT";

    /**
     * Points to string preference indicating single-tag mode. See SINGLE_TAG_SCANNED_ONLY
     * and SINGLE_TAG_ALL_TAGS.
     */
    public static final String PREF_SINGLE_TAG_MODE = "PREF_SINGLE_TAG_MODE";

    /**
     * Value for PREF_SINGLE_TAG_MODE indicating that single-tag mode should only delete
     * previous scans.
     */
    public static final String SINGLE_TAG_SCANNED_ONLY = "SINGLE_TAG_SCANNED_ONLY";

    /**
     * Value for PREF_SINGLE_TAG_MODE indicating that single-tag mode should delete all
     * other tags.
     */
    public static final String SINGLE_TAG_ALL_TAGS = "SINGLE_TAG_ALL_TAGS";
}
