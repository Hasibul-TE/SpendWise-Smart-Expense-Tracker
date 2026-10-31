package com.alard.spendwise;

import android.os.Bundle;
import android.util.Patterns;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;

public class ForgotPasswordActivity extends AppCompatActivity {

    private TextInputEditText editResetEmail;
    private FirebaseAuth firebaseAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Shows the forgot password screen
        setContentView(R.layout.activity_forgot_password);

        // Initializes Firebase Authentication
        firebaseAuth = FirebaseAuth.getInstance();

        // Connects Java with the screen
        editResetEmail = findViewById(R.id.editResetEmail);

        MaterialButton btnSendResetLink =
                findViewById(R.id.btnSendResetLink);

        TextView txtBackToLogin =
                findViewById(R.id.txtBackToLogin);

        // Sends the password reset email
        btnSendResetLink.setOnClickListener(
                view -> sendResetEmail()
        );

        // Returns to the login screen
        txtBackToLogin.setOnClickListener(
                view -> finish()
        );
    }

    // Validates email and sends the reset link
    private void sendResetEmail() {

        String email = editResetEmail.getText() != null
                ? editResetEmail.getText().toString().trim()
                : "";

        // Checks empty email
        if (email.isEmpty()) {
            editResetEmail.setError("Email is required");
            editResetEmail.requestFocus();
            return;
        }

        // Checks valid email format
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editResetEmail.setError("Enter a valid email");
            editResetEmail.requestFocus();
            return;
        }

        // Sends reset email using Firebase
        firebaseAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {

                    if (task.isSuccessful()) {

                        Toast.makeText(
                                ForgotPasswordActivity.this,
                                "Password reset email sent",
                                Toast.LENGTH_LONG
                        ).show();

                    } else {

                        String errorMessage = task.getException() != null
                                ? task.getException().getMessage()
                                : "Unable to send reset email";

                        Toast.makeText(
                                ForgotPasswordActivity.this,
                                errorMessage,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
    }
}