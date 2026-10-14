package com.thirtydegreesray.openhub.util;

import android.database.Cursor;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.thirtydegreesray.openhub.dao.AuthUser;
import com.thirtydegreesray.openhub.dao.AuthUserDao;
import com.thirtydegreesray.openhub.dao.DaoSession;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Encrypts the GitHub tokens of the accounts table with keys of the Android Keystore,
 * which never leave the phone's secure hardware (where it has one) and are not part of
 * any backup. A copied database file is useless without them.
 *
 * The token is the table's primary key, so the encryption must be deterministic (the same
 * token always gives the same text, or deleting an account would not find its row):
 * AES-256-GCM whose 12-byte nonce is an HMAC-SHA256 of the token (a synthetic-IV
 * construction). Equal tokens give equal texts; nothing else is revealed.
 *
 * Stored form: "v1:" + Base64(nonce + ciphertext + tag). Older rows hold the plain token;
 * {@link #open} accepts both and {@link #migrate} rewrites the old ones once.
 * When the Keystore does not work on a device, tokens stay as they were (the app keeps
 * working, as before this change).
 */
public final class TokenCrypto {

    private static final String PREFIX = "v1:";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String AES_ALIAS = "openhub_token_aes";
    private static final String MAC_ALIAS = "openhub_token_mac";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    @Nullable private static SecretKey aesKey;
    @Nullable private static SecretKey macKey;
    private static boolean unavailable;

    private TokenCrypto() {
    }

    /** The text to store for {@code token}; the token itself when encryption is not possible. */
    public static synchronized String seal(@Nullable String token) {
        if (token == null || token.isEmpty() || token.startsWith(PREFIX)) return token;
        if (!loadKeys()) return token;
        try {
            byte[] plain = token.getBytes(StandardCharsets.UTF_8);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(macKey);
            byte[] nonce = Arrays.copyOf(mac.doFinal(plain), NONCE_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] sealed = cipher.doFinal(plain);
            byte[] out = new byte[nonce.length + sealed.length];
            System.arraycopy(nonce, 0, out, 0, nonce.length);
            System.arraycopy(sealed, 0, out, nonce.length, sealed.length);
            return PREFIX + java.util.Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            return token;
        }
    }

    /**
     * The token for a stored text. A plain (old) token comes back unchanged; a text that
     * cannot be opened (keys gone) gives "", which the app treats as signed out.
     */
    public static synchronized String open(@Nullable String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) return stored;
        if (!loadKeys()) return "";
        try {
            byte[] data = java.util.Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] nonce = Arrays.copyOf(data, NONCE_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] plain = cipher.doFinal(data, NONCE_BYTES, data.length - NONCE_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean isSealed(@Nullable String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }

    private static boolean loadKeys() {
        if (aesKey != null && macKey != null) return true;
        if (unavailable) return false;
        try {
            KeyStore store = KeyStore.getInstance(KEYSTORE);
            store.load(null);
            if (!store.containsAlias(AES_ALIAS)) {
                KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
                generator.init(new KeyGenParameterSpec.Builder(AES_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        // the nonce comes from the HMAC (deterministic, see the class comment)
                        .setRandomizedEncryptionRequired(false)
                        .build());
                generator.generateKey();
            }
            if (!store.containsAlias(MAC_ALIAS)) {
                KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, KEYSTORE);
                generator.init(new KeyGenParameterSpec.Builder(MAC_ALIAS, KeyProperties.PURPOSE_SIGN).build());
                generator.generateKey();
            }
            aesKey = (SecretKey) store.getKey(AES_ALIAS, null);
            macKey = (SecretKey) store.getKey(MAC_ALIAS, null);
            if (aesKey == null || macKey == null) {
                unavailable = true;
                return false;
            }
            return true;
        } catch (Exception e) {
            unavailable = true;
            return false;
        }
    }

    /**
     * Encrypts the tokens that are still stored in plain text (accounts from before this
     * version). Runs once at start; does nothing when everything is encrypted already.
     */
    public static void migrate(@NonNull DaoSession session) {
        AuthUserDao dao = session.getAuthUserDao();
        List<String> plain = new ArrayList<>();
        try (Cursor cursor = dao.getDatabase().rawQuery("SELECT "
                + AuthUserDao.Properties.AccessToken.columnName + " FROM " + AuthUserDao.TABLENAME, null)) {
            while (cursor.moveToNext()) {
                String stored = cursor.getString(0);
                if (stored != null && !isSealed(stored)) plain.add(stored);
            }
        } catch (Exception e) {
            return;
        }
        if (plain.isEmpty() || seal(plain.get(0)).equals(plain.get(0))) return; // nothing to do, or no Keystore
        dao.detachAll();
        List<AuthUser> users = dao.loadAll(); // tokens come back as plain text either way
        session.getDatabase().beginTransaction();
        try {
            for (AuthUser user : users) {
                String token = user.getAccessToken();
                if (token == null || !plain.contains(token)) continue;
                // the key changes, so the row is replaced: delete the plain one, insert sealed
                dao.getDatabase().execSQL("DELETE FROM " + AuthUserDao.TABLENAME + " WHERE "
                        + AuthUserDao.Properties.AccessToken.columnName + " = ?", new Object[]{token});
                dao.insert(user);
            }
            session.getDatabase().setTransactionSuccessful();
        } catch (Exception ignored) {
            // the transaction rolls back: the plain rows stay and still work
        } finally {
            session.getDatabase().endTransaction();
            dao.detachAll();
        }
    }
}
