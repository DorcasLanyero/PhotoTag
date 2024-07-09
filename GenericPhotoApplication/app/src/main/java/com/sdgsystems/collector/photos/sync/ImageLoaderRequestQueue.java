package com.sdgsystems.collector.photos.sync;

import android.content.Context;
import android.util.Log;

import com.android.volley.Cache;
import com.android.volley.Network;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.ImageLoader;
import com.sdgsystems.blueloggerclient.SDGLog;
import com.sdgsystems.collector.photos.images.CustomImageLoader;
import com.sdgsystems.collector.photos.images.DynamicImageCache;

/**
 * Custom implementation of Volley Request Queue
 */
public class ImageLoaderRequestQueue {

    private static final String TAG = "Imageloaderrequestqueue";
    private static ImageLoaderRequestQueue mInstance;
    private RequestQueue mRequestQueue;
    private ImageLoader mImageLoader;

    private ImageLoaderRequestQueue(Context context) {
        Cache cache = new DiskBasedCache(context.getCacheDir(), 10 * 1024 * 1024);
        Network network = new BasicNetwork(new HurlStack());
        mRequestQueue = new RequestQueue(cache, network);
        // Don't forget to start the volley request queue
        mRequestQueue.start();

        SDGLog.d(TAG, "Creating new custom image loader");

        mImageLoader = new CustomImageLoader(context, mRequestQueue, new DynamicImageCache());
    }

    public static synchronized ImageLoaderRequestQueue getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new ImageLoaderRequestQueue(context);
        }
        return mInstance;
    }

    public RequestQueue getRequestQueue() {
        return mRequestQueue;
    }

    public ImageLoader getImageLoader() {
        return mImageLoader;
    }

}