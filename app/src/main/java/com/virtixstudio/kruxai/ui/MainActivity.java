package com.virtixstudio.kruxai.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.virtixstudio.kruxai.R;
import com.virtixstudio.kruxai.adapters.ChatAdapter;
import com.virtixstudio.kruxai.api.GroqApiClient;
import com.virtixstudio.kruxai.models.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String GROQ_API_KEY = com.virtixstudio.kruxai.BuildConfig.GROQ_API_KEY;
    private static final int PERMISSION_AUDIO_CODE = 101;

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ImageButton btnMenu, btnAccount, btnPlus, btnMic, btnSend;
    private EditText etInput;
    private RecyclerView rvChat;

    private ChatAdapter chatAdapter;
    private List<ChatMessage> messageList;
    private GroqApiClient groqClient;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;

    private boolean isLearningMode = false;
    private boolean isDeepSearchEnabled = false;
    private boolean isThinkingMode = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        groqClient = new GroqApiClient(GROQ_API_KEY);

        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        btnMenu = findViewById(R.id.btnMenu);
        btnAccount = findViewById(R.id.btnAccount);
        btnPlus = findViewById(R.id.btnPlus);
        btnMic = findViewById(R.id.btnMic);
        btnSend = findViewById(R.id.btnSend);
        etInput = findViewById(R.id.etInput);
        rvChat = findViewById(R.id.rvChat);

        messageList = new ArrayList<>();
        chatAdapter = new ChatAdapter(messageList);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvChat.setLayoutManager(layoutManager);
        rvChat.setAdapter(chatAdapter);

        btnMenu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        btnAccount.setOnClickListener(v -> showAccountBottomSheet());
        btnPlus.setOnClickListener(v -> showPlusBottomSheet());
        btnSend.setOnClickListener(v -> sendMessage());
        btnMic.setOnClickListener(v -> toggleVoiceRecognition());

        setupDrawerNavigation();
        initSpeechRecognizer();
        listenToFirebaseMessages();
    }

    private void listenToFirebaseMessages() {
        if (currentUser == null) return;

        db.collection("users")
                .document(currentUser.getUid())
                .collection("chats")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;

                    for (DocumentChange dc : value.getDocumentChanges()) {
                        if (dc.getType() == DocumentChange.Type.ADDED) {
                            ChatMessage msg = dc.getDocument().toObject(ChatMessage.class);
                            messageList.add(msg);
                            chatAdapter.notifyItemInserted(messageList.size() - 1);
                            rvChat.smoothScrollToPosition(messageList.size() - 1);
                        }
                    }
                });
    }

    private void saveMessageToFirebase(ChatMessage message) {
        if (currentUser == null) return;

        db.collection("users")
                .document(currentUser.getUid())
                .collection("chats")
                .add(message);
    }

    private void sendMessage() {
        String prompt = etInput.getText().toString().trim();
        if (prompt.isEmpty()) return;

        ChatMessage userMessage = new ChatMessage(prompt, true);
        saveMessageToFirebase(userMessage);
        etInput.setText("");

        StringBuilder systemPrompt = new StringBuilder("Tu es KruxAI, un assistant IA expert, ultra-précis et technique.");
        if (isThinkingMode) {
            systemPrompt.append(" Règle absolue : Réfléchis toujours ÉTAPE PAR ÉTAPE avant de formuler ta réponse finale.");
        }
        if (isLearningMode) {
            systemPrompt.append(" Adopte un mode Pédagogique et d'Apprentissage.");
        }
        if (isDeepSearchEnabled) {
            systemPrompt.append(" Agis comme si tu avais effectué une recherche web approfondie en temps réel.");
        }

        groqClient.sendMessage("llama-3.3-70b-versatile", systemPrompt.toString(), prompt, new GroqApiClient.GroqCallback() {
            @Override
            public void onSuccess(String responseText) {
                ChatMessage aiMessage = new ChatMessage(responseText, false);
                saveMessageToFirebase(aiMessage);
            }

            @Override
            public void onError(String errorMessage) {
                ChatMessage errorMsg = new ChatMessage("Erreur : " + errorMessage, false);
                saveMessageToFirebase(errorMsg);
            }
        });
    }

    private void showPlusBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_plus, null);
        dialog.setContentView(view);

        TextView optFiles = view.findViewById(R.id.optFiles);
        TextView optLearning = view.findViewById(R.id.optLearning);
        TextView optDeepSearch = view.findViewById(R.id.optDeepSearch);
        TextView optThinking = view.findViewById(R.id.optThinking);

        if (isLearningMode) optLearning.setText("🎓 Mode Apprentissage [ACTIF]");
        if (isDeepSearchEnabled) optDeepSearch.setText("🌐 Deep Search [ACTIF]");
        if (isThinkingMode) optThinking.setText("🧠 Mode Réflexion (Étape par étape) [ACTIF]");

        optFiles.setOnClickListener(v -> {
            Toast.makeText(this, "Sélection de fichiers / images (Bientôt disponible)", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        optLearning.setOnClickListener(v -> {
            isLearningMode = !isLearningMode;
            Toast.makeText(this, isLearningMode ? "Mode Apprentissage activé 🎓" : "Mode Apprentissage désactivé", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        optDeepSearch.setOnClickListener(v -> {
            isDeepSearchEnabled = !isDeepSearchEnabled;
            Toast.makeText(this, isDeepSearchEnabled ? "Deep Search activé 🌐" : "Deep Search désactivé", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        optThinking.setOnClickListener(v -> {
            isThinkingMode = !isThinkingMode;
            Toast.makeText(this, isThinkingMode ? "Mode Réflexion étape par étape activé 🧠" : "Mode Réflexion standard", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) { Toast.makeText(MainActivity.this, "Écoute en cours...", Toast.LENGTH_SHORT).show(); }
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() { isListening = false; }
                @Override public void onError(int error) { isListening = false; }
                @Override public void onResults(Bundle results) {
                    isListening = false;
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        etInput.setText(matches.get(0));
                    }
                }
                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onEvent(int eventType, Bundle params) {}
            });
        }
    }

    private void toggleVoiceRecognition() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_AUDIO_CODE);
            return;
        }
        if (speechRecognizer == null) return;

        if (isListening) {
            speechRecognizer.stopListening();
            isListening = false;
        } else {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            speechRecognizer.startListening(intent);
            isListening = true;
        }
    }

    private void showAccountBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_account, null);
        dialog.setContentView(view);

        TextView tvUserEmail = view.findViewById(R.id.tvUserEmail);
        Button btnLogout = view.findViewById(R.id.btnLogout);

        if (currentUser != null && currentUser.getEmail() != null) {
            tvUserEmail.setText(currentUser.getEmail());
        }

        btnLogout.setOnClickListener(v -> {
            dialog.dismiss();
            mAuth.signOut();
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
        });

        dialog.show();
    }

    private void setupDrawerNavigation() {
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_studio) {
                startActivity(new Intent(MainActivity.this, StudioActivity.class));
            } else if (id == R.id.nav_logout) {
                mAuth.signOut();
                startActivity(new Intent(MainActivity.this, LoginActivity.class));
                finish();
            }
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
    }
}
