package com.sdgsystems.collector.photos.sync.authorizedVolleyRequests;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonArrayRequest;
import com.sdgsystems.collector.photos.Constants;
import com.sdgsystems.collector.photos.GenericPhotoApplication;

import org.json.JSONArray;

import java.util.Map;

/**
 * Created by bfriedberg on 8/4/17.
 */

public class AuthorizedJsonArrayRequest extends JsonArrayRequest {

    public AuthorizedJsonArrayRequest(String url, Response.Listener<JSONArray> listener, Response.ErrorListener errorListener) {
        super(url, listener, errorListener);

        setRetryPolicy(new DefaultRetryPolicy(
                Constants.RETRY_DEFAULT_TIMEOUT_MS,
                Constants.RETRY_DEFAULT_MAX_RETRIES,
                Constants.RETRY_DEFAULT_BACKOFF_MULT));
    }

    @Override
    public Map<String, String> getHeaders() throws AuthFailureError {
        return GenericPhotoApplication.getInstance().getAuthHeaders();
    }
}
