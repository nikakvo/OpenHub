

package com.thirtydegreesray.openhub.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import androidx.annotation.NonNull;

import com.thirtydegreesray.openhub.util.NetHelper;

/**
 * Created by ThirtyDegreesRay on 2016/8/25 16:07
 */

public class NetBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, @NonNull Intent intent) {
        String action = intent.getAction();
        if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
            // NetHelper posts NetChangedEvent itself when the status changes.
            NetHelper.INSTANCE.checkNet();
        }
    }

}
