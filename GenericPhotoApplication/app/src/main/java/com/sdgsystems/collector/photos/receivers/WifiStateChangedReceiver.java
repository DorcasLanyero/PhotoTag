package com.sdgsystems.collector.photos.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;

import com.sdgsystems.collector.photos.Constants;

public class WifiStateChangedReceiver extends BroadcastReceiver {


    boolean firstMessageShown = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if(action.equals(ConnectivityManager.CONNECTIVITY_ACTION)){
            NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
            if(info.getType() == ConnectivityManager.TYPE_WIFI){

                if(!firstMessageShown) {
                    firstMessageShown = true;
                } else {

                    if (info.isConnected()) {
                        Intent i = new Intent(Constants.ACTION_WIFI_CONNECTED);
                        i.setPackage(context.getPackageName());
                        context.sendBroadcast(i);
                    } else {
                        Intent i = new Intent(Constants.ACTION_WIFI_DISCONNECTED);
                        i.setPackage(context.getPackageName());
                        context.sendBroadcast(i);
                    }
                }
            }

        }
    }
}
