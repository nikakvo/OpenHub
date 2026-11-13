package com.thirtydegreesray.openhub.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
		registerCallback();
	}

	/** Re-reads the current network state. */
	public void checkNet() {
		setStatus(readStatus());
	}

	private int readStatus() {
		try {
			ConnectivityManager cm = connectivity();
			if (cm == null) return TYPE_DISCONNECT;
			Network network = cm.getActiveNetwork();
			return statusOf(network == null ? null : cm.getNetworkCapabilities(network));
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

	/**
	 * Without the network callback (the system refused it) nothing reports changes:
	 * read the state again on every query and try to register once more.
	 */
	private void refreshIfNoCallback() {
		if (mCallbackRegistered || mContext == null) return;
		checkNet();
		long now = android.os.SystemClock.elapsedRealtime();
		if (now - mLastRegisterTry < 30_000L) return;
		mLastRegisterTry = now;
		registerCallback();
	}

	private volatile long mLastRegisterTry;

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
		refreshIfNoCallback();
		return mCurNetStatus != TYPE_DISCONNECT;
	}

	/** Metered network (mobile data, roaming...). */
	@NonNull
	public Boolean isMobileStatus() {
		refreshIfNoCallback();
		return mCurNetStatus == TYPE_MOBILE;
	}

	public int getNetStatus() {
		refreshIfNoCallback();
		return mCurNetStatus;
	}
}
