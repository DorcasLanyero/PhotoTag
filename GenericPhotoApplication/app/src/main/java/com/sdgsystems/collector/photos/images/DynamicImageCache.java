package com.sdgsystems.collector.photos.images;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

import com.android.volley.toolbox.ImageLoader;

/**
 * Created by bfriedberg on 8/8/17.
 */

public class DynamicImageCache implements ImageLoader.ImageCache{

    private final LruCache<String, Bitmap>
            cache = new LruCache<String, Bitmap>(200);

    @Override
    public Bitmap getBitmap(String key) {
        if (key.contains("file://")) {
            return BitmapFactory.decodeFile(key.substring(key.indexOf("file://") + 7));
        } else {
            // Here you can add an actual cache
            return cache.get(key);
        }
    }
    @Override
    public void putBitmap(String key, Bitmap bitmap) {
        // Here you can add an actual cache
        cache.put(key, bitmap);
    }
}
