package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.bouncycastle.crypto.digests.Blake2bDigest;
import org.bouncycastle.crypto.engines.Salsa20Engine;
import org.bouncycastle.crypto.engines.XSalsa20Engine;
import org.bouncycastle.crypto.macs.Poly1305;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.math.ec.rfc7748.X25519;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.Pack;

import java.security.SecureRandom;

/**
 * libsodium's {@code crypto_box_seal}: how GitHub wants secret values encrypted. Only the
 * repository's private key (held by GitHub) can open the result.
 * <p>
 * Sealed box = ephemeral public key (32) | Poly1305 tag (16) | XSalsa20 ciphertext, where
 * the key is HSalsa20 of the X25519 shared secret and the nonce is
 * BLAKE2b-192(ephemeral public key | recipient public key).
 */
public final class SealedBox {

    public static final int KEY_BYTES = 32;
    private static final int MAC_BYTES = 16;
    private static final int NONCE_BYTES = 24;

    private SealedBox() {
    }

    @NonNull
    public static byte[] seal(@NonNull byte[] message, @NonNull byte[] recipientPublicKey) {
        return seal(message, recipientPublicKey, new SecureRandom());
    }

    @NonNull
    static byte[] seal(@NonNull byte[] message, @NonNull byte[] recipientPublicKey,
                       @NonNull SecureRandom random) {
        if (recipientPublicKey.length != KEY_BYTES) {
            throw new IllegalArgumentException("public key must be 32 bytes");
        }
        byte[] ephemeralSecret = new byte[KEY_BYTES];
        random.nextBytes(ephemeralSecret);
        byte[] ephemeralPublic = new byte[KEY_BYTES];
        X25519.scalarMultBase(ephemeralSecret, 0, ephemeralPublic, 0);

        byte[] nonce = nonce(ephemeralPublic, recipientPublicKey);
        byte[] key = boxKey(ephemeralSecret, recipientPublicKey);
        Arrays.fill(ephemeralSecret, (byte) 0);

        byte[] boxed = secretBox(message, nonce, key);
        Arrays.fill(key, (byte) 0);

        byte[] result = new byte[KEY_BYTES + boxed.length];
        System.arraycopy(ephemeralPublic, 0, result, 0, KEY_BYTES);
        System.arraycopy(boxed, 0, result, KEY_BYTES, boxed.length);
        return result;
    }

    /** {@code crypto_box_seal_open}: for the tests. Null when the box is not valid. */
    @Nullable
    static byte[] open(@NonNull byte[] sealed, @NonNull byte[] publicKey, @NonNull byte[] secretKey) {
        if (sealed.length < KEY_BYTES + MAC_BYTES) return null;
        byte[] ephemeralPublic = Arrays.copyOfRange(sealed, 0, KEY_BYTES);
        byte[] nonce = nonce(ephemeralPublic, publicKey);
        byte[] key = boxKey(secretKey, ephemeralPublic);
        byte[] tag = Arrays.copyOfRange(sealed, KEY_BYTES, KEY_BYTES + MAC_BYTES);
        byte[] cipher = Arrays.copyOfRange(sealed, KEY_BYTES + MAC_BYTES, sealed.length);

        byte[] stream = new byte[KEY_BYTES + cipher.length];
        xsalsa20(key, nonce).processBytes(new byte[stream.length], 0, stream.length, stream, 0);
        if (!Arrays.constantTimeAreEqual(tag, poly1305(Arrays.copyOfRange(stream, 0, KEY_BYTES), cipher))) {
            return null;
        }
        byte[] message = new byte[cipher.length];
        for (int i = 0; i < cipher.length; i++) message[i] = (byte) (cipher[i] ^ stream[KEY_BYTES + i]);
        return message;
    }

    /** A new X25519 key pair {public, secret}: for the tests. */
    @NonNull
    static byte[][] keyPair(@NonNull SecureRandom random) {
        byte[] secret = new byte[KEY_BYTES];
        random.nextBytes(secret);
        byte[] pub = new byte[KEY_BYTES];
        X25519.scalarMultBase(secret, 0, pub, 0);
        return new byte[][]{pub, secret};
    }

    // ---------------------------------------------------------------------------------

    @NonNull
    private static byte[] nonce(@NonNull byte[] ephemeralPublic, @NonNull byte[] recipientPublic) {
        Blake2bDigest blake = new Blake2bDigest(NONCE_BYTES * 8);
        blake.update(ephemeralPublic, 0, ephemeralPublic.length);
        blake.update(recipientPublic, 0, recipientPublic.length);
        byte[] nonce = new byte[NONCE_BYTES];
        blake.doFinal(nonce, 0);
        return nonce;
    }

    /** {@code crypto_box_beforenm}: HSalsa20(X25519(secret, public), zero nonce). */
    @NonNull
    private static byte[] boxKey(@NonNull byte[] secretKey, @NonNull byte[] publicKey) {
        byte[] shared = new byte[KEY_BYTES];
        X25519.scalarMult(secretKey, 0, publicKey, 0, shared, 0);
        byte[] key = hsalsa20(shared, new byte[16]);
        Arrays.fill(shared, (byte) 0);
        return key;
    }

    /** HSalsa20, as XSalsa20Engine computes its sub key. */
    @NonNull
    private static byte[] hsalsa20(@NonNull byte[] key, @NonNull byte[] input16) {
        int[] state = new int[16];
        state[0] = 0x61707865;
        state[5] = 0x3320646e;
        state[10] = 0x79622d32;
        state[15] = 0x6b206574;
        Pack.littleEndianToInt(key, 0, state, 1, 4);
        Pack.littleEndianToInt(key, 16, state, 11, 4);
        Pack.littleEndianToInt(input16, 0, state, 6, 4);
        int[] out = new int[16];
        Salsa20Engine.salsaCore(20, state, out);
        // salsaCore adds the input words back; HSalsa20 does not
        int[] words = {
                out[0] - state[0], out[5] - state[5], out[10] - state[10], out[15] - state[15],
                out[6] - state[6], out[7] - state[7], out[8] - state[8], out[9] - state[9]
        };
        byte[] result = new byte[KEY_BYTES];
        Pack.intToLittleEndian(words, result, 0);
        return result;
    }

    /** {@code crypto_secretbox}: tag | ciphertext. */
    @NonNull
    private static byte[] secretBox(@NonNull byte[] message, @NonNull byte[] nonce, @NonNull byte[] key) {
        byte[] input = new byte[KEY_BYTES + message.length];
        System.arraycopy(message, 0, input, KEY_BYTES, message.length);
        byte[] output = new byte[input.length];
        xsalsa20(key, nonce).processBytes(input, 0, input.length, output, 0);
        byte[] cipher = Arrays.copyOfRange(output, KEY_BYTES, output.length);
        byte[] tag = poly1305(Arrays.copyOfRange(output, 0, KEY_BYTES), cipher);
        byte[] result = new byte[MAC_BYTES + cipher.length];
        System.arraycopy(tag, 0, result, 0, MAC_BYTES);
        System.arraycopy(cipher, 0, result, MAC_BYTES, cipher.length);
        Arrays.fill(input, (byte) 0);
        Arrays.fill(output, (byte) 0);
        return result;
    }

    @NonNull
    private static XSalsa20Engine xsalsa20(@NonNull byte[] key, @NonNull byte[] nonce) {
        XSalsa20Engine engine = new XSalsa20Engine();
        engine.init(true, new ParametersWithIV(new KeyParameter(key), nonce));
        return engine;
    }

    @NonNull
    private static byte[] poly1305(@NonNull byte[] oneTimeKey, @NonNull byte[] data) {
        Poly1305 mac = new Poly1305();
        mac.init(new KeyParameter(oneTimeKey));
        mac.update(data, 0, data.length);
        byte[] tag = new byte[MAC_BYTES];
        mac.doFinal(tag, 0);
        return tag;
    }
}
