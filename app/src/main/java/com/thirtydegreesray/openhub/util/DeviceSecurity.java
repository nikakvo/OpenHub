package com.thirtydegreesray.openhub.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.thirtydegreesray.openhub.BuildConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * What About - Device security shows: root, hooking frameworks in OpenHub, certificates the
 * user added, and whether the connection to GitHub is GitHub's own. Information only: the
 * app never blocks or changes anything because of it (the user's phone, the user's choice).
 * Only real findings: "not found" never claims a phone is clean (root can be hidden).
 */
public final class DeviceSecurity {

    public enum Connection {
        /** GitHub's own certificates. */
        VERIFIED,
        /** A proxy or network tried, Android refused the connection. */
        REFUSED_BY_ANDROID,
        /** Android's checks are switched off in OpenHub, the extra check stopped it. */
        INTERCEPTED,
        /** No connection right now. */
        NOT_CHECKED,
        /** Debug build or the extra check's end date passed: Android's check only. */
        CHECK_OFF
    }

    public static final class Report {
        /** Root managers or su files found (names to show). */
        @NonNull public final List<String> root = new ArrayList<>();
        /** Hooking frameworks found inside OpenHub's own process. */
        @NonNull public final List<String> hooks = new ArrayList<>();
        /** Certificates the user installed on the phone (OpenHub does not trust them). */
        public int userCertificates;
        @NonNull public Connection connection = Connection.NOT_CHECKED;
        /** The host the connection test used. */
        @NonNull public final String host = "api.github.com";
    }

    private static final String[] SU_PATHS = {
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/sbin/su", "/vendor/bin/su",
            "/debug_ramdisk/su", "/data/local/su", "/data/local/bin/su", "/data/local/xbin/su"
    };

    /** Package name and the name shown for it. */
    private static final String[][] MANAGERS = {
            {"com.topjohnwu.magisk", "Magisk"},
            {"io.github.huskydg.magisk", "Kitsune Mask"},
            {"io.github.vvb2060.magisk", "Magisk Alpha"},
            {"me.weishu.kernelsu", "KernelSU"},
            {"com.rifsxd.ksunext", "KernelSU Next"},
            {"com.sukisu.ultra", "SukiSU Ultra"},
            {"me.bmax.apatch", "APatch"},
            {"org.lsposed.manager", "LSPosed manager"},
    };

    private DeviceSecurity() {
    }

    /** All checks; the connection test goes over the network (no sign-in is sent). */
    @WorkerThread
    @NonNull
    public static Report check(@NonNull Context context) {
        Report report = new Report();
        findRoot(context.getApplicationContext(), report.root);
        findHooks(report.hooks);
        report.userCertificates = userCertificates();
        report.connection = testConnection(report.host);
        return report;
    }

    static void findRoot(@NonNull Context context, @NonNull List<String> found) {
        PackageManager pm = context.getPackageManager();
        for (String[] manager : MANAGERS) {
            if ("org.lsposed.manager".equals(manager[0])) continue;
            try {
                pm.getPackageInfo(manager[0], 0);
                found.add(manager[1]);
            } catch (Exception ignored) {
                // not installed (or hidden)
            }
        }
        for (String path : SU_PATHS) {
            if (new File(path).exists()) {
                found.add("su (" + path + ")");
                break;
            }
        }
        if (Build.TAGS != null && Build.TAGS.contains("test-keys")) found.add("test-keys build");
    }

    static void findHooks(@NonNull List<String> found) {
        Set<String> names = new LinkedHashSet<>();
        // libraries and files mapped into OpenHub's own process
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/maps"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String l = line.toLowerCase(Locale.ROOT);
                if (l.contains("lspd") || l.contains("lsposed") || l.contains("lspatch")) names.add("LSPosed");
                else if (l.contains("xposed") || l.contains("edxp")) names.add("Xposed");
                if (l.contains("frida") || l.contains("gum-js") || l.contains("linjector")) names.add("Frida");
                if (l.contains("zygisk")) names.add("Zygisk");
                if (l.contains("riru")) names.add("Riru");
            }
        } catch (IOException ignored) {
            // unreadable: nothing found
        }
        // Xposed's bridge class visible to the app
        for (ClassLoader loader : new ClassLoader[]{ClassLoader.getSystemClassLoader(),
                DeviceSecurity.class.getClassLoader()}) {
            try {
                Class.forName("de.robv.android.xposed.XposedBridge", false, loader);
                names.add("Xposed API");
                break;
            } catch (Throwable ignored) {
                // not there
            }
        }
        for (StackTraceElement element : new Throwable().getStackTrace()) {
            String c = element.getClassName().toLowerCase(Locale.ROOT);
            if (c.contains("xposed") || c.contains("lsposed") || c.contains("lsphooker")) {
                names.add("Xposed API");
                break;
            }
        }
        // Frida's threads and its default port
        File[] tasks = new File("/proc/self/task").listFiles();
        if (tasks != null) {
            for (File task : tasks) {
                String comm = readFirstLine(new File(task, "comm"));
                if (comm != null && (comm.contains("gum-js") || comm.contains("frida"))) {
                    names.add("Frida");
                    break;
                }
            }
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 27042), 150);
            names.add("Frida server (port 27042)");
        } catch (IOException ignored) {
            // nothing listens there
        }
        found.addAll(names);
    }

    static int userCertificates() {
        try {
            KeyStore store = KeyStore.getInstance("AndroidCAStore");
            store.load(null, null);
            int count = 0;
            Enumeration<String> aliases = store.aliases();
            while (aliases.hasMoreElements()) {
                if (aliases.nextElement().startsWith("user:")) count++;
            }
            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * A request to GitHub without the sign-in: which check, if any, stopped a connection
     * that is not GitHub's own.
     */
    @NonNull
    static Connection testConnection(@NonNull String host) {
        if (!TlsGuard.isActive()) return Connection.CHECK_OFF;
        final TlsChainCheck.Result[] seen = new TlsChainCheck.Result[1];
        OkHttpClient client = new OkHttpClient.Builder()
                .addNetworkInterceptor(chain -> {
                    seen[0] = TlsGuard.INSTANCE.check(chain.connection(), host);
                    if (seen[0] != TlsChainCheck.Result.OK) {
                        throw new IOException("intercepted");
                    }
                    return chain.proceed(chain.request());
                })
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
        Request request = new Request.Builder().url("https://" + host + "/").head().build();
        try (Response ignored = client.newCall(request).execute()) {
            return Connection.VERIFIED;
        } catch (SSLException e) {
            return Connection.REFUSED_BY_ANDROID;
        } catch (IOException e) {
            if (seen[0] != null && seen[0] != TlsChainCheck.Result.OK) return Connection.INTERCEPTED;
            return Connection.NOT_CHECKED;
        } finally {
            client.connectionPool().evictAll();
        }
    }

    @Nullable
    private static String readFirstLine(@NonNull File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine();
            return line == null ? null : line.toLowerCase(Locale.ROOT);
        } catch (IOException e) {
            return null;
        }
    }

    /** True in debug builds (the extra check is off there). */
    public static boolean isDebugBuild() {
        return BuildConfig.DEBUG;
    }
}
