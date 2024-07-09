package com.sdgsystems.collector.photos.images;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.widget.ImageView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.sdgsystems.blueloggerclient.SDGLog;
import com.sdgsystems.collector.photos.sync.authorizedVolleyRequests.AuthorizedImageRequest;

/**
 * Created by bfriedberg on 8/4/17.
 */

public class CustomImageLoader extends ImageLoader{
    private static final String TAG = "CustomImageLoader";
    private Context mContext;

    /**
     * Constructs a new ImageLoader.
     *
     * @param queue      The RequestQueue to use for making image requests.
     * @param imageCache The cache to use as an L1 cache.
     */
    public CustomImageLoader(Context aContext, RequestQueue queue, ImageCache imageCache) {
        super(queue, imageCache);
        mContext = aContext;

        SDGLog.d(TAG, "New CustomImageLoader");
    }


    @Override
    protected Request<Bitmap> makeImageRequest(String requestUrl, final int maxWidth, final int maxHeight,
                                               final ImageView.ScaleType scaleType, final String cacheKey) {

        return new AuthorizedImageRequest(requestUrl, new Response.Listener<Bitmap>() {
            @Override
            public void onResponse(Bitmap response) {
                onGetImageSuccess(cacheKey, response);

                SDGLog.d(TAG, "Got image: " + scaleType + " " + maxWidth + " x " + maxHeight);
            }
        }, maxWidth, maxHeight, scaleType, Bitmap.Config.RGB_565, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                onGetImageError(cacheKey, error);

                SDGLog.d(TAG, "Got error: " + error.getMessage());
            }
        });

    }
}
