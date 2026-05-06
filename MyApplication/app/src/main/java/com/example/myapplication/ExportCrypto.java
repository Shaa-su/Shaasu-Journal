package com.example.myapplication;

import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class ExportCrypto {
    private ExportCrypto() {}

    // Envelope format so we can detect encrypted backups.
    // { "format": "shaasu-backup", "v": 1, "enc": "AES-256-GCM", "kdf": "PBKDF2", "iter": 200000, "salt": "...", "iv": "...", "ct": "..." }

    private static final String FORMAT = "shaasu-backup";
    private static final int VERSION = 1;

    private static final int SALT_LEN_BYTES = 16;
    private static final int IV_LEN_BYTES = 12; // recommended for GCM
    private static final int KEY_LEN_BITS = 256;
    private static final int GCM_TAG_LEN_BITS = 128;

    // Keep this reasonably strong but not too slow on low-end devices.
    private static final int PBKDF2_ITERS = 200_000;

    public static boolean looksEncrypted(JSONObject root) {
        if (root == null) return false;
        String format = root.optString("format", "");
        int v = root.optInt("v", -1);
        String enc = root.optString("enc", "");
        return FORMAT.equals(format) && v == VERSION && enc.contains("GCM") && root.has("ct");
    }

    public static JSONObject encryptToEnvelope(String plaintextJson, char[] password) throws GeneralSecurityException, JSONException {
        if (plaintextJson == null) plaintextJson = "";
        byte[] salt = new byte[SALT_LEN_BYTES];
        byte[] iv = new byte[IV_LEN_BYTES];
        SecureRandom rng = new SecureRandom();
        rng.nextBytes(salt);
        rng.nextBytes(iv);

        SecretKey key = deriveKey(password, salt, PBKDF2_ITERS);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LEN_BITS, iv));
        byte[] ct = cipher.doFinal(plaintextJson.getBytes(StandardCharsets.UTF_8));

        JSONObject env = new JSONObject();
        env.put("format", FORMAT);
        env.put("v", VERSION);
        env.put("enc", "AES-256-GCM");
        env.put("kdf", "PBKDF2");
        env.put("iter", PBKDF2_ITERS);
        env.put("salt", Base64.encodeToString(salt, Base64.NO_WRAP));
        env.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP));
        env.put("ct", Base64.encodeToString(ct, Base64.NO_WRAP));
        return env;
    }

    public static String decryptEnvelope(JSONObject envelope, char[] password) throws GeneralSecurityException {
        if (envelope == null) throw new GeneralSecurityException("Missing envelope");

        int iters = envelope.optInt("iter", -1);
        String saltB64 = envelope.optString("salt", null);
        String ivB64 = envelope.optString("iv", null);
        String ctB64 = envelope.optString("ct", null);

        if (iters <= 0 || saltB64 == null || ivB64 == null || ctB64 == null) {
            throw new GeneralSecurityException("Invalid envelope");
        }

        byte[] salt = Base64.decode(saltB64, Base64.DEFAULT);
        byte[] iv = Base64.decode(ivB64, Base64.DEFAULT);
        byte[] ct = Base64.decode(ctB64, Base64.DEFAULT);

        SecretKey key = deriveKey(password, salt, iters);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LEN_BITS, iv));
        byte[] pt = cipher.doFinal(ct);
        return new String(pt, StandardCharsets.UTF_8);
    }

    private static SecretKey deriveKey(char[] password, byte[] salt, int iters) throws GeneralSecurityException {
        // PBKDF2WithHmacSHA256 is only available on some API levels/providers.
        // Use SHA-256 when available; otherwise fall back to SHA-1.
        String[] algos = new String[]{"PBKDF2WithHmacSHA256", "PBKDF2WithHmacSHA1"};
        GeneralSecurityException last = null;

        for (String algo : algos) {
            try {
                PBEKeySpec spec = new PBEKeySpec(password, salt, iters, KEY_LEN_BITS);
                SecretKeyFactory skf = SecretKeyFactory.getInstance(algo);
                byte[] keyBytes = skf.generateSecret(spec).getEncoded();
                // Wipe intermediate where possible
                spec.clearPassword();
                SecretKey key = new SecretKeySpec(keyBytes, "AES");
                Arrays.fill(keyBytes, (byte) 0);
                return key;
            } catch (GeneralSecurityException e) {
                last = e;
            }
        }

        throw last != null ? last : new GeneralSecurityException("No PBKDF2 available");
    }
}
