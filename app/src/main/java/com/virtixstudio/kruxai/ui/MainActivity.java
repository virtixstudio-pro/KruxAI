package com.virtixstudio.kruxai.ui;

import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.virtixstudio.kruxai.R;
import com.virtixstudio.kruxai.api.GroqApiClient;
import com.virtixstudio.kruxai.db.AppDatabase;
import com.virtixstudio.kruxai.db.MemoryEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int SPEECH_REQUEST_CODE = 101;
    private static final String GROQ_API_KEY = com.virtixstudio.kruxai.BuildConfig.GROQ_API_KEY;

    private TextView tvGreeting, tvChatDisplay;
    private LinearLayout welcomeContainer;
    private EditText etMessage;
    private ImageButton btnSend, btnVoice, btnAddAttachment;

    private GroqApiClient groqClient;
    private AppDatabase db;
    private String userName = "Développeur";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        groqClient = new GroqApiClient(GROQ_API_KEY);
        db = AppDatabase.getInstance(this);

        tvGreeting = findViewById(R.id.tvGreeting);
        tvChatDisplay = findViewById(R.id.tvChatDisplay);
        welcomeContainer = findViewById(R.id.welcomeContainer);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnVoice = findViewById(R.id.btnVoice);
        btnAddAttachment = findViewById(R.id.btnAddAttachment);

        loadUserProfile();

        btnSend.setOnClickListener(v -> handleSendMessage());
        btnVoice.setOnClickListener(v -> startVoiceRecognition());
    }

    private void loadUserProfile() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            String uid = auth.getCurrentUser().getUid();
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists() && documentSnapshot.contains("firstName")) {
                        userName = documentSnapshot.getString("firstName");
                        tvGreeting.setText("Salut " + userName + " ! On fait quoi aujourd'hui ?");
                    }
                });
        }
    }

    private void handleSendMessage() {
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty()) return;

        welcomeContainer.setVisibility(View.GONE);
        appendChat("Vous: " + text + "\n\n");
        etMessage.setText("");

        // Récupérer la mémoire locale pour l'inclure dans le Prompt Système
        Executors.newSingleThreadExecutor().execute(() -> {
            List<MemoryEntity> memories = db.memoryDao().getAllMemories();
            StringBuilder memoryContext = new StringBuilder("Informations retenues sur l'utilisateur (" + userName + ") :\n");
            for (MemoryEntity m : memories) {
                memoryContext.append("- ").append(m.key).append(" : ").append(m.value).append("\n");
            }

            String systemPrompt = "Tu es KRUX AI, créé par Virtix Studio. Sois ultra-précis, logique et effectue un raisonnement étape par étape (Deep Thinking). " 
                    + memoryContext.toString();

            runOnUiThread(() -> {
                appendChat("KRUX AI : Génération en cours...\n\n");
                groqClient.sendMessage("llama-3.3-70b-versatile", systemPrompt, text, new GroqApiClient.GroqCallback() {
                    @Override
                    public void onSuccess(String responseText) {
                        updateLastResponse(responseText);
                    }

                    @Override
                    public void onError(String errorMessage) {
                        updateLastResponse("Erreur : " + errorMessage);
                    }
                });
            });
        });
    }

    private void appendChat(String text) {
        tvChatDisplay.append(text);
    }

    private void updateLastResponse(String response) {
        String currentText = tvChatDisplay.getText().toString();
        int lastIndex = currentText.lastIndexOf("KRUX AI : Génération en cours...\n\n");
        if (lastIndex != -1) {
            String updatedText = currentText.substring(0, lastIndex) + "KRUX AI :\n" + response + "\n\n-------------------\n\n";
            tvChatDisplay.setText(updatedText);
        } else {
            tvChatDisplay.append("KRUX AI :\n" + response + "\n\n-------------------\n\n");
        }
    }

    private void startVoiceRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Parlez maintenant...");
        try {
            startActivityForResult(intent, SPEECH_REQUEST_CODE);
        } catch (Exception e) {
            Toast.makeText(this, "Reconnaissance vocale non disponible sur cet appareil.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                etMessage.setText(results.get(0));
            }
        }
    }
}
