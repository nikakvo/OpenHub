package com.thirtydegreesray.openhub.util;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.thirtydegreesray.openhub.AppApplication;
import com.thirtydegreesray.openhub.AppConfig;
import com.thirtydegreesray.openhub.AppData;
import com.thirtydegreesray.openhub.BuildConfig;
import com.thirtydegreesray.openhub.dao.AuthUser;
import com.thirtydegreesray.openhub.dao.AuthUserDao;
import com.thirtydegreesray.openhub.mvp.model.OauthToken;
import com.thirtydegreesray.openhub.util.TokenRefreshRules.Outcome;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Keeps expiring browser sign-ins working. GitHub gives such a sign-in an access token for
 * 8 hours and a refresh token for about 6 months; every refresh returns a NEW pair and the
 * old refresh token stops working at once. So:
 * <ul>
 *   <li>only one refresh runs at a time, and inside the lock the stored row is read again:
 *   a caller that waited finds the new token and uses it instead of burning the refresh
 *   token a second time;</li>
 *   <li>the new pair is written to the database before it is used;</li>
 *   <li>no network, a server error or any unclear answer changes nothing (the old pair
 *   stays, the next request tries again after a short pause);</li>
 *   <li>only GitHub's explicit "bad_refresh_token" means "sign in again", and even then the
 *   account is kept.</li>
 * </ul>
 * Tokens that do not expire (personal access tokens, sign-ins from before 3.11.0) are never
 * touched. Requests made with an older token of an account are sent with its newest one
 * ({@link #current}), so screens and workers that kept a token keep working.
 */
public final class TokenRefresher {

    private static final String TAG = "TokenRefresher";

    private static final Object LOCK = new Object();

    /** Old access token -> the one that replaced it (in memory). */
    private static final Map<String, String> NEWER = new ConcurrentHashMap<>();
    /** Access token -> what is known about its account. */
    private static final Map<String, Account> KNOWN = new ConcurrentHashMap<>();
    /** Logins whose refresh token GitHub refused; kept on the phone (SignInNotice). */
    private static final Set<String> REFUSED = ConcurrentHashMap.newKeySet();
    private static volatile boolean refusedLoaded;
    /** Login -> time of the last temporary failure. */
    private static final Map<String, Long> FAILED_AT = new ConcurrentHashMap<>();

    /** An unknown token is looked up in the database again after this time. */
    private static final long UNKNOWN_TTL_MS = 60 * 1000L;

    private static volatile OkHttpClient client;

    private TokenRefresher() {
    }

    /** What the refresher knows about the account of one access token. */
    private static final class Account {
        @Nullable final String login;
        @Nullable final Long accessExpiresAt;
        final boolean canRefresh;
        final long loadedAt;

        Account(@Nullable String login, @Nullable Long accessExpiresAt, boolean canRefresh) {
            this.login = login;
            this.accessExpiresAt = accessExpiresAt;
            this.canRefresh = canRefresh;
            this.loadedAt = System.currentTimeMillis();
        }

        static Account of(@NonNull AuthUser user) {
            return new Account(user.getLoginId(), user.getAccessExpiresAt(), user.canRefresh());
        }
    }

    /** The newest token of the account that once had {@code token}. */
    @NonNull
    public static String current(@NonNull String token) {
        String result = token;
        for (int i = 0; i < 20; i++) {
            String next = NEWER.get(result);
            if (next == null) break;
            result = next;
        }
        return result;
    }

    /**
     * The token to send now: the newest one, refreshed first when it ends within 5 minutes.
     * Never fails; on any problem the token comes back as it is.
     */
    @NonNull
    public static String freshToken(@NonNull String token) {
        String now = current(token);
        Account account = account(now);
        if (account == null || !account.canRefresh || account.login == null) return now;
        if (!TokenRefreshRules.refreshSoon(account.accessExpiresAt, System.currentTimeMillis())) {
            return now;
        }
        String refreshed = refresh(now, account.login, false);
        return refreshed == null ? now : refreshed;
    }

    /**
     * GitHub answered 401 to {@code token}: refreshes once. Returns the token to send the
     * request again with, or null when there is none (the request fails as it is).
     */
    @Nullable
    public static String renewAfter401(@NonNull String token) {
        String now = current(token);
        if (!now.equals(token)) return now; // already replaced meanwhile
        Account account = account(now);
        if (account == null || !account.canRefresh || account.login == null) return null;
        return refresh(now, account.login, true);
    }

    /**
     * True when a 401 for {@code token} really means "sign in again": the token cannot be
     * refreshed (personal access token, older sign-in) or GitHub refused its refresh token.
     * A 401 while a refresh only failed for a temporary reason does not count.
     */
    public static boolean needsSignIn(@Nullable String token) {
        if (StringUtils.isBlank(token)) return false;
        Account account = account(current(token));
        if (account == null || !account.canRefresh) return true;
        return account.login != null && refused().contains(account.login);
    }

    /**
     * True when GitHub refused the refresh token of this login. The mark survives restarts;
     * only a new sign-in of the login removes it.
     */
    public static boolean isRefused(@Nullable String login) {
        return login != null && refused().contains(login);
    }

    /** After a sign-in (login) or log out (null): forget what was read from the database. */
    public static void accountsChanged(@Nullable String login) {
        KNOWN.clear();
        if (login != null) {
            FAILED_AT.remove(login);
            if (refused().remove(login)) saveRefused();
            try {
                SignInNotice.clear(AppApplication.get(), login);
            } catch (Exception ignored) {
                // no notification to remove
            }
        }
    }

    /** The marks, read from the phone the first time they are needed. */
    @NonNull
    private static Set<String> refused() {
        if (!refusedLoaded) {
            synchronized (REFUSED) {
                if (!refusedLoaded) {
                    try {
                        REFUSED.addAll(SignInNotice.load(AppApplication.get()));
                    } catch (Exception ignored) {
                        // no stored marks
                    }
                    refusedLoaded = true;
                }
            }
        }
        return REFUSED;
    }

    private static void saveRefused() {
        try {
            SignInNotice.save(AppApplication.get(), REFUSED);
        } catch (Exception ignored) {
            // stays in memory
        }
    }

    // ---- the refresh itself ----

    /**
     * Refreshes the account {@code login} whose newest known token is {@code token}.
     * {@code after401}: the request already failed, so a token that is not near its end is
     * refreshed too. Returns the new token, the token another caller already got, or null.
     */
    @Nullable
    private static String refresh(@NonNull String token, @NonNull String login, boolean after401) {
        synchronized (LOCK) {
            AuthUserDao dao;
            AuthUser stored;
            try {
                dao = AppApplication.get().getAppComponent().getDatabase().authUserDao();
                stored = findByLogin(dao, login);
            } catch (Exception e) {
                return null;
            }
            if (stored == null) return null; // logged out meanwhile
            String storedToken = stored.getAccessToken();
            if (!StringUtils.isBlank(storedToken) && !storedToken.equals(token)) {
                // another request refreshed while this one waited: use its result
                remember(token, stored);
                return storedToken;
            }
            if (!stored.canRefresh() || refused().contains(login)) return null;
            long nowMs = System.currentTimeMillis();
            if (!after401 && !TokenRefreshRules.refreshSoon(stored.getAccessExpiresAt(), nowMs)) {
                return null;
            }
            if (!TokenRefreshRules.mayTry(FAILED_AT.get(login), nowMs)) return null;

            Answer answer = ask(stored.getRefreshToken());
            Outcome outcome = TokenRefreshRules.outcome(answer.code,
                    answer.token == null ? null : answer.token.getAccessToken(),
                    answer.token == null ? null : answer.token.getError());
            if (outcome == Outcome.TEMPORARY) {
                FAILED_AT.put(login, nowMs);
                log("refresh failed for now (" + answer.code + ")");
                return null;
            }
            if (outcome == Outcome.REFUSED) {
                // kept on the phone: background checks stop using this sign-in; the
                // account itself stays until the user signs in again or logs out
                if (refused().add(login)) {
                    saveRefused();
                    try {
                        SignInNotice.refused(AppApplication.get(), login);
                    } catch (Exception ignored) {
                        // no notification
                    }
                }
                log("refresh token refused");
                return null;
            }
            FAILED_AT.remove(login);

            OauthToken pair = answer.token;
            AuthUser updated = copyWithPair(stored, pair, nowMs);
            try {
                // stored before it is used: the old refresh token no longer works
                dao.replaceAccount(updated);
            } catch (Exception e) {
                // keep it in memory at least, so this run of the app stays signed in
                Log.e(TAG, "could not store the new token", e);
            }
            remember(token, updated);
            AuthUser selected = AppData.INSTANCE.getAuthUser();
            if (selected != null && login.equals(selected.getLoginId())) {
                AppData.INSTANCE.setAuthUser(updated);
            }
            log("refreshed");
            return updated.getAccessToken();
        }
    }

    private static void remember(@NonNull String oldToken, @NonNull AuthUser user) {
        String newToken = user.getAccessToken();
        if (!StringUtils.isBlank(newToken) && !newToken.equals(oldToken)) {
            NEWER.put(oldToken, newToken);
        }
        if (!StringUtils.isBlank(newToken)) KNOWN.put(newToken, Account.of(user));
    }

    @NonNull
    private static AuthUser copyWithPair(@NonNull AuthUser stored, @NonNull OauthToken pair,
                                         long nowMs) {
        AuthUser user = new AuthUser(pair.getAccessToken(), stored.getAuthTime(),
                pair.getExpiresIn() == null || pair.getExpiresIn() < 0 ? 0 : pair.getExpiresIn(),
                stored.getScope(), stored.getSelected(), stored.getLoginId(), stored.getName(),
                stored.getAvatar());
        if (!StringUtils.isBlank(pair.getRefreshToken())) {
            user.setRefreshToken(pair.getRefreshToken());
            user.setAccessExpiresAt(TokenLifetime.endsAt(nowMs, pair.getExpiresIn()));
            user.setRefreshExpiresAt(TokenLifetime.endsAt(nowMs, pair.getRefreshTokenExpiresIn()));
        }
        // no refresh token in the answer: GitHub now gives a token that does not expire
        return user;
    }

    /** GitHub's answer to one refresh request; code -1 = no answer. */
    private static final class Answer {
        final int code;
        @Nullable final OauthToken token;

        Answer(int code, @Nullable OauthToken token) {
            this.code = code;
            this.token = token;
        }
    }

    @NonNull
    private static Answer ask(@Nullable String refreshToken) {
        if (StringUtils.isBlank(refreshToken)
                || StringUtils.isBlank(AppConfig.OPENHUB_CLIENT_ID)) {
            return new Answer(-1, null);
        }
        Request request = new Request.Builder()
                .url(AppConfig.GITHUB_BASE_URL + "login/oauth/access_token")
                .header("Accept", "application/json")
                // everything in the body, never in the URL
                .post(new FormBody.Builder()
                        .add("client_id", AppConfig.OPENHUB_CLIENT_ID)
                        .add("client_secret", AppConfig.OPENHUB_CLIENT_SECRET)
                        .add("grant_type", "refresh_token")
                        .add("refresh_token", refreshToken)
                        .build())
                .build();
        try (Response response = client().newCall(request).execute()) {
            ResponseBody body = response.body();
            OauthToken token = null;
            try {
                token = new Gson().fromJson(body.string(), OauthToken.class);
            } catch (Exception ignored) {
                // not JSON (an error page): temporary
            }
            return new Answer(response.code(), token);
        } catch (Exception e) {
            return new Answer(-1, null);
        }
    }

    @NonNull
    private static OkHttpClient client() {
        OkHttpClient result = client;
        if (result == null) {
            synchronized (TokenRefresher.class) {
                if (client == null) {
                    // its own client: never the interceptors that call back into here
                    client = new OkHttpClient.Builder()
                            // only GitHub's own certificates carry the sign-in (TlsGuard)
                            .addNetworkInterceptor(com.thirtydegreesray.openhub.util.TlsGuard.INSTANCE)
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(20, TimeUnit.SECONDS)
                            .writeTimeout(20, TimeUnit.SECONDS)
                            .build();
                }
                result = client;
            }
        }
        return result;
    }

    // ---- accounts ----

    @Nullable
    private static Account account(@NonNull String token) {
        Account known = KNOWN.get(token);
        if (known != null && (known.login != null
                || System.currentTimeMillis() - known.loadedAt < UNKNOWN_TTL_MS)) {
            return known;
        }
        AuthUser selected = AppData.INSTANCE.getAuthUser();
        if (selected != null && token.equals(selected.getAccessToken())) {
            Account account = Account.of(selected);
            KNOWN.put(token, account);
            return account;
        }
        try {
            AuthUserDao dao = AppApplication.get().getAppComponent().getDatabase().authUserDao();
            Account found = null;
            for (AuthUser user : dao.loadAll()) {
                if (StringUtils.isBlank(user.getAccessToken())) continue;
                Account account = Account.of(user);
                KNOWN.put(user.getAccessToken(), account);
                if (token.equals(user.getAccessToken())) found = account;
            }
            if (found == null) {
                // a token that is not saved (yet): e.g. the check during sign-in
                found = new Account(null, null, false);
                KNOWN.put(token, found);
            }
            return found;
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private static AuthUser findByLogin(@NonNull AuthUserDao dao, @NonNull String login) {
        for (AuthUser user : dao.loadAll()) {
            if (login.equals(user.getLoginId())) return user;
        }
        return null;
    }

    private static void log(String message) {
        if (BuildConfig.DEBUG) Log.d(TAG, message);
    }
}
