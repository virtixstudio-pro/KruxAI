package com.virtixstudio.kruxai.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.virtixstudio.kruxai.R;
import com.virtixstudio.kruxai.utils.LanguageManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Écran qui suit « S'inscrire » : résumé clair des données, puis création du compte quand l'utilisateur accepte.
 * Les informations saisies passent par {@link Pending}, en mémoire seulement (le mot de passe n'est jamais mis dans un Intent).
 */
public class PrivacyActivity extends AppCompatActivity {

    /** Données de l'inscription en attente d'acceptation. Effacées dès que le compte est créé. */
    public static final class Pending {
        static String firstName, email, password;
        static void set(String f, String e, String p) { firstName = f; email = e; password = p; }
        static void clear() { firstName = null; email = null; password = null; }
        static boolean has() { return email != null && password != null; }
    }

    public static final String POLICY_URL = "https://virtix-studio.kruxvirtix.workers.dev/privacy.html";
    public static final String TIPS_URL = "https://virtix-studio.kruxvirtix.workers.dev/precautions.html";

    private Button btnAccept;
    private boolean creating;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LanguageManager.applySavedLanguage(this);
        setContentView(R.layout.activity_privacy);
        KruxTheme.apply(this);

        if (!Pending.has()) { finish(); return; }

        final CheckBox cb = findViewById(R.id.cbAcceptPrivacy);
        btnAccept = findViewById(R.id.btnAcceptPrivacy);
        findViewById(R.id.tvPrivacyFull).setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(POLICY_URL))); }
            catch (Exception e) { Toast.makeText(this, "Impossible d'ouvrir la page.", Toast.LENGTH_SHORT).show(); }
        });
        findViewById(R.id.tvPrivacyBack).setOnClickListener(v -> finish());
        android.widget.TextView tips = findViewById(R.id.tvPrivacyTips);
        android.widget.TextView policy = findViewById(R.id.tvPrivacyFull);
        policy.setPaintFlags(policy.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        tips.setPaintFlags(tips.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        tips.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(TIPS_URL))); }
            catch (Exception e) { Toast.makeText(this, "Impossible d'ouvrir la page.", Toast.LENGTH_SHORT).show(); }
        });

        setAcceptEnabled(false);
        cb.setOnCheckedChangeListener((v, checked) -> setAcceptEnabled(checked && !creating));
        btnAccept.setOnClickListener(v -> createAccount());
    }

    private void setAcceptEnabled(boolean on) {
        btnAccept.setEnabled(on);
        btnAccept.setAlpha(on ? 1f : 0.45f);
    }

    private void createAccount() {
        if (creating || !Pending.has()) return;
        creating = true;
        setAcceptEnabled(false);
        btnAccept.setText("Création du compte…");
        final String firstName = Pending.firstName, email = Pending.email, password = Pending.password;
        final FirebaseAuth auth = FirebaseAuth.getInstance();

        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                String m = task.getException() == null || task.getException().getMessage() == null
                        ? "" : task.getException().getMessage().toLowerCase();
                if (m.contains("email address is already in use")) {
                    Toast.makeText(this, "Cet e-mail est déjà utilisé.", Toast.LENGTH_LONG).show();
                    finish(); // retour à l'inscription pour changer d'e-mail
                } else {
                    Toast.makeText(this, "Impossible de créer le compte. Vérifie ta connexion.", Toast.LENGTH_LONG).show();
                    creating = false;
                    btnAccept.setText("Accepter et créer mon compte");
                    setAcceptEnabled(((CheckBox) findViewById(R.id.cbAcceptPrivacy)).isChecked());
                }
                return;
            }
            FirebaseUser user = auth.getCurrentUser();
            if (user != null) {
                Map<String, Object> profile = new HashMap<>();
                profile.put("uid", user.getUid());
                profile.put("firstName", firstName);
                profile.put("email", email);
                profile.put("createdAt", FieldValue.serverTimestamp());
                // le profil s'enregistre en arrière-plan : on n'attend pas pour entrer dans l'app
                FirebaseFirestore.getInstance().collection("users").document(user.getUid()).set(profile);
            }
            Pending.clear();
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        // si l'utilisateur quitte l'écran sans créer le compte, on ne garde pas le mot de passe en mémoire
        if (!creating) Pending.clear();
        super.onDestroy();
    }
}
