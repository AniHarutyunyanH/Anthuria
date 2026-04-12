package com.inania.Anthuria;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.inania.Anthuria.databinding.ActivityRegisterBinding;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();

        // SIGN UP button
        binding.btnRegister.setOnClickListener(v -> registerUser());

        // Back to Login
        binding.backToLogin.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });

        // Close / Skip (anonymous continue) - adjust logic as needed
        binding.btnCloseAnonymous.setOnClickListener(v -> {
            // For example: go to MainActivity without auth
            startActivity(new Intent(RegisterActivity.this, MainActivity.class));
            finish();
        });
    }

    private void registerUser() {
        String email = binding.emailRegister.getText().toString().trim();
        String password = binding.passwordRegister.getText().toString().trim();
        String confirmPassword = binding.confirmPasswordRegister.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            binding.emailRegister.setError("Email is required");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            binding.passwordRegister.setError("Password is required");
            return;
        }
        if (password.length() < 6) {
            binding.passwordRegister.setError("Password must be at least 6 characters");
            return;
        }
        if (!password.equals(confirmPassword)) {
            binding.confirmPasswordRegister.setError("Passwords do not match");
            return;
        }

        // Optional: show loading (you can add ProgressBar later)
        binding.btnRegister.setEnabled(false);
        binding.btnRegister.setText("Signing up...");

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        binding.btnRegister.setEnabled(true);
                        binding.btnRegister.setText("SIGN UP");

                        if (task.isSuccessful()) {
                            Toast.makeText(RegisterActivity.this, "Registration successful!", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                            finish();
                        } else {
                            String errorMsg = task.getException() != null
                                    ? task.getException().getLocalizedMessage()
                                    : "Registration failed";
                            Toast.makeText(RegisterActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }
}