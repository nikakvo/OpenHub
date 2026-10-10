package com.thirtydegreesray.openhub.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.thirtydegreesray.openhub.common.AppEventBus;
import com.thirtydegreesray.openhub.common.Event;

/**
 * Tracks whether the device has a usable network.
 *
 * Works with every transport (Wi-Fi, mobile, roaming, VPN, Ethernet, Bluetooth tether).
 * The old version only recognised TYPE_WIFI and TYPE_MOBILE, so with a VPN active the
 * status was never updated and the app could believe it was offline.
 *
 * TYPE_WIFI means an unmetered network and TYPE_MOBILE a metered one. Callers only use
 * this for "is it unmetered" decisions (image loading) and "is there any network".
 */
public enum NetHelper {
	INSTANCE;

	private static final String TAG = "NetHelper";

	public static final int TYPE_DISCONNECT = 0;
	/** Connected, unmetered (Wi-Fi, Ethernet, unmetered VPN). */
	public static final int TYPE_WIFI = 1;
	/** Connected, metered (mobile data, roaming, metered hotspot or VPN over mobile). */
	public static final int TYPE_MOBILE = 2;

	private volatile int mCurNetStatus = TYPE_DISCONNECT;
	private Context mContext;
	private boolean mCallbackRegistered;

	public void init(@NonNull Context context) {
		mContext = context.getApplicationContext();
		checkNet();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			registerCallback();
		}
	}

	/** Re-reads the current network state. */
	public void checkNet() {
		setStatus(readStatus());
	}

	private int readStatus() {
		try {
			ConnectivityManager cm = connectivity();
			if (cm == null) return TYPE_DISCONNECT;
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				Network network = cm.getActiveNetwork();
				return statusOf(network == null ? null : cm.getNetworkCapabilities(network));
			}
			return legacyStatus(cm);
		} catch (Exception e) {
			Log.w(TAG, "checkNet failed", e);
			// Never report "offline" because of an error, it would block requests.
			return mCurNetStatus == TYPE_DISCONNECT ? TYPE_MOBILE : mCurNetStatus;
		}
	}

	private static int statusOf(@Nullable NetworkCapabilities caps) {
		if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
			return TYPE_DISCONNECT;
		}
		return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
				? TYPE_WIFI : TYPE_MOBILE;
	}

	@SuppressWarnings("deprecation")
	private static int legacyStatus(@NonNull ConnectivityManager cm) {
		NetworkInfo info = cm.getActiveNetworkInfo();
		if (info == null || !info.isConnected()) return TYPE_DISCONNECT;
		return cm.isActiveNetworkMetered() ? TYPE_MOBILE : TYPE_WIFI;
	}

	@RequiresApi(Build.VERSION_CODES.N)
	private void registerCallback() {
		if (mCallbackRegistered) return;
		ConnectivityManager cm = connectivity();
		if (cm == null) return;
		try {
			cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
				@Override
				public void onAvailable(@NonNull Network network) {
					checkNet();
				}

				@Override
				public void onCapabilitiesChanged(@NonNull Network network,
												  @NonNull NetworkCapabilities caps) {
					setStatus(statusOf(caps));
				}

				@Override
				public void onLost(@NonNull Network network) {
					checkNet();
				}
			});
			mCallbackRegistered = true;
		} catch (Exception e) {
			Log.w(TAG, "registerDefaultNetworkCallback failed", e);
		}
	}

	/** True when the legacy CONNECTIVITY_ACTION broadcast is still needed (below Android 7). */
	public boolean needsBroadcastReceiver() {
		return !mCallbackRegistered;
	}

	private void setStatus(int status) {
		int previous = mCurNetStatus;
		mCurNetStatus = status;
		if (previous != status) {
			AppEventBus.INSTANCE.getEventBus().post(new Event.NetChangedEvent(previous, status));
		}
	}

	@Nullable
	private ConnectivityManager connectivity() {
		return mContext == null ? null
				: (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
	}

	/** Any usable network. */
	@NonNull
	public Boolean getNetEnabled() {
		return mCurNetStatus != TYPE_DISCONNECT;
	}

	/** Metered network (mobile data, roaming...). */
	@NonNull
	public Boolean isMobileStatus() {
		return mCurNetStatus == TYPE_MOBILE;
	}

	public int getNetStatus() {
		return mCurNetStatus;
	}
}
