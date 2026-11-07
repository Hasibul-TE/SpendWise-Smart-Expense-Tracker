
package com.alard.spendwise;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SessionRouterActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY = 1800L;
    private static final String PREF_NAME = "SpendWisePrefs";
    private static final String KEY_REMEMBER_ME = "remember_me";

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private final Runnable routeRunnable = this::checkLoginState;

    private FirebaseAuth firebaseAuth;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // Install the AndroidX splash screen first.
        SplashScreen splashScreen =
                SplashScreen.installSplashScreen(this);

        super.onCreate(savedInstanceState);

        // Keep the system splash until navigation finishes.
        splashScreen.setKeepOnScreenCondition(() -> true);

        // No setContentView: avoid a second custom splash.

        firebaseAuth = FirebaseAuth.getInstance();

        sharedPreferences = getSharedPreferences(
                PREF_NAME,
                MODE_PRIVATE
        );

        // Preserve the existing 1800 ms delay.
        mainHandler.postDelayed(routeRunnable, SPLASH_DELAY);
    }

    private void checkLoginState() {

        if (isFinishing() || isDestroyed()) {
            return;
        }

        boolean rememberMe = sharedPreferences.getBoolean(
                KEY_REMEMBER_ME,
                false
        );

        FirebaseUser currentUser = firebaseAuth.getCurrentUser();

        Intent intent;

        if (rememberMe && currentUser != null) {

            // Open Dashboard for remembered users.
            intent = new Intent(this, MainActivity.class);

        } else {

            // Clear a session that was not remembered.
            if (currentUser != null) {
                firebaseAuth.signOut();
            }

            intent = new Intent(this, LoginActivity.class);
        }

        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {

        // Avoid delayed navigation after Activity destruction.
        mainHandler.removeCallbacks(routeRunnable);

        super.onDestroy();
    }
}
