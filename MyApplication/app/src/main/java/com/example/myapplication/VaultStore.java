package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class VaultStore {
    private VaultStore() {}

    private static final String ENCRYPTED_PREFS = "vault_secure";

    public static SharedPreferences get(Context context) {
        Context appCtx = context.getApplicationContext();
        try {
            MasterKey masterKey = new MasterKey.Builder(appCtx)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    appCtx,
                    ENCRYPTED_PREFS,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            throw new IllegalStateException("Unable to open encrypted vault store", e);
        }
    }

    public static final class VaultItem {
        public final String id;
        public final String category;
        public final String label;
        public final String account;
        public final String password;
        public final String optional;
        public final String imageBase64;
        public final long createdAtMillis;

        public VaultItem(String id, String category, String label, String account,
                         String password, String optional, long createdAtMillis) {
            this(id, category, label, account, password, optional, null, createdAtMillis);
        }

        public VaultItem(String id, String category, String label, String account,
                         String password, String optional, String imageBase64, long createdAtMillis) {
            this.id = id;
            this.category = category;
            this.label = label;
            this.account = account;
            this.password = password;
            this.optional = optional;
            this.imageBase64 = imageBase64;
            this.createdAtMillis = createdAtMillis;
        }

        JSONObject toJson() throws Exception {
            JSONObject obj = new JSONObject();
            obj.put("id", id);
            obj.put("category", category);
            obj.put("label", label);
            obj.put("account", account);
            obj.put("password", password);
            obj.put("optional", optional);
            obj.put("imageBase64", imageBase64 != null ? imageBase64 : "");
            obj.put("createdAtMillis", createdAtMillis);
            return obj;
        }

        static VaultItem fromJson(JSONObject obj) throws Exception {
            if (obj == null) return null;
            String imgB64 = obj.optString("imageBase64", "");
            return new VaultItem(
                    obj.optString("id", UUID.randomUUID().toString()),
                    obj.optString("category", ""),
                    obj.optString("label", ""),
                    obj.optString("account", ""),
                    obj.optString("password", ""),
                    obj.optString("optional", ""),
                    imgB64 != null && !imgB64.isEmpty() ? imgB64 : null,
                    obj.optLong("createdAtMillis", System.currentTimeMillis())
            );
        }
    }

    public static List<VaultItem> getAll(Context context) {
        SharedPreferences prefs = get(context);
        String raw = prefs.getString("vault_list", "[]");
        List<VaultItem> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                try {
                    VaultItem item = VaultItem.fromJson(arr.getJSONObject(i));
                    if (item != null) out.add(item);
                } catch (Exception ignored) {
                    // Skip individual item if it fails to parse
                }
            }
        } catch (Exception e) {
            android.util.Log.e("VaultStore", "Failed to parse vault list", e);
        }
        return out;
    }

    public static void put(Context context, VaultItem item) {
        if (item == null) return;
        List<VaultItem> all = getAll(context);
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(item.id)) {
                all.set(i, item);
                replaced = true;
                break;
            }
        }
        if (!replaced) all.add(item);
        saveAll(context, all);
    }

    public static void delete(Context context, String itemId) {
        if (itemId == null) return;
        List<VaultItem> all = getAll(context);
        List<VaultItem> remaining = new ArrayList<>();
        for (VaultItem item : all) {
            if (!item.id.equals(itemId)) remaining.add(item);
        }
        saveAll(context, remaining);
    }

    public static void clear(Context context) {
        if (!get(context).edit().remove("vault_list").commit()) {
            android.util.Log.e("VaultStore", "Failed to commit vault clear");
        }
    }

    private static void saveAll(Context context, List<VaultItem> items) {
        try {
            JSONArray arr = new JSONArray();
            for (VaultItem item : items) {
                arr.put(item.toJson());
            }
            String json = arr.toString();
            if (!get(context).edit().putString("vault_list", json).commit()) {
                android.util.Log.e("VaultStore", "Failed to commit vault save");
            }
        } catch (Exception e) {
            android.util.Log.e("VaultStore", "Failed to save vault items", e);
        }
    }
}
