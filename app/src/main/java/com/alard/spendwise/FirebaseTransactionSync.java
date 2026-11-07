package com.alard.spendwise;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Firebase Realtime Database bridge for the signed-in user's transactions.
 *
 * A normal write replaces exactly one transaction by ID. A delete writes a
 * persistent tombstone instead of removing the node, so offline devices can
 * recognize deletions when they reconnect. TransactionStore owns local state.
 */
public final class FirebaseTransactionSync {

    private FirebaseTransactionSync() {
        // Utility class.
    }

    public interface Completion {
        void onComplete(boolean success, String message);
    }

    public interface RecordsListener {
        void onRecords(List<TransactionStore.Record> records);
        void onError(String message);
    }

    /** Remove the Firebase listener when the screen stops or the user signs out. */
    public static final class Subscription {
        private final DatabaseReference reference;
        private final ValueEventListener listener;
        private boolean closed;

        private Subscription(DatabaseReference reference, ValueEventListener listener) {
            this.reference = reference;
            this.listener = listener;
        }

        public synchronized void close() {
            if (closed) return;
            closed = true;
            reference.removeEventListener(listener);
        }

        private synchronized boolean isClosed() {
            return closed;
        }
    }


    private static DatabaseReference transactionsFor(String uid) {
        // Use the correct Asia Southeast 1 database.
        return FirebaseDatabase.getInstance(
                        "https://spendwise-279b5-default-rtdb.asia-southeast1.firebasedatabase.app"
                ).getReference()
                .child("users")
                .child(uid)
                .child("transactions");
    }


    private static String signedInUid() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getUid() == null || user.getUid().isEmpty()) {
            throw new IllegalStateException("Please sign in before syncing transactions.");
        }
        return user.getUid();
    }

    private static boolean validId(String id) {
        // Same ID pattern used by TransactionStore's deletion marker.
        return id != null && id.matches("[A-Za-z0-9_-]+");
    }

    /** Validate one and only one category/subcategory for every normal record. */
    private static boolean validRecord(TransactionStore.Record record) {
        if (record == null || record.isRemoteDeletion || !validId(record.id)
                || record.amountPaisa <= 0 || record.timestamp <= 0
                || record.timestamp > System.currentTimeMillis()) {
            return false;
        }
        CategoryCatalog.Category category =
                CategoryCatalog.findCategory(record.categoryId, record.isIncome);
        return category != null
                && category.getSubcategories().contains(record.subcategory);
    }

    private static Map<String, Object> asFirebaseMap(TransactionStore.Record record) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", record.id);
        data.put("type", record.isIncome ? "income" : "expense");
        data.put("categoryId", record.categoryId);
        data.put("categoryName", record.categoryName);
        data.put("subcategory", record.subcategory);
        data.put("amountPaisa", record.amountPaisa);
        data.put("timestamp", record.timestamp);
        data.put("note", record.note == null ? "" : record.note);
        return data;
    }

    /** Save or edit by stable ID; call back only after Firebase reports completion. */
    public static void save(TransactionStore.Record record, Completion callback) {
        if (!validRecord(record)) {
            deliver(callback, false, "Invalid transaction or category selection.");
            return;
        }
        try {
            transactionsFor(signedInUid()).child(record.id)
                    .setValue(asFirebaseMap(record))
                    .addOnCompleteListener(task -> deliver(callback,
                            task.isSuccessful(), task.isSuccessful()
                                    ? "Transaction synced."
                                    : errorMessage(task.getException())));
        } catch (RuntimeException error) {
            deliver(callback, false, errorMessage(error));
        }
    }

    /**
     * Persist a deletion marker at the same transaction ID. Never removeValue:
     * an absent node cannot reliably tell another offline device what to erase.
     */
    public static void delete(String transactionId, Completion callback) {
        if (!validId(transactionId)) {
            deliver(callback, false, "Invalid transaction ID.");
            return;
        }
        try {
            Map<String, Object> tombstone = new HashMap<>();
            tombstone.put("id", transactionId);
            tombstone.put("deleted", true);
            tombstone.put("deletedAt", ServerValue.TIMESTAMP);
            transactionsFor(signedInUid()).child(transactionId)
                    .setValue(tombstone)
                    .addOnCompleteListener(task -> deliver(callback,
                            task.isSuccessful(), task.isSuccessful()
                                    ? "Transaction deletion synced."
                                    : errorMessage(task.getException())));
        } catch (RuntimeException error) {
            deliver(callback, false, errorMessage(error));
        }
    }

    /**
     * Observe all normal records and tombstones for the signed-in user. The
     * existing TransactionStore.mergeCloudSnapshot() resolves pending local
     * operations and removes tombstoned IDs without duplicate transactions.
     */
    public static Subscription observe(RecordsListener callback) {
        if (callback == null) {
            throw new IllegalArgumentException("RecordsListener is required.");
        }
        final String uid = signedInUid();
        final DatabaseReference reference = transactionsFor(uid);
        final Subscription[] holder = new Subscription[1];
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
                if (current == null || !uid.equals(current.getUid())
                        || (holder[0] != null && holder[0].isClosed())) return;
                try {
                    List<TransactionStore.Record> records = new ArrayList<>();
                    for (DataSnapshot item : snapshot.getChildren()) {
                        records.add(decode(item));
                    }
                    records.sort(Comparator.comparingLong(
                            (TransactionStore.Record r) -> r.timestamp).reversed());
                    if (holder[0] == null || !holder[0].isClosed()) {
                        callback.onRecords(records);
                    }
                } catch (RuntimeException error) {
                    // Reject the whole snapshot; never merge a partial result.
                    callback.onError("Invalid cloud snapshot: " + errorMessage(error));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (holder[0] == null || !holder[0].isClosed()) {
                    callback.onError(error.getMessage());
                }
            }
        };
        Subscription subscription = new Subscription(reference, listener);
        holder[0] = subscription;
        reference.addValueEventListener(listener);
        return subscription;
    }

    private static TransactionStore.Record decode(DataSnapshot snapshot) {
        String id = requiredText(snapshot, "id");
        if (!validId(id) || !id.equals(snapshot.getKey())) {
            throw new IllegalArgumentException("Transaction key/ID mismatch.");
        }

        // Tombstones are not Income/Expense records and must never be counted.
        Object deleted = snapshot.child("deleted").getValue();
        if (Boolean.TRUE.equals(deleted)) {
            return TransactionStore.Record.remoteDeletion(id);
        }
        if (deleted != null && !Boolean.FALSE.equals(deleted)) {
            throw new IllegalArgumentException("Invalid deletion marker.");
        }

        String type = requiredText(snapshot, "type");
        if (!"income".equals(type) && !"expense".equals(type)) {
            throw new IllegalArgumentException("Invalid transaction type.");
        }
        boolean isIncome = "income".equals(type);
        String categoryId = requiredText(snapshot, "categoryId");
        CategoryCatalog.Category category =
                CategoryCatalog.findCategory(categoryId, isIncome);
        String subcategory = requiredText(snapshot, "subcategory");
        if (category == null || !category.getSubcategories().contains(subcategory)) {
            throw new IllegalArgumentException("Unknown category/subcategory.");
        }

        Object rawAmount = snapshot.child("amountPaisa").getValue();
        Object rawTimestamp = snapshot.child("timestamp").getValue();
        if (!(rawAmount instanceof Number) || !(rawTimestamp instanceof Number)) {
            throw new IllegalArgumentException("Invalid amount or date.");
        }
        Object rawNote = snapshot.child("note").getValue();
        if (rawNote != null && !(rawNote instanceof String)) {
            throw new IllegalArgumentException("Invalid transaction note.");
        }
        TransactionStore.Record record = new TransactionStore.Record(
                id, isIncome, categoryId, category.getName(), subcategory,
                ((Number) rawAmount).longValue(), ((Number) rawTimestamp).longValue(),
                rawNote == null ? "" : (String) rawNote);
        if (!validRecord(record)) {
            throw new IllegalArgumentException("Invalid saved transaction.");
        }
        return record;
    }

    private static String requiredText(DataSnapshot snapshot, String key) {
        Object value = snapshot.child(key).getValue();
        if (!(value instanceof String) || ((String) value).isEmpty()) {
            throw new IllegalArgumentException("Missing field: " + key);
        }
        return (String) value;
    }

    private static void deliver(Completion callback, boolean success, String message) {
        if (callback != null) callback.onComplete(success, message);
    }

    private static String errorMessage(Exception error) {
        if (error == null || error.getMessage() == null || error.getMessage().isEmpty()) {
            return "Cloud operation failed.";
        }
        return error.getMessage();
    }
}
