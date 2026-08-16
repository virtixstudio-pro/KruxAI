package com.virtixstudio.kruxai.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
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
            showMessage("Veuillez remplir tous les champs obligatoires.");
            return;
        }

        if (isSignUpMode) {
            handleSignUp(email, password);
        } else {
            handleLogin(email, password);
        }
    }

    private void handleSignUp(String email, String password) {
        String firstName = etFirstName.getText().toString().trim();
        String repeatPassword = etRepeatPassword.getText().toString().trim();

        if (firstName.isEmpty()) {
            showMessage("Veuillez entrer votre prénom.");
            return;
        }

        if (!password.equals(repeatPassword)) {
            showMessage("Les mots de passe ne correspondent pas.");
            return;
        }

        setLoading(true);

        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {

                    FirebaseUser user = authResult.getUser();

                    if (user == null) {
                        setLoading(false);
                        showMessage("Impossible de récupérer le compte créé.");
                        return;
                    }

                    String uid = user.getUid();

                    Map<String, Object> profile = new HashMap<>();
                    profile.put("firstName", firstName);
                    profile.put("email", email);
                    profile.put("createdAt",
                            com.google.firebase.firestore.FieldValue.serverTimestamp());

                    db.collection("users")
                            .document(uid)
                            .set(profile)
                            .addOnSuccessListener(unused -> {
                                setLoading(false);
                                showAccountCreated(firstName);
                            })
                            .addOnFailureListener(error -> {
                                setLoading(false);
                                showMessage(
                                        "Compte créé, mais le profil n'a pas pu être enregistré."
                                );
                            });
                })
                .addOnFailureListener(error -> {
                    setLoading(false);
                    showFirebaseError(error);
                });
    }

    private void handleLogin(String email, String password) {
        setLoading(true);

        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    setLoading(false);
                    navigateToMain();
                })
                .addOnFailureListener(error -> {
                    setLoading(false);
                    showFirebaseError(error);
                });
    }

    private void showAccountCreated(String firstName) {
        final android.app.Dialog dialog = new android.app.Dialog(this);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER_HORIZONTAL);
        container.setPadding(48, 42, 48, 36);

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(22, 22, 27));
        background.setCornerRadius(32);
        container.setBackground(background);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_success);
        icon.setContentDescription("Compte créé avec succès");

        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(88, 88);
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        container.addView(icon, iconParams);

        TextView title = new TextView(this);
        title.setText("Compte créé avec succès");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
        titleParams.topMargin = 22;
        container.addView(title, titleParams);

        TextView message = new TextView(this);
        message.setText(
                "Bienvenue " + firstName + ".\n" +
                "Ton compte KRUX est prêt."
        );
        message.setTextColor(Color.rgb(190, 190, 198));
        message.setTextSize(15);
        message.setGravity(Gravity.CENTER);
        message.setLineSpacing(0, 1.15f);

        LinearLayout.LayoutParams messageParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
        messageParams.topMargin = 12;
        container.addView(message, messageParams);

        Button continueButton = new Button(this);
        continueButton.setText("Continuer");
        continueButton.setTextColor(Color.WHITE);
        continueButton.setTextSize(16);
        continueButton.setAllCaps(false);

        GradientDrawable buttonBackground = new GradientDrawable();
        buttonBackground.setColor(Color.rgb(55, 120, 255));
        buttonBackground.setCornerRadius(28);
        continueButton.setBackground(buttonBackground);

        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        56
                );
        buttonParams.topMargin = 28;

        container.addView(continueButton, buttonParams);

        continueButton.setOnClickListener(v -> {
            dialog.dismiss();
            navigateToMain();
        });

        dialog.setContentView(container);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.88f),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        dialog.setCancelable(false);
        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.88f),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }

    private void showFirebaseError(Exception error) {
        String message = "Impossible de créer le compte.";

        if (error instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) error).getErrorCode();

            switch (code) {
                case "ERROR_EMAIL_ALREADY_IN_USE":
                    message = "Cette adresse email est déjà utilisée.";
                    break;

                case "ERROR_INVALID_EMAIL":
                    message = "Cette adresse email n'est pas valide.";
                    break;

                case "ERROR_WEAK_PASSWORD":
                    message = "Le mot de passe est trop faible.";
                    break;

                case "ERROR_USER_NOT_FOUND":
                case "ERROR_WRONG_PASSWORD":
                case "ERROR_INVALID_CREDENTIAL":
                    message = "Email ou mot de passe incorrect.";
                    break;

                case "ERROR_NETWORK_REQUEST_FAILED":
                    message = "Connexion Internet indisponible.";
                    break;

                case "ERROR_TOO_MANY_REQUESTS":
                    message = "Trop de tentatives. Réessaie dans quelques instants.";
                    break;

                default:
                    message = "Une erreur est survenue. Réessaie.";
                    break;
            }
        }

        showMessage(message);
    }

    private void showMessage(String message) {
        android.widget.Toast.makeText(
                this,
                message,
                android.widget.Toast.LENGTH_LONG
        ).show();
    }

    private void setLoading(boolean loading) {
        btnSubmit.setEnabled(!loading);

        if (loading) {
            btnSubmit.setText("Patientez...");
        } else {
            btnSubmit.setText(isSignUpMode ? "S'inscrire" : "Se connecter");
        }

        etEmail.setEnabled(!loading);
        etPassword.setEnabled(!loading);
        etFirstName.setEnabled(!loading);
        etRepeatPassword.setEnabled(!loading);
        tvToggleMode.setEnabled(!loading);
    }

    private void navigateToMain() {
        startActivity(new Intent(LoginActivity.this, MainActivity.class));
        finish();
    }
}
