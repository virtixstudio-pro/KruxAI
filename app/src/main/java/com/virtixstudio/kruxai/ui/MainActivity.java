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

    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;

    // États des modes avancés
    private boolean isLearningMode = false;
    private boolean isDeepSearchEnabled = false;
    private boolean isThinkingMode = true; // Activé par défaut pour exiger la rigueur

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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

        addMessage("Salut ! Je suis KRUX AI. Comment puis-je vous aider aujourd'hui ?", false);

        btnMenu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        btnAccount.setOnClickListener(v -> showAccountBottomSheet());
        btnPlus.setOnClickListener(v -> showPlusBottomSheet());
        btnSend.setOnClickListener(v -> sendMessage());
        btnMic.setOnClickListener(v -> toggleVoiceRecognition());

        setupDrawerNavigation();
        initSpeechRecognizer();
    }

    private void showPlusBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_plus, null);
        dialog.setContentView(view);

        TextView optFiles = view.findViewById(R.id.optFiles);
        TextView optLearning = view.findViewById(R.id.optLearning);
        TextView optDeepSearch = view.findViewById(R.id.optDeepSearch);
        TextView optThinking = view.findViewById(R.id.optThinking);

        // Mettre à jour visuellement l'état des modes actifs
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
            Toast.makeText(this, isDeepSearchEnabled ? "Deep Search (Google/Bing/DuckDuckGo) activé 🌐" : "Deep Search désactivé", Toast.LENGTH_SHORT).show();
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
                @Override public void onError(int error) {
                    isListening = false;
                    Toast.makeText(MainActivity.this, "Erreur d'écoute vocale.", Toast.LENGTH_SHORT).show();
                }
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
        TextView optCustomize = view.findViewById(R.id.optCustomize);
        TextView optMemory = view.findViewById(R.id.optMemory);
        TextView optSocials = view.findViewById(R.id.optSocials);
        Button btnLogout = view.findViewById(R.id.btnLogout);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getEmail() != null) {
            tvUserEmail.setText(user.getEmail());
        } else {
            tvUserEmail.setText("Utilisateur KruxAI");
        }

        optCustomize.setOnClickListener(v -> {
            Toast.makeText(this, "Module de personnalisation à venir.", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        optMemory.setOnClickListener(v -> {
            Toast.makeText(this, "Mémoire active : Contexte de l'utilisateur retenu.", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        optSocials.setOnClickListener(v -> {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Virtixstudio-pro/KruxAI")));
            dialog.dismiss();
        });

        btnLogout.setOnClickListener(v -> {
            dialog.dismiss();
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
        });

        dialog.show();
    }

    private void sendMessage() {
        String prompt = etInput.getText().toString().trim();
        if (prompt.isEmpty()) return;

        addMessage(prompt, true);
        etInput.setText("");

        // Construction du System Prompt dynamique selon les modes activés
        StringBuilder systemPrompt = new StringBuilder("Tu es KruxAI, un assistant IA expert, ultra-précis et technique.");
        
        if (isThinkingMode) {
            systemPrompt.append(" Règle absolue : Réfléchis toujours ÉTAPE PAR ÉTAPE avant de formuler ta réponse finale. Décompose ton raisonnement méthodiquement pour éviter toute erreur ou réponse superficielle.");
        }
        if (isLearningMode) {
            systemPrompt.append(" Adopte un mode Pédagogique et d'Apprentissage : explique les concepts en détail comme un professeur bienveillant.");
        }
        if (isDeepSearchEnabled) {
            systemPrompt.append(" Agis comme si tu avais effectué une recherche approfondie sur le web (sources croisées Google, Bing, DuckDuckGo) pour fournir des faits actualisés et sourcés.");
        }

        groqClient.sendMessage("llama-3.3-70b-versatile", systemPrompt.toString(), prompt, new GroqApiClient.GroqCallback() {
            @Override
            public void onSuccess(String responseText) {
                addMessage(responseText, false);
            }

            @Override
            public void onError(String errorMessage) {
                addMessage("Erreur : " + errorMessage, false);
            }
        });
    }

    private void addMessage(String text, boolean isUser) {
        messageList.add(new ChatMessage(text, isUser));
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        rvChat.smoothScrollToPosition(messageList.size() - 1);
    }

    private void setupDrawerNavigation() {
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_studio) {
                startActivity(new Intent(MainActivity.this, StudioActivity.class));
            } else if (id == R.id.nav_logout) {
                FirebaseAuth.getInstance().signOut();
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_AUDIO_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            toggleVoiceRecognition();
        } else {
            Toast.makeText(this, "Permission micro refusée.", Toast.LENGTH_SHORT).show();
        }
    }
}
