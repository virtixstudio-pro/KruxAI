package com.virtixstudio.kruxai.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.virtixstudio.kruxai.R;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private EditText etFirstName, etEmail, etPassword, etRepeatPassword;
    private Button btnSubmit;
    private TextView tvTitle, tvToggleMode;

    private boolean isSignUpMode = false;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        etFirstName = findViewById(R.id.etFirstName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etRepeatPassword = findViewById(R.id.etRepeatPassword);
        btnSubmit = findViewById(R.id.btnSubmit);
        tvTitle = findViewById(R.id.tvTitle);
        tvToggleMode = findViewById(R.id.tvToggleMode);

        tvToggleMode.setOnClickListener(v -> toggleMode());
        btnSubmit.setOnClickListener(v -> handleSubmit());
    }

    private void toggleMode() {
        isSignUpMode = !isSignUpMode;
        if (isSignUpMode) {
            tvTitle.setText("Inscription");
            etFirstName.setVisibility(View.VISIBLE);
            etRepeatPassword.setVisibility(View.VISIBLE);
            btnSubmit.setText("S'inscrire");
            tvToggleMode.setText("Déjà un compte ? Se connecter");
        } else {
            tvTitle.setText("Connexion");
            etFirstName.setVisibility(View.GONE);
            etRepeatPassword.setVisibility(View.GONE);
            btnSubmit.setText("Se connecter");
            tvToggleMode.setText("Pas encore de compte ? S'inscrire");
        }
    }

    private void handleSubmit() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Veuillez remplir tous les champs obligatoires.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isSignUpMode) {
            String firstName = etFirstName.getText().toString().trim();
            String repeatPass = etRepeatPassword.getText().toString().trim();

            if (firstName.isEmpty()) {
                Toast.makeText(this, "Veuillez entrer votre prénom.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!password.equals(repeatPass)) {
                Toast.makeText(this, "Les mots de passe ne correspondent pas.", Toast.LENGTH_SHORT).show();
                return;
            }

            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = auth.getCurrentUser().getUid();
                    Map<String, Object> user = new HashMap<>();
                    user.put("firstName", firstName);
                    user.put("email", email);

                    db.collection("users").document(uid).set(user)
                        .addOnSuccessListener(aVoid -> navigateToMain())
                        .addOnFailureListener(e -> Toast.makeText(LoginActivity.this, "Erreur enregistrement profil.", Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e -> Toast.makeText(LoginActivity.this, "Échec Inscription: " + e.getMessage(), Toast.LENGTH_SHORT).show());

        } else {
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> navigateToMain())
                .addOnFailureListener(e -> Toast.makeText(LoginActivity.this, "Échec Connexion: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }

    private void navigateToMain() {
        startActivity(new Intent(LoginActivity.this, MainActivity.class));
        finish();
    }
}
