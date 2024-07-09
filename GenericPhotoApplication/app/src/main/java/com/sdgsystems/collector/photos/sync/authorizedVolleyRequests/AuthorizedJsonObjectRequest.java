package com.sdgsystems.collector.photos.sync.authorizedVolleyRequests;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonObjectRequest;
import com.sdgsystems.collector.photos.GenericPhotoApplication;

import org.json.JSONObject;

import java.util.Map;

import static com.sdgsystems.collector.photos.Constants.*;

/**
 * Created by bfriedberg on 8/4/17.
 */

public class AuthorizedJsonObjectRequest extends JsonObjectRequest {
    public AuthorizedJsonObjectRequest(int method, String url, JSONObject jsonRequest, Response.Listener<JSONObject> listener, Response.ErrorListener errorListener) {
        super(method, url, jsonRequest, listener, errorListener);

        setRetryPolicy(new DefaultRetryPolicy(
                RETRY_DEFAULT_TIMEOUT_MS,
                RETRY_DEFAULT_MAX_RETRIES,
                RETRY_DEFAULT_BACKOFF_MULT));
    }

    public AuthorizedJsonObjectRequest(String s, JSONObject o, Response.Listener<JSONObject> listener, Response.ErrorListener errorListener) {
        super(s, o, listener, errorListener);

        setRetryPolicy(new DefaultRetryPolicy(
                RETRY_DEFAULT_TIMEOUT_MS,
                RETRY_DEFAULT_MAX_RETRIES,
                RETRY_DEFAULT_BACKOFF_MULT));
    }

    @Override
    public Map<String, String> getHeaders() throws AuthFailureError {
        return GenericPhotoApplication.getInstance().getAuthHeaders();
    }
}
