package com.virtixstudio.kruxai.ui;
import com.virtixstudio.kruxai.api.ApiClient;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.virtixstudio.kruxai.R;
import com.virtixstudio.kruxai.adapters.ChatAdapter;
import com.virtixstudio.kruxai.api.GroqApiClient;
import com.virtixstudio.kruxai.api.WebSearchEngine;
import com.virtixstudio.kruxai.models.ChatMessage;
import com.virtixstudio.kruxai.models.SearchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements ChatAdapter.OnSpeechRequestedListener {

    private static final String GROQ_API_KEY = com.virtixstudio.kruxai.BuildConfig.GROQ_API_KEY;
    private static final int PERMISSION_AUDIO_CODE = 101;

    private DrawerLayout drawerLayout;
    private ImageButton btnMenu, btnAccount, btnPlus, btnMic, btnSend, btnCloseSidebar, btnScrollBottom;
    private View navStudio, navLogout;
    private Button btnNewChat;
    private EditText etInput;
    private RecyclerView rvChat;

    private ChatAdapter chatAdapter;
    private List<ChatMessage> messageList;
    private GroqApiClient groqClient;
    private WebSearchEngine webSearchEngine;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;
    private boolean isListening = false;

    private boolean isLearningMode = false;
    private boolean isDeepSearchEnabled = true; // Recherche Web activée par défaut
    private boolean isThinkingMode = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        groqClient = new GroqApiClient(GROQ_API_KEY);
        webSearchEngine = new WebSearchEngine();

        drawerLayout = findViewById(R.id.drawerLayout);
        btnMenu = findViewById(R.id.btnMenu);
        btnAccount = findViewById(R.id.btnAccount);
        btnPlus = findViewById(R.id.btnPlus);
        btnMic = findViewById(R.id.btnMic);
        btnSend = findViewById(R.id.btnSend);
        btnScrollBottom = findViewById(R.id.btnScrollBottom);
        btnScrollBottom = findViewById(R.id.btnScrollBottom);
        btnScrollBottom = findViewById(R.id.btnScrollBottom);
        etInput = findViewById(R.id.etInput);
        rvChat = findViewById(R.id.rvChat);

        btnCloseSidebar = findViewById(R.id.btnCloseSidebar);
        btnNewChat = findViewById(R.id.btnNewChat);
        navStudio = findViewById(R.id.navStudio);
        navLogout = findViewById(R.id.navLogout);

        messageList = new ArrayList<>();
        chatAdapter = new ChatAdapter(messageList, this);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvChat.setLayoutManager(layoutManager);
        rvChat.setAdapter(chatAdapter);
        if (btnScrollBottom != null) {
            rvChat.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@androidx.annotation.NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    btnScrollBottom.setVisibility(rvChat.canScrollVertically(1) ? View.VISIBLE : View.GONE);
                }
            });
            btnScrollBottom.setOnClickListener(v -> {
                if (chatAdapter != null && chatAdapter.getItemCount() > 0) {
                    rvChat.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
                }
            });
        }
        if (btnScrollBottom != null) {
            rvChat.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@androidx.annotation.NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    btnScrollBottom.setVisibility(rvChat.canScrollVertically(1) ? View.VISIBLE : View.GONE);
                }
            });
            btnScrollBottom.setOnClickListener(v -> {
                if (chatAdapter != null && chatAdapter.getItemCount() > 0) {
                    rvChat.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
                }
            });
        }
        if (btnScrollBottom != null) {
            rvChat.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@androidx.annotation.NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    btnScrollBottom.setVisibility(rvChat.canScrollVertically(1) ? View.VISIBLE : View.GONE);
                }
            });
            btnScrollBottom.setOnClickListener(v -> {
                if (chatAdapter != null && chatAdapter.getItemCount() > 0) {
                    rvChat.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
                }
            });
        }

        btnMenu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        if (btnCloseSidebar != null) btnCloseSidebar.setOnClickListener(v -> drawerLayout.closeDrawer(GravityCompat.START));
        btnAccount.setOnClickListener(v -> showAccountBottomSheet());
        btnPlus.setOnClickListener(v -> showPlusBottomSheet());
        btnSend.setOnClickListener(v -> sendMessage());
        btnMic.setOnClickListener(v -> toggleVoiceRecognition());

        setupSidebarEvents();
        initSpeechRecognizer();
        initTextToSpeech();
        listenToFirebaseMessages();
    }

    private void initTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) textToSpeech.setLanguage(Locale.FRENCH);
        });
    }

    @Override
    public void onSpeakRequested(String text) {
        if (textToSpeech != null) {
            if (textToSpeech.isSpeaking()) {
                textToSpeech.stop();
            } else {
                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "KruxAI_TTS");
            }
        }
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

        if (isDeepSearchEnabled) {
            // VRAIE RECHERCHE EN TEMPS RÉEL SUR LE WEB
            webSearchEngine.search(prompt, new WebSearchEngine.SearchCallback() {
                @Override
                public void onSuccess(List<SearchResult> results, String formattedContext) {
                    executeAiQuery(prompt, formattedContext, results);
                }

                @Override
                public void onError(String error) {
                    executeAiQuery(prompt, "", new ArrayList<>());
                }
            });
        } else {
            executeAiQuery(prompt, "", new ArrayList<>());
        }
    }

    private void executeAiQuery(String userPrompt, String webContext, List<SearchResult> sources) {
        StringBuilder systemPrompt = new StringBuilder("Tu es KruxAI, un assistant IA expert et ultra-précis.");
        if (isThinkingMode) systemPrompt.append(" Réfléchis toujours ÉTAPE PAR ÉTAPE.");
        if (isLearningMode) systemPrompt.append(" Adopte un mode Pédagogique.");

        String fullPrompt = userPrompt;
        if (!webContext.isEmpty()) {
            systemPrompt.append(" Tu as un accès direct aux données du Web en temps réel ci-dessous. Utilise ces informations fraîches pour répondre précisément et cite les numéros des sources si nécessaire.");
            fullPrompt = webContext + "\nQuestion de l'utilisateur : " + userPrompt;
        }

        ApiClient.sendRequest(systemPrompt.toString(), fullPrompt, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(String responseText, String modelBrand) {
                ChatMessage aiMessage = new ChatMessage(responseText, false, sources);
                saveMessageToFirebase(aiMessage);
            }

            @Override
            public void onError(String errorMessage) {
                ChatMessage errorMsg = new ChatMessage("Erreur : " + errorMessage, false);
                saveMessageToFirebase(errorMsg);
            }
        });
    }

        ApiClient.sendRequest(systemPrompt.toString(), fullPrompt, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(String responseText, String modelBrand) {
                ChatMessage aiMessage = new ChatMessage(responseText, false, sources);
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

        TextView optLearning = view.findViewById(R.id.optLearning);
        TextView optDeepSearch = view.findViewById(R.id.optDeepSearch);
        TextView optThinking = view.findViewById(R.id.optThinking);

        if (isLearningMode) optLearning.setText("🎓 Mode Apprentissage [ACTIF]");
        if (isDeepSearchEnabled) optDeepSearch.setText("Recherche Web Temps Réel [ACTIF]");
        if (isThinkingMode) optThinking.setText("🧠 Mode Réflexion [ACTIF]");

        optLearning.setOnClickListener(v -> {
            isLearningMode = !isLearningMode;
            dialog.dismiss();
        });

        optDeepSearch.setOnClickListener(v -> {
            isDeepSearchEnabled = !isDeepSearchEnabled;
            Toast.makeText(this, isDeepSearchEnabled ? "Recherche Web Temps Réel Activée " : "Recherche Web Désactivée", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        optThinking.setOnClickListener(v -> {
            isThinkingMode = !isThinkingMode;
            dialog.dismiss();
        });

        dialog.show();
    }

    private void initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {}
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

    private void setupSidebarEvents() {
        if (navStudio != null) {
            navStudio.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                startActivity(new Intent(MainActivity.this, StudioActivity.class));
            });
        }
        if (navLogout != null) {
            navLogout.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                mAuth.signOut();
                startActivity(new Intent(MainActivity.this, LoginActivity.class));
                finish();
            });
        }
        if (btnNewChat != null) {
            btnNewChat.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                messageList.clear();
                chatAdapter.notifyDataSetChanged();
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }

    private boolean isGenerating = false;
    
    private void setGeneratingState(boolean generating) {
        isGenerating = generating;
        ImageButton btnSend = findViewById(R.id.btnSend); // Assurez-vous que l id correspond
        btnScrollBottom = findViewById(R.id.btnScrollBottom);
        btnScrollBottom = findViewById(R.id.btnScrollBottom);
        btnScrollBottom = findViewById(R.id.btnScrollBottom);
        if (btnSend != null) {
            if (generating) {
                // Icône ou couleur Stop
                btnSend.setBackgroundColor(0xFFD32F2F); // Rouge d arrêt
            } else {
                // Icône ou couleur Envoi normal
                btnSend.setBackgroundColor(0xFF1E88E5); // Bleu brillant
            }
        }
    }
    
}
