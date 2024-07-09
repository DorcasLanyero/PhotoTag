package com.sdgsystems.collector.photos.sync;

import android.content.Context;
import android.preference.PreferenceManager;
import android.util.Log;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.RetryPolicy;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.JsonObjectRequest;
import com.sdgsystems.blueloggerclient.SDGLog;
import com.sdgsystems.collector.photos.BuildConfig;
import com.sdgsystems.collector.photos.Constants;
import com.sdgsystems.collector.photos.GenericPhotoApplication;
import com.sdgsystems.collector.photos.R;

import org.json.JSONObject;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

/**
 * Created by pknight on 4/22/17.
 */

public class NetworkRequestHandler {

    public static final String TAG = "NetworkRequestHandler";


    public static JSONObject mLoginResponse = null;

    public interface ILoginRequestCallback {
        void loginSucceeded();
        void loginFailed(int statusCode, String errorMsg);
    }

    // This is to support the default login for the beta version.
    public static boolean isLoggedIn() {
        return GenericPhotoApplication.getInstance().getBearerToken() != null || BuildConfig.FLAVOR.contains("localNoAuth");
    }

    public static void logOut() {
        GenericPhotoApplication.getInstance().setBearerToken(null);
    }

    public static void LoginRequestWithCallback(final ILoginRequestCallback callback, Context context) {

        final String username = PreferenceManager.getDefaultSharedPreferences(context).getString(Constants.PREF_USERNAME, "");
        final String password = PreferenceManager.getDefaultSharedPreferences(context).getString(Constants.PREF_PASSWORD, "");

        try {
            LoginRequestWithCallback(username, password, callback);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static class LoginJsonObjectRequest extends JsonObjectRequest {

        public LoginJsonObjectRequest(int method, String url, JSONObject jsonRequest, Response.Listener
                <JSONObject> listener, Response.ErrorListener errorListener) {
            super(method, url, jsonRequest, listener, errorListener);
        }

        public LoginJsonObjectRequest(String url, JSONObject jsonRequest, Response.Listener<JSONObject>
                listener, Response.ErrorListener errorListener) {
            super(url, jsonRequest, listener, errorListener);
        }

        @Override
        protected Response<JSONObject> parseNetworkResponse(NetworkResponse response) {
            GenericPhotoApplication.getInstance().setBearerToken(new String(response.data));
            return Response.success(new JSONObject(),
                    HttpHeaderParser.parseCacheHeaders(response));
        }
    };

    public static void LoginRequestWithCallback(String userName, String password, final ILoginRequestCallback callback) throws NoSuchAlgorithmException, KeyManagementException, IOException {
        // Tag used to cancel the request
        String tag_json_obj = "LoginRequestWithCallback";

        HashMap<String, String> login = new HashMap<>();
        login.put("username", userName);
        login.put("password", password);

        JSONObject jsonObject =  new JSONObject(login);

        String url = GenericPhotoApplication.getInstance().getAuthUrl() + "login";
        SDGLog.d(TAG, "logging into url: " + url);

        NetworkRequestHandler.LoginJsonObjectRequest loginJsonObjectReq = new NetworkRequestHandler.LoginJsonObjectRequest(Request.Method.POST, url , jsonObject,
                new Response.Listener<JSONObject>() {

                    @Override
                    public void onResponse(JSONObject response) {
                        SDGLog.d(TAG, response.toString());
                        String jsonString = response.toString();
                        SDGLog.d(TAG, "Login response: " + jsonString);

                        mLoginResponse = response;

                        callback.loginSucceeded();
                    }
                }, new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(VolleyError error) {
                        if(error.networkResponse == null) {
                            SDGLog.d(TAG, "Login error: " + error.getMessage());
                            callback.loginFailed(handleStatusCode(error), error.getMessage());
                        } else {
                            SDGLog.d(TAG, "Login error: " + handleErrorResponse(error.networkResponse));
                            callback.loginFailed(handleStatusCode(error), handleErrorResponse(error.networkResponse));
                        }
                    }
                });

        loginJsonObjectReq.setRetryPolicy(new RetryPolicy() {
            @Override
            public int getCurrentTimeout() {
                return 0;
            }

            @Override
            public int getCurrentRetryCount() {
                return 0;
            }

            // Zero retries
            @Override
            public void retry(VolleyError error) throws VolleyError {
                throw error;
            }
        });

        GenericPhotoApplication.getInstance().addToRequestQueue(loginJsonObjectReq, tag_json_obj);
    }

    private static String handleErrorResponse (NetworkResponse response) {
        if (response == null) return "Unknown error";
        return new String(response.data).trim();
    }

    private static int handleStatusCode (final VolleyError error) {
        if (error.networkResponse != null) {
            return error.networkResponse.statusCode;
        }
        else {
            return -1;
        }
    }
}
