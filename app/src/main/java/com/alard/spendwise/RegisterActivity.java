package com.alard.spendwise;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;


public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText editFullName;
    private TextInputEditText editRegisterEmail;
    private TextInputEditText editRegisterPassword;
    private TextInputEditText editConfirmPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Shows the registration screen
        setContentView(R.layout.activity_register);

        // Connects Java with the registration form
        editFullName = findViewById(R.id.editFullName);
        editRegisterEmail = findViewById(R.id.editRegisterEmail);
        editRegisterPassword = findViewById(R.id.editRegisterPassword);
        editConfirmPassword = findViewById(R.id.editConfirmPassword);

        MaterialButton btnRegister = findViewById(R.id.btnRegister);
        TextView txtLogin = findViewById(R.id.txtLogin);

        // Validates the registration form
        btnRegister.setOnClickListener(view -> validateRegister());

        // Returns to the login screen
        txtLogin.setOnClickListener(view -> {
            Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
            startActivity(intent);

            // Closes the registration screen
            finish();
        });
    }

    // Checks all registration input fields
    private void validateRegister() {

        String fullName = editFullName.getText() != null
                ? editFullName.getText().toString().trim()
                : "";

        String email = editRegisterEmail.getText() != null
                ? editRegisterEmail.getText().toString().trim()
                : "";

        String password = editRegisterPassword.getText() != null
                ? editRegisterPassword.getText().toString().trim()
                : "";

        String confirmPassword = editConfirmPassword.getText() != null
                ? editConfirmPassword.getText().toString().trim()
                : "";

        // Checks full name
        if (fullName.isEmpty()) {
            editFullName.setError("Full name is required");
            editFullName.requestFocus();
            return;
        }

        // Checks email
        if (email.isEmpty()) {
            editRegisterEmail.setError("Email is required");
            editRegisterEmail.requestFocus();
            return;
        }

        // Checks valid email format
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editRegisterEmail.setError("Enter a valid email");
            editRegisterEmail.requestFocus();
            return;
        }

        // Checks password
        if (password.isEmpty()) {
            editRegisterPassword.setError("Password is required");
            editRegisterPassword.requestFocus();
            return;
        }

        // Requires at least 6 characters
        if (password.length() < 6) {
            editRegisterPassword.setError("Password must be at least 6 characters");
            editRegisterPassword.requestFocus();
            return;
        }

        // Checks confirm password
        if (confirmPassword.isEmpty()) {
            editConfirmPassword.setError("Confirm your password");
            editConfirmPassword.requestFocus();
            return;
        }

        // Checks whether both passwords match
        if (!password.equals(confirmPassword)) {
            editConfirmPassword.setError("Passwords do not match");
            editConfirmPassword.requestFocus();
            return;
        }

        // Firebase registration will be added in Step 9
        Toast.makeText(
                RegisterActivity.this,
                "Registration form is valid",
                Toast.LENGTH_SHORT
        ).show();
    }
}