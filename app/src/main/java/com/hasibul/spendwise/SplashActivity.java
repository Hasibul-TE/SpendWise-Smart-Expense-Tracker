package com.hasibul.spendwise;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    // Keeps the splash screen visible for 1.8 seconds
    private static final long SPLASH_DELAY = 1800;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Shows the splash screen layout
        setContentView(R.layout.activity_splash);

        // Opens the login screen after a short delay
        new Handler(Looper.getMainLooper()).postDelayed(() -> {

            // Opens LoginActivity
            Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
            startActivity(intent);

            // Prevents returning to the splash screen
            finish();

        }, SPLASH_DELAY);
    }
}