package com.alard.spendwise;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY = 1800;

    private static final String PREF_NAME = "SpendWisePrefs";
    private static final String KEY_REMEMBER_ME = "remember_me";

    private FirebaseAuth firebaseAuth;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Shows the splash screen
        setContentView(R.layout.activity_splash);

        // Initializes Firebase Authentication
        firebaseAuth = FirebaseAuth.getInstance();

        // Loads local preferences
        sharedPreferences = getSharedPreferences(
                PREF_NAME,
                MODE_PRIVATE
        );

        // Checks login state after the splash delay
        new Handler(Looper.getMainLooper()).postDelayed(
                this::checkLoginState,
                SPLASH_DELAY
        );
    }

    // Decides which screen should open
    private void checkLoginState() {

        boolean rememberMe = sharedPreferences.getBoolean(
                KEY_REMEMBER_ME,
                false
        );

        FirebaseUser currentUser = firebaseAuth.getCurrentUser();

        Intent intent;

        if (rememberMe && currentUser != null) {

            // Keeps the user logged in
            intent = new Intent(
                    SplashActivity.this,
                    MainActivity.class
            );

        } else {

            // Clears Firebase session when not remembered
            if (currentUser != null) {
                firebaseAuth.signOut();
            }

            intent = new Intent(
                    SplashActivity.this,
                    LoginActivity.class
            );
        }

        startActivity(intent);
        finish();
    }
}