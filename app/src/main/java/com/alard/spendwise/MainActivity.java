package com.alard.spendwise;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {

    private static final String PREF_NAME = "SpendWisePrefs";
    private static final String KEY_REMEMBER_ME = "remember_me";

    private FirebaseAuth firebaseAuth;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Shows the temporary main screen
        setContentView(R.layout.activity_main);

        // Initializes Firebase Authentication
        firebaseAuth = FirebaseAuth.getInstance();

        // Loads local preferences
        sharedPreferences = getSharedPreferences(
                PREF_NAME,
                MODE_PRIVATE
        );

        // Connects Java with the logout button
        MaterialButton btnLogout = findViewById(R.id.btnLogout);

        // Logs out the current user
        btnLogout.setOnClickListener(view -> logoutUser());
    }

    // Clears login session and returns to LoginActivity
    private void logoutUser() {

        // Signs out from Firebase
        firebaseAuth.signOut();

        // Clears the Remember Me preference
        sharedPreferences.edit()
                .putBoolean(KEY_REMEMBER_ME, false)
                .apply();

        // Opens the login screen
        Intent intent = new Intent(
                MainActivity.this,
                LoginActivity.class
        );

        // Clears previous activities from the back stack
        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
    }
}