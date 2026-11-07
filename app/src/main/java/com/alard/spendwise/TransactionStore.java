package com.alard.spendwise;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-user local transaction storage with a durable Firebase upload queue.
 * Local saves are immediate; remote writes are asynchronous and retryable.
 */
public final class TransactionStore {

    private static final String PREFS_NAME = "SpendWiseLocalTransactions";
    private static final String PENDING_PREFIX = "pending_";
    private static final String MIGRATED_PREFIX = "migration_queued_";
    private static final String ERROR_PREFIX = "sync_error_";
    private static final String OP_SAVE = "save";
    private static final String OP_DELETE = "delete";

    // Avoid concurrent writes from this process to the same user's remote path.
    private static final Map<String, String> IN_FLIGHT = new HashMap<>();

    private TransactionStore() {
        // Utility class.
    }

    public static final class Record {
        public final String id;
        public final boolean isIncome;
        public final String categoryId;
        public final String categoryName;
        public final String subcategory;
        public final long amountPaisa;
        public final long timestamp;
        public final String note;
        // Only used in cloud listener snapshots; never stored as a transaction.
        public final boolean isRemoteDeletion;

        Record(String id, boolean isIncome, String categoryId, String categoryName,
               String subcategory, long amountPaisa, long timestamp, String note) {
            this.id = id;
            this.isIncome = isIncome;
            this.categoryId = categoryId;
            this.categoryName = categoryName;
            this.subcategory = subcategory;
            this.amountPaisa = amountPaisa;
            this.timestamp = timestamp;
            this.note = note;
            this.isRemoteDeletion = false;
        }

        private Record(String deletedId) {
            this.id = deletedId;
            this.isIncome = false;
            this.categoryId = "";
            this.categoryName = "";
            this.subcategory = "";
            this.amountPaisa = 0;
            this.timestamp = 0;
            this.note = "";
            this.isRemoteDeletion = true;
        }

        /** Marker received from Firebase, not a real Income/Expense record. */
        static Record remoteDeletion(String deletedId) {
            if (deletedId == null || !deletedId.matches("[A-Za-z0-9_-]+")) {
                throw new IllegalArgumentException("Invalid deleted transaction ID.");
            }
            return new Record(deletedId);
        }

        JSONObject toJson() throws JSONException {
            if (isRemoteDeletion) {
                throw new JSONException("Deletion markers must not be stored as transactions.");
            }
            JSONObject json = new JSONObject();
            json.put("id", id);
            json.put("type", isIncome ? "income" : "expense");
            json.put("categoryId", categoryId);
            json.put("categoryName", categoryName);
            json.put("subcategory", subcategory);
            json.put("amountPaisa", amountPaisa);
            json.put("timestamp", timestamp);
            json.put("note", note);
            return json;
        }

        static Record fromJson(JSONObject json) throws JSONException {
            String type = json.getString("type");
            if (!"income".equals(type) && !"expense".equals(type)) {
                throw new JSONException("Invalid transaction type");
            }
            return new Record(
                    json.getString("id"),
                    "income".equals(type),
                    json.getString("categoryId"),
                    json.getString("categoryName"),
                    json.getString("subcategory"),
                    json.getLong("amountPaisa"),
                    json.getLong("timestamp"),
                    json.optString("note", "")
            );
        }
    }

    private static String currentUid() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            throw new IllegalStateException("Sign in before accessing transactions.");
        }
        return user.getUid();
    }

    private static String userKey() {
        return "user_" + currentUid();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static JSONArray readArray(SharedPreferences preferences, String key)
            throws JSONException {
        return new JSONArray(preferences.getString(key, "[]"));
    }

    private static boolean validSelection(boolean isIncome,
                                          CategoryCatalog.Category category,
                                          String subcategory, long amountPaisa,
                                          long timestamp) {
        if (category == null || subcategory == null || subcategory.trim().isEmpty()
                || amountPaisa <= 0 || timestamp <= 0
                || timestamp > System.currentTimeMillis()) {
            return false;
        }
        CategoryCatalog.Category canonical =
                CategoryCatalog.findCategory(category.getId(), isIncome);
        return canonical != null && canonical.getSubcategories().contains(subcategory);
    }

    /** Returns newest first. JSON read errors never erase stored transactions. */
    public static synchronized List<Record> getAll(Context context) {
        String key = userKey();
        List<Record> result = new ArrayList<>();
        try {
            JSONArray array = readArray(prefs(context), key);
            for (int i = 0; i < array.length(); i++) {
                result.add(Record.fromJson(array.getJSONObject(i)));
            }
        } catch (JSONException | ClassCastException error) {
            throw new IllegalStateException("Saved transaction data could not be read.", error);
        }
        result.sort(Comparator.comparingLong((Record r) -> r.timestamp).reversed());
        return result;
    }

    /** Saves locally and atomically queues the same ID for Firebase. */
    public static synchronized boolean add(Context context, boolean isIncome,
                                           CategoryCatalog.Category category,
                                           String subcategory, long amountPaisa,
                                           long timestamp, String note) {
        if (!validSelection(isIncome, category, subcategory, amountPaisa, timestamp)) {
            return false;
        }
        String uid = currentUid();
        String key = "user_" + uid;
        SharedPreferences preferences = prefs(context);
        try {
            JSONArray array = readArray(preferences, key);
            JSONArray pending = readArray(preferences, PENDING_PREFIX + uid);
            Record record = new Record(UUID.randomUUID().toString(), isIncome,
                    category.getId(), category.getName(), subcategory,
                    amountPaisa, timestamp, note == null ? "" : note.trim());
            array.put(record.toJson());
            pending.put(saveOperation(record));
            boolean committed = preferences.edit()
                    .putString(key, array.toString())
                    .putString(PENDING_PREFIX + uid, pending.toString())
                    .commit();
            if (committed) syncPending(context);
            return committed;
        } catch (JSONException | ClassCastException error) {
            return false;
        }
    }

    /** Gets the original record for Edit / Details. */
    public static synchronized Record findById(Context context, String transactionId) {
        if (transactionId == null || transactionId.isEmpty()) return null;
        for (Record record : getAll(context)) {
            if (transactionId.equals(record.id)) return record;
        }
        return null;
    }

    /** Replaces exactly one local record, preserving its ID, then queues upload. */
    public static synchronized boolean update(Context context, String transactionId,
                                              boolean isIncome,
                                              CategoryCatalog.Category category,
                                              String subcategory, long amountPaisa,
                                              long timestamp, String note) {
        if (transactionId == null || transactionId.isEmpty()
                || !validSelection(isIncome, category, subcategory, amountPaisa, timestamp)) {
            return false;
        }
        String uid = currentUid();
        String key = "user_" + uid;
        SharedPreferences preferences = prefs(context);
        try {
            JSONArray oldArray = readArray(preferences, key);
            JSONArray updatedArray = new JSONArray();
            JSONArray pending = readArray(preferences, PENDING_PREFIX + uid);
            int matches = 0;
            Record replacement = new Record(transactionId, isIncome,
                    category.getId(), category.getName(), subcategory,
                    amountPaisa, timestamp, note == null ? "" : note.trim());
            for (int i = 0; i < oldArray.length(); i++) {
                JSONObject oldJson = oldArray.getJSONObject(i);
                if (transactionId.equals(oldJson.getString("id"))) {
                    matches++;
                    updatedArray.put(replacement.toJson());
                } else {
                    updatedArray.put(oldJson);
                }
            }
            if (matches != 1) return false;
            pending.put(saveOperation(replacement));
            boolean committed = preferences.edit()
                    .putString(key, updatedArray.toString())
                    .putString(PENDING_PREFIX + uid, pending.toString())
                    .commit();
            if (committed) syncPending(context);
            return committed;
        } catch (JSONException | ClassCastException error) {
            return false;
        }
    }

    /** Deletes exactly one local record and queues a cloud deletion tombstone. */
    public static synchronized boolean delete(Context context, String transactionId) {
        if (transactionId == null || transactionId.isEmpty()) return false;
        String uid = currentUid();
        String key = "user_" + uid;
        SharedPreferences preferences = prefs(context);
        try {
            JSONArray oldArray = readArray(preferences, key);
            JSONArray remaining = new JSONArray();
            JSONArray pending = readArray(preferences, PENDING_PREFIX + uid);
            int matches = 0;
            for (int i = 0; i < oldArray.length(); i++) {
                JSONObject oldJson = oldArray.getJSONObject(i);
                if (transactionId.equals(oldJson.getString("id"))) {
                    matches++;
                } else {
                    remaining.put(oldJson);
                }
            }
            if (matches != 1) return false;
            pending.put(deleteOperation(transactionId));
            boolean committed = preferences.edit()
                    .putString(key, remaining.toString())
                    .putString(PENDING_PREFIX + uid, pending.toString())
                    .commit();
            if (committed) syncPending(context);
            return committed;
        } catch (JSONException | ClassCastException error) {
            return false;
        }
    }

    private static JSONObject saveOperation(Record record) throws JSONException {
        return new JSONObject()
                .put("opId", UUID.randomUUID().toString())
                .put("type", OP_SAVE)
                .put("record", record.toJson());
    }

    private static JSONObject deleteOperation(String id) throws JSONException {
        return new JSONObject()
                .put("opId", UUID.randomUUID().toString())
                .put("type", OP_DELETE)
                .put("id", id);
    }

    /**
     * Queue existing local records once, so a user does not lose transactions
     * created before Firebase syncing was introduced. Same IDs are reused.
     */
    private static boolean queueLegacyRecords(SharedPreferences preferences, String uid)
            throws JSONException {
        String marker = MIGRATED_PREFIX + uid;
        if (preferences.getBoolean(marker, false)) return true;
        JSONArray records = readArray(preferences, "user_" + uid);
        JSONArray pending = readArray(preferences, PENDING_PREFIX + uid);
        Set<String> queuedSaveIds = new HashSet<>();
        for (int i = 0; i < pending.length(); i++) {
            JSONObject operation = pending.getJSONObject(i);
            if (OP_SAVE.equals(operation.getString("type"))) {
                queuedSaveIds.add(operation.getJSONObject("record").getString("id"));
            }
        }
        for (int i = 0; i < records.length(); i++) {
            Record record = Record.fromJson(records.getJSONObject(i));
            // Do not duplicate already queued saves; cloud IDs stay stable.
            if (!queuedSaveIds.contains(record.id)) {
                pending.put(saveOperation(record));
            }
        }
        return preferences.edit()
                .putString(PENDING_PREFIX + uid, pending.toString())
                .putBoolean(marker, true)
                .commit();
    }

    /**
     * Attempts pending operations in order. Call after login/app resume and
     * after every local mutation. Offline failures leave a durable retry queue.
     */
    public static synchronized void syncPending(Context context) {
        String uid = currentUid();
        if (IN_FLIGHT.containsKey(uid)) return;
        Context appContext = context.getApplicationContext();
        SharedPreferences preferences = prefs(appContext);
        try {
            if (!queueLegacyRecords(preferences, uid)) return;
            JSONArray pending = readArray(preferences, PENDING_PREFIX + uid);
            if (pending.length() == 0) return;
            JSONObject operation = pending.getJSONObject(0);
            String opId = operation.getString("opId");
            String type = operation.getString("type");
            IN_FLIGHT.put(uid, opId);
            FirebaseTransactionSync.Completion completion = (success, message) ->
                    completeUpload(appContext, uid, opId, success, message);
            if (OP_SAVE.equals(type)) {
                Record record = Record.fromJson(operation.getJSONObject("record"));
                FirebaseTransactionSync.save(record, completion);
            } else if (OP_DELETE.equals(type)) {
                FirebaseTransactionSync.delete(operation.getString("id"), completion);
            } else {
                IN_FLIGHT.remove(uid);
                preferences.edit().putString(ERROR_PREFIX + uid,
                        "Invalid pending operation").apply();
            }
        } catch (JSONException | ClassCastException | IllegalStateException error) {
            IN_FLIGHT.remove(uid);
            preferences.edit().putString(ERROR_PREFIX + uid,
                    "Unable to read pending transactions").apply();
        }
    }

    private static void completeUpload(Context appContext, String uid, String opId,
                                       boolean success, String message) {
        boolean advance = false;
        synchronized (TransactionStore.class) {
            if (!opId.equals(IN_FLIGHT.get(uid))) return;
            IN_FLIGHT.remove(uid);
            SharedPreferences preferences = prefs(appContext);
            if (!success) {
                preferences.edit().putString(ERROR_PREFIX + uid,
                        message == null ? "Cloud sync failed" : message).apply();
                return;
            }
            try {
                JSONArray pending = readArray(preferences, PENDING_PREFIX + uid);
                if (pending.length() == 0
                        || !opId.equals(pending.getJSONObject(0).getString("opId"))) {
                    preferences.edit().putString(ERROR_PREFIX + uid,
                            "Cloud queue changed during upload").apply();
                    return;
                }
                JSONArray remainder = new JSONArray();
                for (int i = 1; i < pending.length(); i++) {
                    remainder.put(pending.getJSONObject(i));
                }
                advance = preferences.edit()
                        .putString(PENDING_PREFIX + uid, remainder.toString())
                        .remove(ERROR_PREFIX + uid)
                        .commit();
            } catch (JSONException | ClassCastException error) {
                preferences.edit().putString(ERROR_PREFIX + uid,
                        "Cloud queue could not be updated").apply();
            }
        }
        // Do not upload to the previous user's path after switching accounts.
        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        if (advance && current != null && uid.equals(current.getUid())) {
            syncPending(appContext);
        }
    }

    /**
     * Non-destructively merges a Firebase snapshot into the signed-in user's local
     * records. A locally pending save/edit/delete always wins until acknowledged.
     *
     * Absence from a cloud snapshot is NOT treated as deletion: offline/cached
     * snapshots can be incomplete. Explicit remote tombstones are handled safely.
     *
     * Returns false on invalid cloud data or a local persistence failure; it never
     * commits a partially decoded cloud snapshot.
     */
    public static synchronized boolean mergeCloudSnapshot(
            Context context, List<Record> cloudRecords) {
        if (cloudRecords == null) return false;
        String uid = currentUid();
        SharedPreferences preferences = prefs(context);
        String localKey = "user_" + uid;
        try {
            // Preserve existing unsynced records before accepting cloud data.
            if (!queueLegacyRecords(preferences, uid)) return false;

            Set<String> pendingIds = new HashSet<>();
            JSONArray pending = readArray(preferences, PENDING_PREFIX + uid);
            for (int i = 0; i < pending.length(); i++) {
                JSONObject operation = pending.getJSONObject(i);
                String type = operation.getString("type");
                String id;
                if (OP_SAVE.equals(type)) {
                    id = operation.getJSONObject("record").getString("id");
                } else if (OP_DELETE.equals(type)) {
                    id = operation.getString("id");
                } else {
                    return false;
                }
                pendingIds.add(id);
            }

            // A map keyed by transaction ID ensures exactly one local copy.
            Map<String, Record> merged = new LinkedHashMap<>();
            JSONArray local = readArray(preferences, localKey);
            for (int i = 0; i < local.length(); i++) {
                Record record = Record.fromJson(local.getJSONObject(i));
                if (merged.put(record.id, record) != null) return false;
            }

            Set<String> cloudIds = new HashSet<>();
            for (Record cloud : cloudRecords) {
                if (cloud == null || cloud.id == null || cloud.id.isEmpty()
                        || !cloudIds.add(cloud.id)) return false;

                // An explicit cloud tombstone removes exactly one local record.
                // Pending local operations take priority until cloud acknowledges them.
                if (cloud.isRemoteDeletion) {
                    if (!pendingIds.contains(cloud.id)) {
                        merged.remove(cloud.id);
                    }
                    continue;
                }

                CategoryCatalog.Category canonical =
                        CategoryCatalog.findCategory(cloud.categoryId, cloud.isIncome);
                if (!validSelection(cloud.isIncome, canonical, cloud.subcategory,
                        cloud.amountPaisa, cloud.timestamp)) return false;

                // A local pending deletion must never be undone by a cached snapshot.
                // A local pending edit must not be overwritten by an older cloud value.
                if (!pendingIds.contains(cloud.id)) {
                    merged.put(cloud.id, new Record(cloud.id, cloud.isIncome,
                            canonical.getId(), canonical.getName(), cloud.subcategory,
                            cloud.amountPaisa, cloud.timestamp, cloud.note));
                }
            }

            // Never remove a local record just because a snapshot omits it.
            JSONArray updated = new JSONArray();
            for (Record record : merged.values()) {
                updated.put(record.toJson());
            }
            String json = updated.toString();
            if (json.equals(preferences.getString(localKey, "[]"))) return true;
            return preferences.edit().putString(localKey, json).commit();
        } catch (JSONException | ClassCastException error) {
            return false;
        }
    }

    /** Number of local changes not yet acknowledged by the Firebase server. */
    public static synchronized int getPendingSyncCount(Context context) {
        String uid = currentUid();
        try {
            return readArray(prefs(context), PENDING_PREFIX + uid).length();
        } catch (JSONException | ClassCastException error) {
            return -1;
        }
    }

    /** Null means no recorded sync error; zero pending means fully uploaded. */
    public static synchronized String getLastSyncError(Context context) {
        return prefs(context).getString(ERROR_PREFIX + currentUid(), null);
    }
}
