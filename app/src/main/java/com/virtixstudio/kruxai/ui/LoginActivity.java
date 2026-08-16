package com.virtixstudio.kruxai.ui;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;
import com.virtixstudio.kruxai.R;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private EditText etFirstName;
    private EditText etEmail;
    private EditText etPassword;
    private EditText etRepeatPassword;

    private Button btnSubmit;

    private TextView tvTitle;
    private TextView tvToggleMode;

    private boolean isSignUpMode = false;
    private boolean isLoading = false;

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

        tvToggleMode.setOnClickListener(v -> {
            if (!isLoading) {
                toggleMode();
            }
        });

        btnSubmit.setOnClickListener(v -> {
            if (!isLoading) {
                handleSubmit();
            }
        });
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
        String password = etPassword.getText().toString();

        if (email.isEmpty()) {
            showMessage("Veuillez entrer votre adresse email.");
            return;
        }

        if (password.isEmpty()) {
            showMessage("Veuillez entrer votre mot de passe.");
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
        String repeatPassword = etRepeatPassword.getText().toString();

        if (firstName.isEmpty()) {
            showMessage("Veuillez entrer votre prénom.");
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

        setLoading(true);

        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {

                    if (!task.isSuccessful()) {
                        setLoading(false);

                        if (task.getException() != null) {
                            showFirebaseError(task.getException());
                        } else {
                            showMessage("Impossible de créer le compte.");
                        }

                        return;
                    }

                    FirebaseUser user = auth.getCurrentUser();

                    if (user == null) {
                        setLoading(false);
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

                                setLoading(false);

                                if (profileTask.isSuccessful()) {
                                    showAccountCreated(firstName);
                                } else {
                                    showMessage(
                                            "Compte créé, mais le profil n'a pas pu être enregistré."
                                    );
                                }
                            });
                });
    }

    private void handleLogin(String email, String password) {

        setLoading(true);

        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {

                    setLoading(false);

                    if (task.isSuccessful()) {
                        navigateToMain();
                    } else if (task.getException() != null) {
                        showFirebaseError(task.getException());
                    } else {
                        showMessage("Impossible de se connecter.");
                    }
                });
    }

    private void showAccountCreated(String firstName) {

        if (isFinishing() || isDestroyed()) {
            return;
        }

        final Dialog dialog = new Dialog(this);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER_HORIZONTAL);

        container.setPadding(
                dpToPx(32),
                dpToPx(28),
                dpToPx(32),
                dpToPx(24)
        );

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(22, 22, 27));
        background.setCornerRadius(dpToPx(28));
        container.setBackground(background);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_success);
        icon.setContentDescription("Compte créé avec succès");

        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(
                        dpToPx(76),
                        dpToPx(76)
                );

        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        container.addView(icon, iconParams);

        TextView title = new TextView(this);
        title.setText("Compte créé avec succès");
        title.setTextColor(Color.WHITE);
        title.setTextSize(21);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, Typeface.BOLD);

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        titleParams.topMargin = dpToPx(18);
        container.addView(title, titleParams);

        TextView message = new TextView(this);
        message.setText(
                "Bienvenue " + firstName + ".\n\n" +
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

        messageParams.topMargin = dpToPx(10);
        container.addView(message, messageParams);

        Button continueButton = new Button(this);
        continueButton.setText("Continuer");
        continueButton.setTextColor(Color.WHITE);
        continueButton.setTextSize(16);
        continueButton.setAllCaps(false);
        continueButton.setGravity(Gravity.CENTER);

        GradientDrawable buttonBackground = new GradientDrawable();
        buttonBackground.setColor(Color.rgb(55, 120, 255));
        buttonBackground.setCornerRadius(dpToPx(28));

        continueButton.setBackground(buttonBackground);

        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dpToPx(52)
                );

        buttonParams.topMargin = dpToPx(24);
        container.addView(continueButton, buttonParams);

        continueButton.setOnClickListener(v -> {
            if (dialog.isShowing()) {
                dialog.dismiss();
            }

            navigateToMain();
        });

        dialog.setContentView(container);
        dialog.setCancelable(false);

        dialog.setOnShowListener(d -> {

            Window window = dialog.getWindow();

            if (window == null) {
                return;
            }

            window.setBackgroundDrawableResource(
                    android.R.color.transparent
            );

            WindowManager.LayoutParams params =
                    window.getAttributes();

            params.width =
                    (int) (
                            getResources()
                                    .getDisplayMetrics()
                                    .widthPixels * 0.88f
                    );

            params.height =
                    WindowManager.LayoutParams.WRAP_CONTENT;

            window.setAttributes(params);
        });

        dialog.show();
    }

    private void showFirebaseError(Exception error) {

        String message = "Une erreur est survenue. Réessaie.";

        if (error instanceof FirebaseAuthException) {

            String code =
                    ((FirebaseAuthException) error).getErrorCode();

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
                    message = "Trop de tentatives. Réessaie plus tard.";
                    break;

                default:
                    message = "Une erreur est survenue. Réessaie.";
                    break;
            }
        }

        showMessage(message);
    }

    private void showMessage(String message) {

        if (isFinishing() || isDestroyed()) {
            return;
        }

        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }

    private void setLoading(boolean loading) {

        isLoading = loading;

        if (btnSubmit != null) {
            btnSubmit.setEnabled(!loading);

            btnSubmit.setText(
                    loading
                            ? "Patientez..."
                            : (isSignUpMode
                            ? "S'inscrire"
                            : "Se connecter")
            );
        }

        if (etEmail != null) {
            etEmail.setEnabled(!loading);
        }

        if (etPassword != null) {
            etPassword.setEnabled(!loading);
        }

        if (etFirstName != null) {
            etFirstName.setEnabled(!loading);
        }

        if (etRepeatPassword != null) {
            etRepeatPassword.setEnabled(!loading);
        }

        if (tvToggleMode != null) {
            tvToggleMode.setEnabled(!loading);
        }
    }

    private void navigateToMain() {

        if (isFinishing() || isDestroyed()) {
            return;
        }

        Intent intent =
                new Intent(
                        LoginActivity.this,
                        MainActivity.class
                );

        startActivity(intent);
        finish();
    }

    private int dpToPx(int dp) {

        float density =
                getResources()
                        .getDisplayMetrics()
                        .density;

        return Math.round(dp * density);
    }
}
