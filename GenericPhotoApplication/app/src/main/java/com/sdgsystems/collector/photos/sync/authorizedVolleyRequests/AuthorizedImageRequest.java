package com.sdgsystems.collector.photos.sync.authorizedVolleyRequests;

import android.graphics.Bitmap;
import android.widget.ImageView;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Response;
import com.android.volley.toolbox.ImageRequest;
import com.sdgsystems.collector.photos.GenericPhotoApplication;

import java.util.Map;

import static com.sdgsystems.collector.photos.Constants.RETRY_DEFAULT_BACKOFF_MULT;
import static com.sdgsystems.collector.photos.Constants.RETRY_DEFAULT_MAX_RETRIES;
import static com.sdgsystems.collector.photos.Constants.RETRY_DEFAULT_TIMEOUT_MS;

/**
 * Created by bfriedberg on 8/4/17.
 */

public class AuthorizedImageRequest extends ImageRequest {
    public AuthorizedImageRequest(String url, Response.Listener<Bitmap> listener, int maxWidth, int maxHeight, ImageView.ScaleType scaleType, Bitmap.Config decodeConfig, Response.ErrorListener errorListener) {
        super(url, listener, maxWidth, maxHeight, scaleType, decodeConfig, errorListener);

        setRetryPolicy(new DefaultRetryPolicy(
                RETRY_DEFAULT_TIMEOUT_MS,
                RETRY_DEFAULT_MAX_RETRIES,
                RETRY_DEFAULT_BACKOFF_MULT));
    }

    @Override
    public Map<String, String> getHeaders() throws AuthFailureError {
        Map<String, String> headers = GenericPhotoApplication.getInstance().getAuthHeaders();

        headers.put("Content-Type", "application/octet-stream");

        return headers;
    }
}
