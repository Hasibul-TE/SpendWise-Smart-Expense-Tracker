package com.alard.spendwise;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends AppCompatActivity {

    private static final String PREF_NAME = "SpendWisePrefs";
    private static final String KEY_REMEMBER_ME = "remember_me";

    private TextInputEditText editEmail;
    private TextInputEditText editPassword;
    private MaterialCheckBox checkRememberMe;

    private FirebaseAuth firebaseAuth;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Shows the login screen
        setContentView(R.layout.activity_login);

        // Initializes Firebase Authentication
        firebaseAuth = FirebaseAuth.getInstance();

        // Initializes local preferences
        sharedPreferences = getSharedPreferences(
                PREF_NAME,
                MODE_PRIVATE
        );

        // Connects Java with the login form
        editEmail = findViewById(R.id.editEmail);
        editPassword = findViewById(R.id.editPassword);
        checkRememberMe = findViewById(R.id.checkRememberMe);

        MaterialButton btnLogin = findViewById(R.id.btnLogin);
        TextView txtRegister = findViewById(R.id.txtRegister);
        TextView txtForgotPassword = findViewById(R.id.txtForgotPassword);

        // Restores the saved Remember Me state
        boolean rememberMe = sharedPreferences.getBoolean(
                KEY_REMEMBER_ME,
                false
        );

        checkRememberMe.setChecked(rememberMe);

        // Validates and logs in the user
        btnLogin.setOnClickListener(view -> validateLogin());

        // Opens the registration screen
        txtRegister.setOnClickListener(view -> {
            Intent intent = new Intent(
                    LoginActivity.this,
                    RegisterActivity.class
            );

            startActivity(intent);
        });

        // Opens the forgot password screen
        txtForgotPassword.setOnClickListener(view -> {
            Intent intent = new Intent(
                    LoginActivity.this,
                    ForgotPasswordActivity.class
            );

            startActivity(intent);
        });
    }

    // Checks all login input fields
    private void validateLogin() {

        String email = editEmail.getText() != null
                ? editEmail.getText().toString().trim()
                : "";

        String password = editPassword.getText() != null
                ? editPassword.getText().toString().trim()
                : "";

        // Checks email
        if (email.isEmpty()) {
            editEmail.setError("Email is required");
            editEmail.requestFocus();
            return;
        }

        // Checks valid email format
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editEmail.setError("Enter a valid email");
            editEmail.requestFocus();
            return;
        }

        // Checks password
        if (password.isEmpty()) {
            editPassword.setError("Password is required");
            editPassword.requestFocus();
            return;
        }

        // Requires at least 6 characters
        if (password.length() < 6) {
            editPassword.setError("Password must be at least 6 characters");
            editPassword.requestFocus();
            return;
        }

        // Logs in with Firebase
        loginUser(email, password);
    }

    // Logs in the user with Firebase Authentication
    private void loginUser(String email, String password) {

        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {

                    if (task.isSuccessful()) {

                        // Saves the Remember Me choice
                        sharedPreferences.edit()
                                .putBoolean(
                                        KEY_REMEMBER_ME,
                                        checkRememberMe.isChecked()
                                )
                                .apply();

                        Toast.makeText(
                                LoginActivity.this,
                                "Login successful",
                                Toast.LENGTH_SHORT
                        ).show();

                        // Opens the main screen
                        Intent intent = new Intent(
                                LoginActivity.this,
                                MainActivity.class
                        );

                        startActivity(intent);
                        finish();

                    } else {

                        String errorMessage = task.getException() != null
                                ? task.getException().getMessage()
                                : "Login failed";

                        Toast.makeText(
                                LoginActivity.this,
                                errorMessage,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
    }
}