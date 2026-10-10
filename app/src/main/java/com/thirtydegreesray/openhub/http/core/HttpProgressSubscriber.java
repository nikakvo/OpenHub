

package com.thirtydegreesray.openhub.http.core;

import android.app.AlertDialog;
import androidx.annotation.NonNull;

/**
 * 网络请求开始时显示dialog，结束取消dialog
 * Created by ThirtyDegreesRay on 2016/7/15 11:24
 */
public class HttpProgressSubscriber<T> extends HttpSubscriber<T> {

    /**
     * 网络请求dialog
     */
    private AlertDialog mDialog;

    public HttpProgressSubscriber(@NonNull AlertDialog dialog, @NonNull HttpObserver<T> observer) {
        super(observer);
        mDialog = dialog;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!isDisposed())
            mDialog.show();
    }

    @Override
    public void onComplete() {
        super.onComplete();
        mDialog.dismiss();
    }

    @Override
    public void onError(Throwable e) {
        super.onError(e);
        mDialog.dismiss();
    }
}
