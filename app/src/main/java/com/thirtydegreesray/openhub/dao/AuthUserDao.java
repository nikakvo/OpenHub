package com.thirtydegreesray.openhub.dao;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.thirtydegreesray.openhub.util.TokenCrypto;

import java.util.ArrayList;
import java.util.List;

/**
 * Signed-in accounts. The token is the primary key and is stored encrypted
 * ({@link TokenCrypto}): the package-private methods see the stored (sealed) rows, the
 * public ones take and return accounts with the plain token and never change the objects
 * they are given.
 */
@Dao
public abstract class AuthUserDao {

    // ---- stored rows (sealed tokens) ----

    @Query("SELECT * FROM AUTH_USER")
    abstract List<AuthUser> storedAll();

    @Query("SELECT * FROM AUTH_USER ORDER BY SELECTED DESC LIMIT 1")
    abstract AuthUser storedSelectedOrFirst();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract void storedInsert(AuthUser user);

    @Update
    abstract int storedUpdate(AuthUser user);

    @Query("DELETE FROM AUTH_USER WHERE ACCESS_TOKEN = :storedToken")
    abstract int storedDelete(String storedToken);

    // ---- public API (plain tokens) ----

    /** All accounts. */
    @NonNull
    public List<AuthUser> loadAll() {
        List<AuthUser> result = new ArrayList<>();
        for (AuthUser stored : storedAll()) result.add(opened(stored));
        return result;
    }

    /** The selected account, or the first one when none is selected, or null. */
    @Nullable
    public AuthUser loadSelectedOrFirst() {
        AuthUser stored = storedSelectedOrFirst();
        return stored == null ? null : opened(stored);
    }

    /** Saves a new account (an account with the same token is replaced). */
    public void insert(@NonNull AuthUser user) {
        storedInsert(sealed(user));
    }

    public void update(@NonNull AuthUser user) {
        storedUpdate(sealed(user));
    }

    /** Deletes the account with this token; does nothing for null. */
    public void delete(@Nullable AuthUser user) {
        if (user == null || user.getAccessToken() == null) return;
        storedDelete(TokenCrypto.seal(user.getAccessToken()));
    }

    /** Deletes the accounts of this login, whatever form their token has. */
    @Query("DELETE FROM AUTH_USER WHERE LOGIN_ID = :loginId")
    public abstract int deleteByLogin(String loginId);

    @Query("UPDATE AUTH_USER SET SELECTED = 0")
    public abstract void unselectAll();

    /** Makes this login the selected account (and no other). */
    @Transaction
    public void select(@NonNull String loginId) {
        unselectAll();
        setSelected(loginId, true);
    }

    @Query("UPDATE AUTH_USER SET SELECTED = :selected WHERE LOGIN_ID = :loginId")
    abstract void setSelected(String loginId, boolean selected);

    /**
     * Encrypts the tokens that are still stored in plain text (accounts from before 3.5.1,
     * or saved while the Keystore was not available). Does nothing when all are encrypted
     * or there is no Keystore. One transaction: on an error the plain rows stay and still
     * work.
     */
    @Transaction
    public void sealPlainTokens() {
        for (AuthUser stored : storedAll()) {
            String token = stored.getAccessToken();
            if (token == null || token.isEmpty() || TokenCrypto.isSealed(token)) continue;
            String sealedToken = TokenCrypto.seal(token);
            if (sealedToken.equals(token)) return; // no Keystore: keep everything plain
            // the key changes, so the row is replaced: delete the plain one, insert sealed
            storedDelete(token);
            storedInsert(copy(stored, sealedToken));
        }
    }

    private static AuthUser sealed(AuthUser user) {
        return copy(user, TokenCrypto.seal(user.getAccessToken()));
    }

    private static AuthUser opened(AuthUser stored) {
        return copy(stored, TokenCrypto.open(stored.getAccessToken()));
    }

    private static AuthUser copy(AuthUser user, String token) {
        return new AuthUser(token, user.getAuthTime(), user.getExpireIn(), user.getScope(),
                user.getSelected(), user.getLoginId(), user.getName(), user.getAvatar());
    }
}
