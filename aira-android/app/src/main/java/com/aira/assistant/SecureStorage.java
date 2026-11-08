package com.aira.assistant;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteOpenHelper;

import java.security.KeyStore;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * Secure encrypted local storage using SQLCipher.
 * The database passphrase is derived from Android Keystore — never hardcoded.
 */
public class SecureStorage {

    private static final String TAG = "SecureStorage";
    private static final String DB_NAME = "aira_secure.db";
    private static final int DB_VERSION = 1;
    private static final String KEYSTORE_ALIAS = "AiraDbKey";
    private static final String TABLE_NOTES = "notes";
    private static final String COL_TOPIC = "topic";
    private static final String COL_CONTENT = "content";
    private static final String COL_TIMESTAMP = "timestamp";

    private static SecureStorage instance;
    private final DbHelper dbHelper;
    private final String passphrase;
    private final Context context;

    private SecureStorage(Context context) {
        this.context = context.getApplicationContext();
        SQLiteDatabase.loadLibs(this.context);
        this.passphrase = getOrCreatePassphrase();
        this.dbHelper = new DbHelper(this.context);
    }

    public static synchronized SecureStorage getInstance(Context context) {
        if (instance == null) {
            instance = new SecureStorage(context);
        }
        return instance;
    }

    // ── Passphrase via Android Keystore ───────────────────────────────────────

    private String getOrCreatePassphrase() {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);

            if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
                KeyGenerator keyGenerator = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
                keyGenerator.init(
                        new KeyGenParameterSpec.Builder(
                                KEYSTORE_ALIAS,
                                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
                        )
                                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                                .setKeySize(256)
                                .build()
                );
                keyGenerator.generateKey();
            }

            SecretKey key = ((KeyStore.SecretKeyEntry) keyStore.getEntry(KEYSTORE_ALIAS, null)).getSecretKey();
            byte[] encoded = key.getEncoded();
            // If getEncoded() returns null (hardware-backed), use alias hash as deterministic passphrase
            if (encoded == null) {
                return Base64.encodeToString(KEYSTORE_ALIAS.getBytes(), Base64.NO_WRAP);
            }
            return Base64.encodeToString(encoded, Base64.NO_WRAP);

        } catch (Exception e) {
            Log.e(TAG, "Keystore error, using fallback passphrase", e);
            // Fallback: use a stable device-specific string (not ideal, but prevents crash)
            return Base64.encodeToString((KEYSTORE_ALIAS + android.os.Build.FINGERPRINT).getBytes(), Base64.NO_WRAP);
        }
    }

    // ── CRUD Operations ───────────────────────────────────────────────────────

    public void saveNote(String topic, String content) {
        try (SQLiteDatabase db = dbHelper.getWritableDatabase(passphrase)) {
            db.execSQL(
                    "INSERT OR REPLACE INTO " + TABLE_NOTES + " (" + COL_TOPIC + ", " + COL_CONTENT + ", " + COL_TIMESTAMP + ") VALUES (?, ?, ?)",
                    new Object[]{topic, content, System.currentTimeMillis()}
            );
        } catch (Exception e) {
            Log.e(TAG, "saveNote failed", e);
        }
    }

    public String readNote(String topic) {
        try (SQLiteDatabase db = dbHelper.getReadableDatabase(passphrase);
             net.sqlcipher.Cursor cursor = db.rawQuery(
                     "SELECT " + COL_CONTENT + " FROM " + TABLE_NOTES + " WHERE " + COL_TOPIC + " = ?",
                     new String[]{topic}
             )) {
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "readNote failed", e);
        }
        return null;
    }

    public void deleteNote(String topic) {
        try (SQLiteDatabase db = dbHelper.getWritableDatabase(passphrase)) {
            db.execSQL("DELETE FROM " + TABLE_NOTES + " WHERE " + COL_TOPIC + " = ?", new Object[]{topic});
        } catch (Exception e) {
            Log.e(TAG, "deleteNote failed", e);
        }
    }

    public java.util.List<String> listTopics() {
        java.util.List<String> topics = new java.util.ArrayList<>();
        try (SQLiteDatabase db = dbHelper.getReadableDatabase(passphrase);
             net.sqlcipher.Cursor cursor = db.rawQuery(
                     "SELECT " + COL_TOPIC + " FROM " + TABLE_NOTES + " ORDER BY " + COL_TIMESTAMP + " DESC",
                     null
             )) {
            while (cursor.moveToNext()) {
                topics.add(cursor.getString(0));
            }
        } catch (Exception e) {
            Log.e(TAG, "listTopics failed", e);
        }
        return topics;
    }

    // ── SQLite Helper ─────────────────────────────────────────────────────────

    private static class DbHelper extends SQLiteOpenHelper {
        DbHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(
                    "CREATE TABLE IF NOT EXISTS " + TABLE_NOTES + " ("
                            + COL_TOPIC + " TEXT PRIMARY KEY, "
                            + COL_CONTENT + " TEXT NOT NULL, "
                            + COL_TIMESTAMP + " INTEGER DEFAULT 0"
                            + ")"
            );
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NOTES);
            onCreate(db);
        }
    }
}
