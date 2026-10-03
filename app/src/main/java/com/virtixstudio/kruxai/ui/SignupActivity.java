package com.virtixstudio.kruxai.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.virtixstudio.kruxai.R;
import com.virtixstudio.kruxai.utils.LanguageManager;

import java.util.HashMap;
import java.util.Map;

public class SignupActivity extends AppCompatActivity {

    private EditText etFirstName;
    private EditText etEmail;
    private EditText etPassword;
    private EditText etRepeatPassword;

    private Button btnSignup;
    private TextView tvGoLogin;
    private TextView tvLanguage;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LanguageManager.applySavedLanguage(this);
        setContentView(R.layout.activity_signup);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        etFirstName = findViewById(R.id.etSignupFirstName);
        etEmail = findViewById(R.id.etSignupEmail);
        etPassword = findViewById(R.id.etSignupPassword);
        etRepeatPassword = findViewById(R.id.etSignupRepeatPassword);

        btnSignup = findViewById(R.id.btnSignup);
        tvGoLogin = findViewById(R.id.tvGoLogin);
        tvLanguage = findViewById(R.id.tvSignupLanguage);

        btnSignup.setOnClickListener(v -> handleSignup());

        tvGoLogin.setOnClickListener(v -> {
            finish();
        });

        tvLanguage.setOnClickListener(v ->
                LanguageManager.showLanguageDialog(this)
        );
    }

    private void handleSignup() {

        String firstName = etFirstName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();
        String repeatPassword = etRepeatPassword.getText().toString();

        if (firstName.isEmpty()) {
            showMessage("Veuillez entrer votre prénom.");
            return;
        }

        if (email.isEmpty()) {
            showMessage("Veuillez entrer votre adresse email.");
            return;
        }

        if (password.isEmpty()) {
            showMessage("Veuillez entrer un mot de passe.");
            return;
        }

        if (password.length() < 6) {
            showMessage("Le mot de passe doit contenir au moins 6 caractères.");
            return;
        }

        if (!password.equals(repeatPassword)) {
            showMessage("Les mots de passe ne correspondent pas.");
            return;
        }

        btnSignup.setEnabled(false);
        btnSignup.setText("Création...");

        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {

                    if (!task.isSuccessful()) {
                        btnSignup.setEnabled(true);
                        btnSignup.setText("S'inscrire");

                        if (task.getException() != null) {
                            showFirebaseError(task.getException());
                        } else {
                            showMessage("Impossible de créer le compte.");
                        }

                        return;
                    }

                    FirebaseUser user = auth.getCurrentUser();

                    if (user == null) {
                        btnSignup.setEnabled(true);
                        btnSignup.setText("S'inscrire");
                        showMessage("Le compte n'a pas pu être récupéré.");
                        return;
                    }

                    String uid = user.getUid();

                    Map<String, Object> profile = new HashMap<>();
                    profile.put("uid", uid);
                    profile.put("firstName", firstName);
                    profile.put("email", email);
                    profile.put("createdAt", FieldValue.serverTimestamp());

                    db.collection("users")
                            .document(uid)
                            .set(profile)
                            .addOnCompleteListener(profileTask -> {

                                btnSignup.setEnabled(true);
                                btnSignup.setText("S'inscrire");

                                if (profileTask.isSuccessful()) {
                                    showMessage("Compte créé avec succès.");
                                    navigateToMain();
                                } else {
                                    showMessage(
                                            "Compte créé, mais le profil n'a pas pu être enregistré."
                                    );
                                }
                            });
                });
    }

    private void navigateToMain() {
        Intent intent = new Intent(
                SignupActivity.this,
                MainActivity.class
        );
        startActivity(intent);
        finish();
    }

    private void showFirebaseError(Exception exception) {

        String message = exception.getMessage();

        if (message != null
                && message.toLowerCase().contains("email address is already in use")) {
            showMessage("Cet e-mail est déjà utilisé.");
            return;
        }

        showMessage("Impossible de créer le compte.");
    }

    private void showMessage(String message) {
        android.widget.Toast.makeText(
                this,
                message,
                android.widget.Toast.LENGTH_SHORT
        ).show();
    }
}
