package com.alard.spendwise;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

import java.util.Locale;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Profile, photo, Cloud Sync status, share, phone intent and shake shortcut. */
public final class SettingsActivity extends AppCompatActivity implements SensorEventListener {

    public static final String PREFS_NAME = "SpendWisePrefs";
    public static final String KEY_SHAKE_TO_ADD = "shake_to_add";

    private static final int BG = 0xFF071739;
    private static final int CARD = 0xFF0F274F;
    private static final int CARD2 = 0xFF102A56;
    private static final int OUTLINE = 0xFF1E3A5F;
    private static final int BLUE = 0xFF3B82F6;
    private static final int GREEN = 0xFF22C55E;
    private static final int RED = 0xFFEF4444;
    private static final int WHITE = 0xFFF8FAFC;
    private static final int MUTED = 0xFF94A3B8;
    private static final int DIALOG_TEXT = 0xFF111827;
    private static final int DIALOG_HINT = 0xFF6B7280;

    private ImageView avatarView;
    private TextView avatarInitials;
    private TextView displayNameView;
    private TextView emailView;
    private SwitchCompat shakeToggle;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private long lastShakeElapsed;
    private boolean settingsVisible;
    private String signedInUid;

    // Camera preview is appropriate for a small circular profile image.
    private final ActivityResultLauncher<Void> takePhoto = registerForActivityResult(
            new ActivityResultContracts.TakePicturePreview(), bitmap -> {
                if (bitmap != null && isCurrentUser()) storeProfilePhoto(bitmap);
            });

    private final ActivityResultLauncher<String> choosePhoto = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null && isCurrentUser()) importProfilePhoto(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            returnToLogin();
            return;
        }
        signedInUid = user.getUid();
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager == null ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        buildScreen();
        refreshProfile();
        loadProfilePhoto();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        scroll.setClipToPadding(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(14), dp(18), dp(30));
        scroll.addView(content);
        setContentView(scroll);

        FrameLayout header = new FrameLayout(this);
        content.addView(header, new LinearLayout.LayoutParams(-1, dp(48)));
        TextView brand = text("◆ SpendWise", 21, WHITE, true);
        SpannableString colored = new SpannableString("◆ SpendWise");
        colored.setSpan(new ForegroundColorSpan(BLUE), 0, 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        colored.setSpan(new ForegroundColorSpan(BLUE), 7, colored.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        brand.setText(colored);
        brand.setGravity(Gravity.CENTER);
        header.addView(brand, new FrameLayout.LayoutParams(-1, -1));
        TextView back = text("‹", 36, WHITE, false);
        back.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(dp(46), -1);
        backParams.gravity = Gravity.START;
        header.addView(back, backParams);
        back.setContentDescription("Back to Dashboard");
        back.setOnClickListener(v -> finish());

        content.addView(text("Settings", 27, WHITE, true), topGap(16));
        content.addView(text("Profile, preferences and account tools", 13, MUTED, false),
                topGap(4));

        LinearLayout profile = card();
        profile.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(profile, topGap(18));

        FrameLayout photoFrame = new FrameLayout(this);
        LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(dp(92), dp(92));
        frameParams.bottomMargin = dp(10);
        profile.addView(photoFrame, frameParams);
        avatarView = new ImageView(this);
        avatarView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatarView.setBackground(shape(CARD2, BLUE, 50));
        avatarView.setClipToOutline(true);
        photoFrame.addView(avatarView, new FrameLayout.LayoutParams(-1, -1));
        avatarInitials = text("SW", 28, WHITE, true);
        avatarInitials.setGravity(Gravity.CENTER);
        photoFrame.addView(avatarInitials, new FrameLayout.LayoutParams(-1, -1));
        photoFrame.setContentDescription("Choose a profile photo");
        photoFrame.setOnClickListener(v -> showPhotoSource());

        displayNameView = text("SpendWise User", 19, WHITE, true);
        displayNameView.setGravity(Gravity.CENTER);
        profile.addView(displayNameView);
        emailView = text("", 13, MUTED, false);
        emailView.setGravity(Gravity.CENTER);
        profile.addView(emailView, topGap(3));

        LinearLayout photoButtons = horizontal();
        photoButtons.setGravity(Gravity.CENTER);
        profile.addView(photoButtons, topGap(18));
        Button camera = button("Camera", BLUE);
        Button gallery = button("Gallery", CARD2);
        photoButtons.addView(camera, weightedButton());
        LinearLayout.LayoutParams galleryParams = weightedButton();
        galleryParams.leftMargin = dp(10);
        photoButtons.addView(gallery, galleryParams);
        camera.setOnClickListener(v -> launchCamera());
        gallery.setOnClickListener(v -> launchGallery());

        Button editName = button("Edit Display Name", CARD2);
        profile.addView(editName, topGap(10));
        editName.setOnClickListener(v -> editDisplayName());

        sectionTitle(content, "PREFERENCES");
        LinearLayout preferences = card();
        content.addView(preferences);
        LinearLayout switchRow = horizontal();
        switchRow.setGravity(Gravity.CENTER_VERTICAL);
        preferences.addView(switchRow);
        LinearLayout switchLabels = vertical();
        switchRow.addView(switchLabels, new LinearLayout.LayoutParams(0, -2, 1));
        switchLabels.addView(text("Shake to Add Transaction", 15, WHITE, true));
        switchLabels.addView(text("Shortcut while Settings is open", 12, MUTED, false),
                topGap(4));
        shakeToggle = new SwitchCompat(this);
        shakeToggle.setContentDescription("Enable shake shortcut");
        shakeToggle.setThumbTintList(ColorStateList.valueOf(BLUE));
        shakeToggle.setChecked(prefs().getBoolean(KEY_SHAKE_TO_ADD, false));
        shakeToggle.setEnabled(accelerometer != null);
        switchRow.addView(shakeToggle);
        shakeToggle.setOnCheckedChangeListener((view, checked) -> {
            prefs().edit().putBoolean(KEY_SHAKE_TO_ADD, checked).apply();
            updateSensorRegistration();
        });
        if (accelerometer == null) {
            preferences.addView(text("Accelerometer unavailable on this device.",
                    11, MUTED, false), topGap(8));
        }
        line(preferences);
        preferences.addView(text("Currency", 15, WHITE, true));
        preferences.addView(text("Bangladeshi Taka (BDT · ৳)", 12, MUTED, false),
                topGap(5));

        sectionTitle(content, "DATA & ACCOUNT");
        LinearLayout account = card();
        content.addView(account);
        addAction(account, "Cloud Sync Status", "Check pending uploads and retry", BLUE,
                () -> SyncStatusDialog.show(this));
        line(account);
        addAction(account, "Remember Me", "Configured when you log in", BLUE,
                () -> Toast.makeText(this,
                        prefs().getBoolean("remember_me", false)
                                ? "Remember Me is enabled" : "Remember Me is disabled",
                        Toast.LENGTH_SHORT).show());

        sectionTitle(content, "TOOLS");
        LinearLayout tools = card();
        content.addView(tools);
        addAction(tools, "Share SpendWise", "Share the project link with others", GREEN,
                this::shareApp);
        line(tools);
        addAction(tools, "Project on GitHub", "Open repository in your browser", BLUE,
                this::openRepository);
        line(tools);
        addAction(tools, "Phone Dialer", "Enter a number to open the dialer", BLUE,
                this::askPhoneNumber);

        Button logout = button("Log Out", 0xFF5B2437);
        logout.setTextColor(0xFFFFD7DC);
        content.addView(logout, topGap(22));
        logout.setOnClickListener(v -> confirmLogout());
        content.addView(text("Photos are saved privately on this device. "
                        + "Cloud sync status only covers transaction uploads.",
                11, MUTED, false), topGap(12));
    }

    private void addAction(LinearLayout parent, String title, String description,
                           int accent, Runnable callback) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        parent.addView(row);
        TextView dot = text("●", 16, accent, true);
        row.addView(dot, new LinearLayout.LayoutParams(dp(29), -2));
        LinearLayout labels = vertical();
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        labels.addView(text(title, 15, WHITE, true));
        labels.addView(text(description, 12, MUTED, false), topGap(3));
        row.addView(text("›", 26, MUTED, false));
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(title);
        row.setOnClickListener(v -> callback.run());
    }

    private void showPhotoSource() {
        new AlertDialog.Builder(this)
                .setTitle("Profile photo")
                .setItems(new String[]{"Take photo", "Choose from gallery"},
                        (dialog, which) -> {
                            if (which == 0) launchCamera();
                            else launchGallery();
                        }).show();
    }

    private void launchCamera() {
        try {
            takePhoto.launch(null);
        } catch (ActivityNotFoundException | SecurityException error) {
            toast("No camera app is available.");
        }
    }

    private void launchGallery() {
        try {
            choosePhoto.launch("image/*");
        } catch (ActivityNotFoundException | SecurityException error) {
            toast("No photo picker is available.");
        }
    }

    private boolean isCurrentUser() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null && signedInUid != null && signedInUid.equals(user.getUid());
    }

    private File photoFile() {
        File directory = new File(getFilesDir(), "profile_photos");
        return new File(directory, signedInUid + ".jpg");
    }

    private void importProfilePhoto(Uri uri) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IOException("Photo cannot be opened");
                BitmapFactory.decodeStream(input, null, options);
            }
            if (options.outWidth <= 0 || options.outHeight <= 0) {
                throw new IOException("Unsupported image");
            }
            options.inSampleSize = 1;
            while (options.outWidth / options.inSampleSize > 1000
                    || options.outHeight / options.inSampleSize > 1000) {
                options.inSampleSize *= 2;
            }
            options.inJustDecodeBounds = false;
            Bitmap bitmap;
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IOException("Photo cannot be opened");
                bitmap = BitmapFactory.decodeStream(input, null, options);
            }
            if (bitmap == null) throw new IOException("Unsupported image");
            storeProfilePhoto(bitmap);
        } catch (IOException | RuntimeException error) {
            toast("Unable to import that photo.");
        }
    }

    private void storeProfilePhoto(Bitmap bitmap) {
        if (!isCurrentUser()) return;
        File destination = photoFile();
        File parent = destination.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            toast("Cannot create photo storage.");
            return;
        }
        Bitmap scaled = bitmap;
        int longest = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (longest > 700) {
            float ratio = 700f / longest;
            scaled = Bitmap.createScaledBitmap(bitmap,
                    Math.max(1, Math.round(bitmap.getWidth() * ratio)),
                    Math.max(1, Math.round(bitmap.getHeight() * ratio)), true);
        }
        File temporary = new File(parent, signedInUid + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            if (!scaled.compress(Bitmap.CompressFormat.JPEG, 88, output)) {
                throw new IOException("Image encoding failed");
            }
            output.flush();
            output.getFD().sync();
        } catch (IOException | RuntimeException error) {
            temporary.delete();
            toast("Could not save profile photo.");
            return;
        } finally {
            if (scaled != bitmap) scaled.recycle();
        }
        if (destination.exists() && !destination.delete()) {
            temporary.delete();
            toast("Could not replace profile photo.");
            return;
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete();
            toast("Could not finalize profile photo.");
            return;
        }
        loadProfilePhoto();
        toast("Profile photo updated.");
    }

    private void loadProfilePhoto() {
        if (avatarView == null || !isCurrentUser()) return;
        File imageFile = photoFile();
        Bitmap photo = imageFile.isFile()
                ? BitmapFactory.decodeFile(imageFile.getAbsolutePath()) : null;
        if (photo != null) {
            avatarView.setImageBitmap(photo);
            avatarInitials.setVisibility(View.GONE);
        } else {
            avatarView.setImageDrawable(null);
            avatarInitials.setVisibility(View.VISIBLE);
        }
    }

    private void refreshProfile() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String name = user.getDisplayName();
        if (name == null || name.trim().isEmpty()) name = "SpendWise User";
        displayNameView.setText(name);
        emailView.setText(user.getEmail() == null ? "" : user.getEmail());
        avatarInitials.setText(name.substring(0, 1).toUpperCase(Locale.getDefault()));
    }

    private void editDisplayName() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            toast("Please sign in first.");
            return;
        }
        EditText field = new EditText(this);
        field.setSingleLine(true);
        // The system AlertDialog is light; use dark, readable input text.
        field.setTextColor(DIALOG_TEXT);
        field.setHintTextColor(DIALOG_HINT);
        field.setBackgroundTintList(ColorStateList.valueOf(BLUE));
        field.setHint("Display name");
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        field.setText(user.getDisplayName() == null ? "" : user.getDisplayName());
        LinearLayout wrapper = vertical();
        wrapper.setPadding(dp(22), dp(4), dp(22), 0);
        wrapper.addView(field);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setCustomTitle(readableDialogTitle("Edit Display Name"))
                .setView(wrapper)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(BLUE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(BLUE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = field.getText().toString().trim();
                if (name.length() < 2 || name.length() > 60) {
                    field.setError("Use 2 to 60 characters");
                    return;
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                UserProfileChangeRequest request = new UserProfileChangeRequest.Builder()
                        .setDisplayName(name).build();
                user.updateProfile(request).addOnCompleteListener(task -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (task.isSuccessful()) {
                        refreshProfile();
                        dialog.dismiss();
                        toast("Display name updated.");
                    } else {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        toast("Unable to update name. Try again.");
                    }
                });
            });
        });
        dialog.show();
    }

    private void shareApp() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "SpendWise – Smart Expense Tracker");
        intent.putExtra(Intent.EXTRA_TEXT,
                "SpendWise – Smart Expense Tracker\n"
                        + "https://github.com/Hasibul-TE/SpendWise-Smart-Expense-Tracker");
        try {
            startActivity(Intent.createChooser(intent, "Share SpendWise"));
        } catch (ActivityNotFoundException error) {
            toast("No sharing app is available.");
        }
    }

    private void openRepository() {
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://github.com/Hasibul-TE/SpendWise-Smart-Expense-Tracker"));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException error) {
            toast("No browser is available.");
        }
    }

    private void askPhoneNumber() {
        EditText field = new EditText(this);
        field.setInputType(InputType.TYPE_CLASS_PHONE);
        field.setTextColor(DIALOG_TEXT);
        field.setHintTextColor(DIALOG_HINT);
        field.setBackgroundTintList(ColorStateList.valueOf(BLUE));
        field.setHint("Phone number");
        LinearLayout wrapper = vertical();
        wrapper.setPadding(dp(22), dp(4), dp(22), 0);
        wrapper.addView(field);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setCustomTitle(readableDialogTitle("Open Phone Dialer"))
                .setView(wrapper)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Dial", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(BLUE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(BLUE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String number = field.getText().toString().trim();
                if (!number.matches("\\+?[0-9][0-9 -]{3,20}")) {
                    field.setError("Enter a valid phone number");
                    return;
                }
                Intent intent = new Intent(Intent.ACTION_DIAL,
                        Uri.fromParts("tel", number.replace(" ", "").replace("-", ""), null));
                try {
                    startActivity(intent);
                    dialog.dismiss();
                } catch (ActivityNotFoundException error) {
                    toast("No phone dialer is available.");
                }
            });
        });
        dialog.show();
    }

    private TextView readableDialogTitle(String value) {
        TextView title = text(value, 20, DIALOG_TEXT, true);
        title.setPadding(dp(22), dp(20), dp(22), dp(8));
        return title;
    }

    private void confirmLogout() {
        int pending = 0;
        try {
            pending = TransactionStore.getPendingSyncCount(this);
        } catch (RuntimeException ignored) {
            // Do not block logout if sync status cannot be read.
        }
        String message = pending > 0
                ? pending + " local change(s) are awaiting cloud upload. "
                + "They will remain on this device and can sync when you log back "
                + "into this same account. Sign out anyway?"
                : "Do you want to sign out of SpendWise?";
        new AlertDialog.Builder(this)
                .setTitle("Log Out")
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Log Out", (dialog, which) -> {
                    prefs().edit().putBoolean("remember_me", false).apply();
                    FirebaseAuth.getInstance().signOut();
                    returnToLogin();
                }).show();
    }

    private void returnToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private void updateSensorRegistration() {
        if (sensorManager == null) return;
        sensorManager.unregisterListener(this);
        if (accelerometer != null && shakeToggle != null && shakeToggle.isChecked()
                && settingsVisible) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        settingsVisible = true;
        updateSensorRegistration();
    }

    @Override
    protected void onPause() {
        settingsVisible = false;
        if (sensorManager != null) sensorManager.unregisterListener(this);
        super.onPause();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER
                || shakeToggle == null || !shakeToggle.isChecked()) return;
        double x = event.values[0];
        double y = event.values[1];
        double z = event.values[2];
        double gForce = Math.sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH;
        long now = SystemClock.elapsedRealtime();
        if (gForce > 2.5 && now - lastShakeElapsed > 1800) {
            lastShakeElapsed = now;
            startActivity(new Intent(this, TransactionEntryActivity.class));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No calibration UI is required for a shake shortcut.
    }

    private LinearLayout card() {
        LinearLayout view = vertical();
        view.setPadding(dp(16), dp(17), dp(16), dp(17));
        view.setBackground(shape(CARD, OUTLINE, 16));
        return view;
    }

    private void sectionTitle(LinearLayout root, String title) {
        root.addView(text(title, 12, BLUE, true), topGap(22));
        root.addView(space(8));
    }

    private void line(LinearLayout parent) {
        View divider = new View(this);
        divider.setBackgroundColor(OUTLINE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1));
        params.topMargin = dp(14);
        params.bottomMargin = dp(14);
        parent.addView(divider, params);
    }

    private View space(int height) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(height)));
        return view;
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private Button button(String title, int background) {
        Button result = new Button(this);
        result.setText(title);
        result.setTextColor(WHITE);
        result.setTextSize(13);
        result.setAllCaps(false);
        result.setBackground(shape(background, OUTLINE, 12));
        result.setMinHeight(dp(48));
        return result;
    }

    private GradientDrawable shape(int background, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(background);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams topGap(int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(topDp);
        return params;
    }

    private LinearLayout.LayoutParams weightedButton() {
        return new LinearLayout.LayoutParams(0, dp(46), 1f);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
