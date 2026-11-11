

package com.thirtydegreesray.openhub;

import com.thirtydegreesray.openhub.util.ReleaseWatcher;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;

import android.app.Application;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Build;

import com.thirtydegreesray.openhub.util.AppLog;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.inject.component.DaggerAppComponent;
import com.thirtydegreesray.openhub.inject.module.AppModule;
import com.thirtydegreesray.openhub.service.NetBroadcastReceiver;
import com.thirtydegreesray.openhub.util.AppUtils;
import com.thirtydegreesray.openhub.util.NetHelper;
import com.thirtydegreesray.openhub.util.SignInWatch;

/**
 * AppApplication
 * Created by ThirtyDegreesRay on 2016/7/13 14:01
 */
public class AppApplication extends Application {

    private final String TAG = AppApplication.class.getSimpleName();

    private static AppApplication application;
    private AppComponent mAppComponent;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
//        MultiDex.install(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        application = this;
        //init application
        long startTime = System.currentTimeMillis();
        AppData.INSTANCE.getSystemDefaultLocal();
        //apply language for application context
        AppUtils.updateAppLanguage(getApplicationContext());
        AppLog.i(TAG, "startTime:" + startTime);
        mAppComponent = DaggerAppComponent.builder()
                .appModule(new AppModule(this))
                .build();
        initNetwork();
        // "Sign in again" on the screen in front when GitHub stops accepting the account
        SignInWatch.install(this);
        // RxJava 3 sends errors that arrive after a request was cancelled (screen closed)
        // to this handler; without one they would crash the app.
        RxJavaPlugins.setErrorHandler(error -> AppLog.w(TAG, "Rx error after dispose: " + error));
        // keep the release check scheduled (no-op when nothing is watched)
        ReleaseWatcher.schedule(this);
        com.thirtydegreesray.openhub.util.GitHubNotifier.schedule(this);
        com.thirtydegreesray.openhub.util.ActionsWatcher.schedule(this);
        startTime = System.currentTimeMillis();
        AppLog.i(TAG, "application ok:" + (System.currentTimeMillis() - startTime));


    }

    private void initNetwork(){
        // Android 7+ uses a default-network callback inside NetHelper. The broadcast is
        // only a fallback for older versions.
        NetHelper.INSTANCE.init(this);
        if (NetHelper.INSTANCE.needsBroadcastReceiver()) {
            registerReceiver(new NetBroadcastReceiver(),
                    new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        }
    }

    public static AppApplication get(){
        return application;
    }

    public AppComponent getAppComponent(){
        return mAppComponent;
    }


}
