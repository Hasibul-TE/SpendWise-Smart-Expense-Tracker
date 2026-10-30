package com.alard.spendwise;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;


public class LoginActivity extends AppCompatActivity {

    private TextInputEditText editEmail;
    private TextInputEditText editPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Shows the login screen
        setContentView(R.layout.activity_login);

        // Connects Java with the login form
        editEmail = findViewById(R.id.editEmail);
        editPassword = findViewById(R.id.editPassword);

        MaterialButton btnLogin = findViewById(R.id.btnLogin);
        TextView txtRegister = findViewById(R.id.txtRegister);

        // Validates the login form
        btnLogin.setOnClickListener(view -> validateLogin());

        // Opens the registration screen
        txtRegister.setOnClickListener(view -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
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

        // Firebase requires at least 6 characters
        if (password.length() < 6) {
            editPassword.setError("Password must be at least 6 characters");
            editPassword.requestFocus();
            return;
        }

        // Firebase login will be added in Step 9
        Toast.makeText(
                LoginActivity.this,
                "Login form is valid",
                Toast.LENGTH_SHORT
        ).show();
    }
}