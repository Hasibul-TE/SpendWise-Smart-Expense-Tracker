package com.alard.spendwise;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Toast;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.File;
import java.util.List;

/** Dashboard navigation, all-time balance refresh, and signed-in realtime sync. */
public final class MainActivity extends AppCompatActivity {

    private static final String TAG = "SpendWiseDashboard";
    private static final String PREFS_NAME = "SpendWisePrefs";
    private static final String REMEMBER_KEY = "remember_me";

    private static final int MENU_PROFILE = 1;
    private static final int MENU_SYNC = 2;
    private static final int MENU_LOGOUT = 3;

    private FirebaseTransactionSync.Subscription realtimeSubscription;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            returnToLogin();
            return;
        }

        setContentView(R.layout.activity_main);
        configureNavigation();
    }

    /** Keep the locked Dashboard layout and connect every existing navigation item. */
    private void configureNavigation() {
        setClick(R.id.btnAddTransaction,
                v -> openActivity(TransactionEntryActivity.class));

        // Full history is reachable only from the bottom Transactions tab.
        setClick(R.id.navTransactions,
                v -> openActivity(TransactionHistoryActivity.class));
        // Recent Transactions -> View All is handled inline by DashboardBindings.

        setClick(R.id.btnAllIncomeSources, v -> openBreakdown(true));
        setClick(R.id.btnAllExpenseCategories, v -> openBreakdown(false));

        setClick(R.id.navReports, v -> openActivity(ReportsActivity.class));
        setClick(R.id.navSettings, v -> openActivity(SettingsActivity.class));

        // The profile menu contains Settings, sync status and Log Out.
        setClick(R.id.btnProfile, this::showProfileMenu);

        // Keep the hidden legacy button functional if the XML shows it later.
        setClick(R.id.btnLogout, v -> confirmLogout());

        // Show useful cloud-sync alerts without altering the bell's design.
        setClick(R.id.btnNotifications, v -> showSyncAlerts());

        // Home is already active; return the page to its top.
        setClick(R.id.navHome, v -> {
            ScrollView scroll = findViewById(R.id.dashboardScroll);
            if (scroll != null) scroll.smoothScrollTo(0, 0);
        });
    }

    private void setClick(int viewId, View.OnClickListener listener) {
        View view = findViewById(viewId);
        if (view != null) view.setOnClickListener(listener);
        else Log.w(TAG, "Dashboard view not found: " + viewId);
    }

    private void openActivity(Class<?> destination) {
        startActivity(new Intent(this, destination));
    }

    /** Pass the synchronized dashboard calendar period to either breakdown. */
    private void openBreakdown(boolean income) {
        Intent intent = new Intent(this, BreakdownActivity.class);
        intent.putExtra(BreakdownActivity.EXTRA_IS_INCOME, income);
        intent.putExtra(BreakdownActivity.EXTRA_PERIOD,
                DashboardBindings.getSelectedPeriod(this).name());
        startActivity(intent);
    }

    private void showProfileMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        Menu menu = popup.getMenu();
        menu.add(Menu.NONE, MENU_PROFILE, 0, "Profile & Settings");
        menu.add(Menu.NONE, MENU_SYNC, 1, "Cloud Sync Status");
        menu.add(Menu.NONE, MENU_LOGOUT, 2, "Log Out");
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == MENU_PROFILE) {
                openActivity(SettingsActivity.class);
            } else if (id == MENU_SYNC) {
                SyncStatusDialog.show(this);
            } else if (id == MENU_LOGOUT) {
                confirmLogout();
            } else {
                return false;
            }
            return true;
        });
        popup.show();
    }

    private void showSyncAlerts() {
        final int pending;
        final String lastError;
        try {
            pending = TransactionStore.getPendingSyncCount(this);
            lastError = TransactionStore.getLastSyncError(this);
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to read sync status", exception);
            Toast.makeText(this, "Sync status unavailable", Toast.LENGTH_SHORT).show();
            return;
        }
        String message = pending == 0
                ? "No local uploads are pending."
                : pending + " transaction change(s) waiting for cloud upload.";
        if (lastError != null && !lastError.trim().isEmpty()) {
            message += "\n\nLast sync issue: " + lastError;
        }
        new AlertDialog.Builder(this)
                .setTitle("Sync Notifications")
                .setMessage(message)
                .setNegativeButton("Close", null)
                .setPositiveButton("Sync Status", (dialog, which) -> SyncStatusDialog.show(this))
                .show();
    }

    private void confirmLogout() {
        int pending = 0;
        try {
            pending = TransactionStore.getPendingSyncCount(this);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to check pending uploads", exception);
        }
        String message = pending > 0
                ? pending + " local change(s) have not uploaded yet. "
                + "They remain on this device for this account. Log out anyway?"
                : "Do you want to log out of SpendWise?";
        new AlertDialog.Builder(this)
                .setTitle("Log Out")
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Log Out", (dialog, which) -> signOut())
                .show();
    }

    private void signOut() {
        // Never keep an old user's listener active after account switching.
        stopRealtimeListener();
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        preferences.edit().putBoolean(REMEMBER_KEY, false).apply();
        FirebaseAuth.getInstance().signOut();
        returnToLogin();
    }

    private void returnToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        final String expectedUid = user.getUid();
        try {
            // Queue upload is asynchronous; local changes remain if upload fails.
            TransactionStore.syncPending(this);
            if (realtimeSubscription == null) {
                realtimeSubscription = FirebaseTransactionSync.observe(
                        new FirebaseTransactionSync.RecordsListener() {
                            @Override
                            public void onRecords(List<TransactionStore.Record> records) {
                                runOnUiThread(() -> {
                                    if (isFinishing() || isDestroyed()) return;
                                    FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
                                    if (current == null || !expectedUid.equals(current.getUid())) return;
                                    try {
                                        boolean success = TransactionStore.mergeCloudSnapshot(
                                                MainActivity.this, records);
                                        if (success) DashboardBindings.bind(MainActivity.this);
                                        else Log.w(TAG, "Cloud snapshot could not be merged");
                                    } catch (RuntimeException error) {
                                        Log.e(TAG, "Cloud merge failed", error);
                                    }
                                });
                            }

                            @Override
                            public void onError(String message) {
                                Log.e(TAG, "Realtime sync error: " + message);
                            }
                        });
            }
        } catch (RuntimeException exception) {
            Log.e(TAG, "Could not start realtime sync", exception);
        }
    }


    @Override
    protected void onResume() {
        super.onResume();

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            // Refresh after returning from other screens.
            DashboardBindings.bind(this);

            // Update profile picture.
            updateDashboardAvatar();

            // Update display name and financial overview.
            updateDashboardSubtitle();
        }
    }


    /**
     * The Settings screen saves a profile photo privately as
     * files/profile_photos/<Firebase UID>.jpg. Read that same file whenever
     * Dashboard resumes so changed photos appear without an app restart.
     * The clickable btnProfile TextView and its PopupMenu stay unchanged.
     */
    private void updateDashboardAvatar() {
        TextView profile = findViewById(R.id.btnProfile);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (profile == null || user == null) return;

        int diameter = Math.max(48, Math.round(
                44f * getResources().getDisplayMetrics().density));
        Bitmap icon = Bitmap.createBitmap(diameter, diameter, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(icon);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        float cx = diameter * 0.5f;
        float radius = diameter * 0.47f;
        paint.setColor(Color.rgb(16, 42, 86));
        canvas.drawCircle(cx, cx, radius, paint);

        File photoFile = new File(new File(getFilesDir(), "profile_photos"),
                user.getUid() + ".jpg");
        Bitmap photo = photoFile.isFile()
                ? BitmapFactory.decodeFile(photoFile.getAbsolutePath()) : null;
        if (photo != null) {
            BitmapShader shader = new BitmapShader(photo,
                    Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            float scale = Math.max((diameter - 4f) / photo.getWidth(),
                    (diameter - 4f) / photo.getHeight());
            Matrix matrix = new Matrix();
            matrix.setScale(scale, scale);
            matrix.postTranslate((diameter - photo.getWidth() * scale) / 2f,
                    (diameter - photo.getHeight() * scale) / 2f);
            shader.setLocalMatrix(matrix);
            paint.setShader(shader);
            canvas.drawCircle(cx, cx, diameter * 0.44f, paint);
            paint.setShader(null);
            photo.recycle();
        } else {
            // A recognizable person silhouette, replacing the old dot.
            paint.setColor(Color.WHITE);
            canvas.drawCircle(cx, diameter * 0.37f, diameter * 0.115f, paint);
            RectF shoulders = new RectF(diameter * 0.28f, diameter * 0.51f,
                    diameter * 0.72f, diameter * 0.75f);
            canvas.drawRoundRect(shoulders, diameter * 0.13f,
                    diameter * 0.13f, paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, diameter * 0.035f));
        paint.setColor(Color.rgb(59, 130, 246));
        canvas.drawCircle(cx, cx, radius, paint);

        profile.setText("");
        profile.setBackground(new BitmapDrawable(getResources(), icon));
        profile.setContentDescription("Profile & Settings");
    }

    @Override
    protected void onStop() {
        stopRealtimeListener();
        super.onStop();
    }

    private void stopRealtimeListener() {
        if (realtimeSubscription != null) {
            realtimeSubscription.close();
            realtimeSubscription = null;
        }
    }


    private void updateDashboardSubtitle() {
        TextView subtitle = findViewById(R.id.txtDashboardSubtitle);

        if (subtitle == null) {
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        String name = user != null ? user.getDisplayName() : null;

        if (name == null || name.trim().isEmpty()) {
            subtitle.setText(R.string.dashboard_subtitle);
        } else {
            subtitle.setText(
                    getString(
                            R.string.dashboard_subtitle_with_name,
                            name.trim()
                    )
            );
        }
    }

}
