package com.alard.spendwise;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

/**
 * Read-only cloud-upload status with a manual retry action.
 *
 * This dialog does not claim that the entire cloud snapshot is downloaded.
 * TransactionStore keeps local data and the durable pending-upload queue.
 */
public final class SyncStatusDialog {
    private static final int BG = 0xFF071739;
    private static final int CARD = 0xFF102A56;
    private static final int BORDER = 0xFF1E3A5F;
    private static final int TEXT = 0xFFF8FAFC;
    private static final int MUTED = 0xFF94A3B8;
    private static final int BLUE = 0xFF3B82F6;
    private static final int GREEN = 0xFF22C55E;
    private static final int AMBER = 0xFFF59E0B;
    private static final int RED = 0xFFEF4444;

    private SyncStatusDialog() {
        // Utility class.
    }

    /** Call from a Profile or Settings click listener. */
    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, 20), dp(activity, 18),
                dp(activity, 20), dp(activity, 20));
        content.setBackground(rounded(BG, BLUE, activity, 18));

        TextView title = label(activity, activity.getString(R.string.sync_status_title),
                20, TEXT, true);
        content.addView(title);
        TextView subtitle = label(activity, activity.getString(R.string.sync_status_subtitle),
                12, MUTED, false);
        LinearLayout.LayoutParams subtitleParams = verticalGap(activity, 6);
        content.addView(subtitle, subtitleParams);

        LinearLayout statusCard = new LinearLayout(activity);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(dp(activity, 14), dp(activity, 14),
                dp(activity, 14), dp(activity, 14));
        statusCard.setBackground(rounded(CARD, BORDER, activity, 12));
        LinearLayout.LayoutParams cardParams = verticalGap(activity, 18);
        content.addView(statusCard, cardParams);

        TextView state = label(activity, activity.getString(R.string.sync_status_checking),
                16, TEXT, true);
        statusCard.addView(state);
        TextView details = label(activity, "", 13, MUTED, false);
        statusCard.addView(details, verticalGap(activity, 8));
        // Connection state is not a cloud-upload acknowledgement.
        TextView connection = label(activity,
                "Firebase connection: checking...", 12, MUTED, false);
        content.addView(connection, verticalGap(activity, 10));

        TextView explanation = label(activity,
                activity.getString(R.string.sync_status_explanation),
                11, MUTED, false);
        content.addView(explanation, verticalGap(activity, 12));

        LinearLayout controls = new LinearLayout(activity);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(controls, verticalGap(activity, 18));

        Button refresh = button(activity, activity.getString(R.string.sync_status_refresh), CARD);
        Button retry = button(activity, activity.getString(R.string.sync_status_retry), BLUE);
        controls.addView(refresh, new LinearLayout.LayoutParams(0, dp(activity, 48), 1));
        LinearLayout.LayoutParams retryParams =
                new LinearLayout.LayoutParams(0, dp(activity, 48), 1);
        retryParams.leftMargin = dp(activity, 10);
        controls.addView(retry, retryParams);

        Button close = button(activity, activity.getString(R.string.sync_status_close), CARD);
        content.addView(close, verticalGap(activity, 10));

        AlertDialog dialog = new AlertDialog.Builder(activity).setView(content).create();

        // Firebase Realtime Database publishes its own network connection state.
        // This only tells us if a socket is connected, not if writes succeeded.
        DatabaseReference possibleConnectionRef = null;
        try {
            possibleConnectionRef = FirebaseDatabase.getInstance(
                            "https://spendwise-279b5-default-rtdb.asia-southeast1.firebasedatabase.app"
                    )
                    .getReference(".info/connected");
        } catch (RuntimeException error) {
            connection.setText("Firebase connection: configuration unavailable");
            connection.setTextColor(RED);
        }
        final DatabaseReference connectionRef = possibleConnectionRef;
        final ValueEventListener connectionListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!dialog.isShowing()) return;
                boolean online = Boolean.TRUE.equals(snapshot.getValue(Boolean.class));
                String message = online
                        ? "Firebase connection: online"
                        : "Firebase connection: offline / connecting";
                connection.setText(message);
                connection.setTextColor(online ? GREEN : AMBER);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (!dialog.isShowing()) return;
                String message = "Firebase connection check failed: " + error.getMessage();
                connection.setText(message);
                connection.setTextColor(RED);
            }
        };

        // The upload queue changes *asynchronously*, so refresh its result while
        // the dialog is visible. Stop polling and detach listeners on dismiss.
        Handler uiHandler = new Handler(Looper.getMainLooper());
        final Runnable[] poller = new Runnable[1];
        poller[0] = () -> {
            if (!dialog.isShowing() || activity.isFinishing() || activity.isDestroyed()) return;
            refreshStatus(activity, state, details, retry);
            uiHandler.postDelayed(poller[0], 1200L);
        };

        refresh.setOnClickListener(view -> {
            refreshStatus(activity, state, details, retry);
            Toast.makeText(activity, "Status refreshed", Toast.LENGTH_SHORT).show();
        });

        retry.setOnClickListener(view -> {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                refreshStatus(activity, state, details, retry);
                return;
            }
            try {
                // Resume an explicitly-paused Firebase connection, if any.
                // Existing in-flight writes are retried by the Firebase SDK.
                FirebaseDatabase.getInstance(
                        "https://spendwise-279b5-default-rtdb.asia-southeast1.firebasedatabase.app"

                ).goOnline();
                TransactionStore.syncPending(activity.getApplicationContext());
                Toast.makeText(activity,
                        "Upload requested. Waiting for Firebase acknowledgement.",
                        Toast.LENGTH_LONG).show();
            } catch (RuntimeException error) {
                state.setText(R.string.sync_status_request_failed);
                state.setTextColor(RED);
                String message = "Retry could not start: " + error.getMessage();
                details.setText(message);
                return;
            }
            refreshStatus(activity, state, details, retry);
        });

        close.setOnClickListener(view -> dialog.dismiss());
        dialog.setOnShowListener(ignored -> {
            if (connectionRef != null) {
                connectionRef.addValueEventListener(connectionListener);
            }
            uiHandler.post(poller[0]);
        });
        dialog.setOnDismissListener(ignored -> {
            uiHandler.removeCallbacks(poller[0]);
            if (connectionRef != null) {
                connectionRef.removeEventListener(connectionListener);
            }
        });

        refreshStatus(activity, state, details, retry);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    private static void refreshStatus(Activity activity, TextView state,
                                      TextView details, Button retry) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            state.setText(R.string.sync_status_not_signed_in);
            state.setTextColor(AMBER);
            details.setText(R.string.sync_status_not_signed_in_details);
            retry.setEnabled(false);
            return;
        }
        retry.setEnabled(true);
        try {
            int pending = TransactionStore.getPendingSyncCount(activity);
            String error = TransactionStore.getLastSyncError(activity);
            if (pending < 0) {
                state.setText(R.string.sync_status_queue_unavailable);
                state.setTextColor(RED);
                details.setText(R.string.sync_status_queue_unavailable_details);
            } else if (error != null && !error.trim().isEmpty()) {
                state.setText(R.string.sync_status_upload_error);
                state.setTextColor(RED);
                details.setText(activity.getString(R.string.sync_status_upload_error_details,
                        pending, error));
            } else if (pending > 0) {
                state.setText(R.string.sync_status_uploads_pending);
                state.setTextColor(AMBER);
                details.setText(activity.getString(R.string.sync_status_uploads_pending_details,
                        pending));
            } else {
                state.setText(R.string.sync_status_no_pending);
                state.setTextColor(GREEN);
                details.setText(R.string.sync_status_no_pending_details);
                retry.setEnabled(false);
            }
        } catch (RuntimeException error) {
            state.setText(R.string.sync_status_unavailable);
            state.setTextColor(RED);
            details.setText(R.string.sync_status_unavailable_details);
        }
    }

    private static TextView label(Activity activity, String text, int sp,
                                  int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private static Button button(Activity activity, String text, int color) {
        Button button = new Button(activity);
        button.setText(text);
        button.setTextColor(TEXT);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setBackground(rounded(color, BORDER, activity, 10));
        return button;
    }

    private static GradientDrawable rounded(int background, int stroke,
                                            Activity activity, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(background);
        drawable.setCornerRadius(dp(activity, radiusDp));
        drawable.setStroke(dp(activity, 1), stroke);
        return drawable;
    }

    private static LinearLayout.LayoutParams verticalGap(Activity activity, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(activity, topDp);
        return params;
    }

    private static int dp(Activity activity, float value) {
        return Math.round(activity.getResources().getDisplayMetrics().density * value);
    }
}
