package com.asifqureshi.aivoicestudio.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Stores the access/refresh tokens encrypted with an AES-256-GCM key that lives in the Android Keystore
 * (the key material never leaves secure hardware / the keystore). Tokens are never logged.
 */
public final class SecureTokenStore {
    private static final String ALIAS = "avs_token_key_v1";
    private final SharedPreferences sp;
    private String access, refresh;

    public SecureTokenStore(Context c) {
        sp = c.getSharedPreferences("secure_session", Context.MODE_PRIVATE);
        access = decrypt(sp.getString("a", null));
        refresh = decrypt(sp.getString("r", null));
    }

    public synchronized String getAccess() { return access; }
    public synchronized String getRefresh() { return refresh; }
    public synchronized boolean hasSession() { return refresh != null; }

    public synchronized void save(String accessToken, String refreshToken) {
        access = accessToken;
        refresh = refreshToken;
        sp.edit().putString("a", encrypt(accessToken)).putString("r", encrypt(refreshToken)).apply();
    }

    public synchronized void clear() {
        access = null;
        refresh = null;
        sp.edit().clear().apply();
    }

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            kg.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            return kg.generateKey();
        }
        return (SecretKey) ks.getKey(ALIAS, null);
    }

    private static String encrypt(String plain) {
        if (plain == null) return null;
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key());
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(c.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new IllegalStateException("Secure storage unavailable", e);
        }
    }

    private static String decrypt(String stored) {
        if (stored == null) return null;
        try {
            String[] p = stored.split(":");
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(p[0], Base64.NO_WRAP)));
            return new String(c.doFinal(Base64.decode(p[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null; // key invalidated or data corrupt: treat as signed out
        }
    }
}
