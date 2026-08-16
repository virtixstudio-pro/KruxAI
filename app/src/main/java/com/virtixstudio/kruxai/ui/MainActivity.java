package com.virtixstudio.kruxai.ui;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import com.virtixstudio.kruxai.R;
import com.virtixstudio.kruxai.adapters.ChatAdapter;
import com.virtixstudio.kruxai.adapters.HistoryAdapter;
import com.virtixstudio.kruxai.api.ApiClient;
import com.virtixstudio.kruxai.api.WebSearchEngine;
import com.virtixstudio.kruxai.database.KruxDatabaseHelper;
import com.virtixstudio.kruxai.models.ChatMessage;
import com.virtixstudio.kruxai.models.ChatSession;
import com.virtixstudio.kruxai.models.SearchResult;
import com.virtixstudio.kruxai.utils.ChatHistoryManager;
import com.virtixstudio.kruxai.utils.SystemPromptBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements ChatAdapter.OnSpeechRequestedListener {

    private static final int PERMISSION_AUDIO_CODE = 101;

    private DrawerLayout drawerLayout;
    private ImageButton btnMenu, btnAccount, btnPlus, btnMic, btnStopMic, btnSend, btnCloseSidebar, btnScrollBottom, btnTtsControl;
    private View navStudio, navLogout;
    private Button btnNewChat;
    private EditText etInput;
    private RecyclerView rvChat, rvHistory;
    private LinearLayout llVoiceVisualizer;
    private View waveBar1, waveBar2, waveBar3, waveBar4;

    private ChatAdapter chatAdapter;
    private List<ChatMessage> messageList;
    private WebSearchEngine webSearchEngine;
    private KruxDatabaseHelper dbHelper;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;
    private boolean isListening = false;
    private boolean isTtsSpeaking = false;
    private List<ValueAnimator> activeAnimators = new ArrayList<>();

    private boolean isLearningMode = false;
    private boolean isDeepSearchEnabled = true;
    private boolean isThinkingMode = true;
    private String currentSessionId;
    private boolean isGenerating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        webSearchEngine = new WebSearchEngine();
        dbHelper = new KruxDatabaseHelper(this);

        currentSessionId = "session_" + System.currentTimeMillis();

        drawerLayout = findViewById(R.id.drawerLayout);
        btnMenu = findViewById(R.id.btnMenu);
        btnAccount = findViewById(R.id.btnAccount);
        btnPlus = findViewById(R.id.btnPlus);
        btnMic = findViewById(R.id.btnMic);
        btnStopMic = findViewById(R.id.btnStopMic);
        btnSend = findViewById(R.id.btnSend);
        btnScrollBottom = findViewById(R.id.btnScrollBottom);
        btnTtsControl = findViewById(R.id.btnTtsControl);

        etInput = findViewById(R.id.etInput);
        rvChat = findViewById(R.id.rvChat);
        rvHistory = findViewById(R.id.rvHistory);

        llVoiceVisualizer = findViewById(R.id.llVoiceVisualizer);
        waveBar1 = findViewById(R.id.waveBar1);
        waveBar2 = findViewById(R.id.waveBar2);
        waveBar3 = findViewById(R.id.waveBar3);
        waveBar4 = findViewById(R.id.waveBar4);

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

        if (rvHistory != null) {
            rvHistory.setLayoutManager(new LinearLayoutManager(this));
        }

        rvChat.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                if (dy < -10 && isTtsSpeaking && btnTtsControl != null) {
                    btnTtsControl.setVisibility(View.GONE);
                } else if (dy > 10 && isTtsSpeaking && btnTtsControl != null) {
                    btnTtsControl.setVisibility(View.VISIBLE);
                }

                if (btnScrollBottom != null) {
                    btnScrollBottom.setVisibility(rvChat.canScrollVertically(1) ? View.VISIBLE : View.GONE);
                }
            }
        });

        if (btnScrollBottom != null) {
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
        if (btnStopMic != null) btnStopMic.setOnClickListener(v -> toggleVoiceRecognition());
        if (btnTtsControl != null) btnTtsControl.setOnClickListener(v -> toggleTtsPlayback());

        setupSidebarEvents();
        initSpeechRecognizer();
        initTextToSpeech();
        listenToFirebaseMessages();
        loadHistorySidebar();
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
                isTtsSpeaking = false;
                if (btnTtsControl != null) btnTtsControl.setVisibility(View.GONE);
            } else {
                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "KruxAI_TTS");
                isTtsSpeaking = true;
                if (btnTtsControl != null) {
                    btnTtsControl.setImageResource(R.drawable.ic_pause);
                    btnTtsControl.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    private void toggleTtsPlayback() {
        if (textToSpeech == null) return;
        if (textToSpeech.isSpeaking()) {
            textToSpeech.stop();
            isTtsSpeaking = false;
            if (btnTtsControl != null) btnTtsControl.setImageResource(R.drawable.ic_play);
        } else {
            isTtsSpeaking = false;
            if (btnTtsControl != null) btnTtsControl.setVisibility(View.GONE);
        }
    }

    private void listenToFirebaseMessages() {
        // Désactivé pour éviter la duplication des messages à l'écran.
        // L'interface locale gère l'affichage immédiat, Firestore gère la sauvegarde.
    }

    private void saveMessageToDatabase(ChatMessage message) {
        if (currentSessionId == null || currentSessionId.isEmpty()) {
            currentSessionId = "session_" + System.currentTimeMillis();
        }
        dbHelper.saveMessage(currentSessionId, message.isUser() ? "user" : "ai", message.getText());
        ChatHistoryManager.saveMessage(this, currentSessionId, message);

        // Mise à jour de l'UI en local
        runOnUiThread(() -> {
            messageList.add(message);
            chatAdapter.notifyItemInserted(messageList.size() - 1);
            rvChat.smoothScrollToPosition(messageList.size() - 1);
            loadHistorySidebar();
        });

        // Sauvegarde distante dans Firestore
        if (currentUser != null) {
            db.collection("users")
                    .document(currentUser.getUid())
                    .collection("chats")
                    .add(message);
        } else {
            // Auto-connexion anonyme pour débloquer l'écriture Firestore si non connecté
            com.google.firebase.auth.FirebaseAuth.getInstance().signInAnonymously()
                    .addOnSuccessListener(authResult -> {
                        currentUser = authResult.getUser();
                        if (currentUser != null) {
                            db.collection("users")
                                    .document(currentUser.getUid())
                                    .collection("chats")
                                    .add(message);
                        }
                    });
        }
    }

    private void sendMessage() {
        String prompt = etInput.getText().toString().trim();
        if (prompt.isEmpty()) return;

        ChatMessage userMessage = new ChatMessage(prompt, true);
        saveMessageToDatabase(userMessage);
        etInput.setText("");

        if (isDeepSearchEnabled) {
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
        StringBuilder historyBuilder = new StringBuilder();
        int startIndex = Math.max(0, messageList.size() - 10);
        for (int i = startIndex; i < messageList.size() - 1; i++) {
            ChatMessage msg = messageList.get(i);
            historyBuilder.append(msg.isUser() ? "Utilisateur: " : "Krux AI: ")
                    .append(msg.getText())
                    .append("\n");
        }

        if (webContext != null && !webContext.isEmpty()) {
            historyBuilder.append("\nContexte Recherche Web:\n").append(webContext);
        }

        List<String> memoryFacts = dbHelper.getAllMemoryFacts();
        if (memoryFacts != null && !memoryFacts.isEmpty()) {
            historyBuilder.append("\nFaits mémorisés sur l'utilisateur:\n");
            for (String fact : memoryFacts) {
                historyBuilder.append("- ").append(fact).append("\n");
            }
        }

        String systemPrompt = new SystemPromptBuilder()
                .withHistory(historyBuilder.toString())
                .build();

        StringBuilder promptWithContext = new StringBuilder();
        promptWithContext.append("Utilisateur: ").append(userPrompt);

        setGeneratingState(true);

        ApiClient.sendRequest(systemPrompt, promptWithContext.toString(), new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(String rawResponse, String modelBrand) {
                runOnUiThread(() -> setGeneratingState(false));

                String cleanResponse = rawResponse;
                if (cleanResponse.contains("<REMEMBER>") && cleanResponse.contains("</REMEMBER>")) {
                    try {
                        int start = cleanResponse.indexOf("<REMEMBER>") + 10;
                        int end = cleanResponse.indexOf("</REMEMBER>");
                        if (end > start) {
                            String fact = cleanResponse.substring(start, end).trim();
                            dbHelper.addMemoryFact(fact);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    cleanResponse = cleanResponse.replaceAll("<REMEMBER>.*?</REMEMBER>", "").trim();
                }

                ChatMessage aiMessage = new ChatMessage(cleanResponse, false, sources);
                saveMessageToDatabase(aiMessage);
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> setGeneratingState(false));
                ChatMessage errorMsg = new ChatMessage("Erreur : " + errorMessage, false);
                saveMessageToDatabase(errorMsg);
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

        if (isLearningMode) optLearning.setText(" Mode Apprentissage [ACTIF]");
        if (isDeepSearchEnabled) optDeepSearch.setText("Recherche Web Temps Réel [ACTIF]");
        if (isThinkingMode) optThinking.setText(" Mode Réflexion [ACTIF]");

        optLearning.setOnClickListener(v -> {
            isLearningMode = !isLearningMode;
            dialog.dismiss();
        });

        optDeepSearch.setOnClickListener(v -> {
            isDeepSearchEnabled = !isDeepSearchEnabled;
            Toast.makeText(this, isDeepSearchEnabled ? "Recherche Web Activée" : "Recherche Web Désactivée", Toast.LENGTH_SHORT).show();
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
                @Override public void onEndOfSpeech() { stopVoiceUI(); }
                @Override public void onError(int error) { stopVoiceUI(); }
                @Override public void onResults(Bundle results) {
                    stopVoiceUI();
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
            stopVoiceUI();
        } else {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            speechRecognizer.startListening(intent);

            isListening = true;
            btnMic.setVisibility(View.GONE);
            if (btnStopMic != null) btnStopMic.setVisibility(View.VISIBLE);
            etInput.setVisibility(View.GONE);
            if (llVoiceVisualizer != null) llVoiceVisualizer.setVisibility(View.VISIBLE);

            startWaveAnimation();
        }
    }

    private void stopVoiceUI() {
        isListening = false;
        stopWaveAnimation();

        btnMic.setVisibility(View.VISIBLE);
        if (btnStopMic != null) btnStopMic.setVisibility(View.GONE);
        if (llVoiceVisualizer != null) llVoiceVisualizer.setVisibility(View.GONE);
        etInput.setVisibility(View.VISIBLE);
    }

    private void startWaveAnimation() {
        animateBar(waveBar1, 12, 32, 280);
        animateBar(waveBar2, 20, 36, 220);
        animateBar(waveBar3, 10, 26, 320);
        animateBar(waveBar4, 16, 34, 250);
    }

    private void animateBar(View bar, int minDp, int maxDp, long duration) {
        if (bar == null) return;
        int minPx = (int) (minDp * getResources().getDisplayMetrics().density);
        int maxPx = (int) (maxDp * getResources().getDisplayMetrics().density);

        ValueAnimator animator = ValueAnimator.ofInt(minPx, maxPx);
        animator.setDuration(duration);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.addUpdateListener(animation -> {
            if (!isListening) {
                animator.cancel();
                return;
            }
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) bar.getLayoutParams();
            params.height = (int) animation.getAnimatedValue();
            bar.setLayoutParams(params);
        });
        animator.start();
        activeAnimators.add(animator);
    }

    private void stopWaveAnimation() {
        for (ValueAnimator animator : activeAnimators) {
            animator.cancel();
        }
        activeAnimators.clear();
    }

    private void showAccountBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_account, null);
        dialog.setContentView(view);

        TextView tvUserEmail = view.findViewById(R.id.tvUserEmail);
        Button btnLogout = view.findViewById(R.id.btnLogout);
        RadioGroup rgTtsEngine = view.findViewById(R.id.rgTtsEngine);
        RadioButton rbTtsLocal = view.findViewById(R.id.rbTtsLocal);
        RadioButton rbTtsCloud = view.findViewById(R.id.rbTtsCloud);

        if (currentUser != null && currentUser.getEmail() != null && tvUserEmail != null) {
            tvUserEmail.setText(currentUser.getEmail());
        }

        if (rgTtsEngine != null) {
            SharedPreferences prefs = getSharedPreferences("krux_settings", MODE_PRIVATE);
            boolean isCloud = prefs.getBoolean("tts_cloud", false);
            if (isCloud && rbTtsCloud != null) rbTtsCloud.setChecked(true);
            else if (rbTtsLocal != null) rbTtsLocal.setChecked(true);

            rgTtsEngine.setOnCheckedChangeListener((group, checkedId) -> {
                boolean useCloud = (checkedId == R.id.rbTtsCloud);
                prefs.edit().putBoolean("tts_cloud", useCloud).apply();
            });
        }

        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> {
                dialog.dismiss();
                mAuth.signOut();
                startActivity(new Intent(MainActivity.this, LoginActivity.class));
                finish();
            });
        }

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
                currentSessionId = "session_" + System.currentTimeMillis();
                messageList.clear();
                chatAdapter.notifyDataSetChanged();
            });
        }
    }

    private void setGeneratingState(boolean generating) {
        isGenerating = generating;
        if (btnSend != null) {
            btnSend.setBackgroundColor(generating ? 0xFFD32F2F : 0xFF1E88E5);
        }
    }

    private void loadHistorySidebar() {
        if (rvHistory == null) return;
        List<ChatSession> sessions = dbHelper.getAllSessions();

        HistoryAdapter historyAdapter = new HistoryAdapter(this, sessions, new HistoryAdapter.OnSessionActionListener() {
            @Override
            public void onSessionClick(ChatSession session) {
                currentSessionId = session.getId();
                messageList.clear();
                messageList.addAll(session.getMessages());
                chatAdapter.notifyDataSetChanged();
                if (drawerLayout != null) drawerLayout.closeDrawers();
            }

            @Override
            public void onSessionPinToggle(ChatSession session) {
                dbHelper.togglePinSession(session.getId());
                loadHistorySidebar();
            }

            @Override
            public void onSessionRename(ChatSession session) {
                showRenameDialog(session);
            }

            @Override
            public void onSessionDelete(ChatSession session) {
                dbHelper.deleteSession(session.getId());
                if (session.getId().equals(currentSessionId)) {
                    messageList.clear();
                    chatAdapter.notifyDataSetChanged();
                }
                loadHistorySidebar();
            }
        });

        rvHistory.setAdapter(historyAdapter);

        View searchView = findViewById(R.id.etSearchHistory);
        if (searchView == null) searchView = findViewById(R.id.searchViewHistory);

        if (searchView instanceof EditText) {
            EditText etSearch = (EditText) searchView;
            etSearch.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterSessions(s.toString(), sessions, historyAdapter);
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        } else if (searchView instanceof androidx.appcompat.widget.SearchView) {
            androidx.appcompat.widget.SearchView sv = (androidx.appcompat.widget.SearchView) searchView;
            sv.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
                @Override public boolean onQueryTextSubmit(String query) { return false; }
                @Override public boolean onQueryTextChange(String newText) {
                    filterSessions(newText, sessions, historyAdapter);
                    return true;
                }
            });
        }
    }

    private void filterSessions(String query, List<ChatSession> allSessions, HistoryAdapter adapter) {
        if (query == null || query.trim().isEmpty()) {
            adapter.updateList(allSessions);
            return;
        }
        String lowerQuery = query.toLowerCase().trim();
        List<ChatSession> filtered = new ArrayList<>();
        for (ChatSession session : allSessions) {
            String title = dbHelper.getSessionTitle(session.getId());
            if (title != null && title.toLowerCase().contains(lowerQuery)) {
                filtered.add(session);
                continue;
            }
            if (session.getMessages() != null) {
                for (ChatMessage msg : session.getMessages()) {
                    if (msg.getText() != null && msg.getText().toLowerCase().contains(lowerQuery)) {
                        filtered.add(session);
                        break;
                    }
                }
            }
        }
        adapter.updateList(filtered);
    }

    private void showRenameDialog(ChatSession session) {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Renommer la discussion");

        final EditText input = new EditText(this);
        String currentTitle = dbHelper.getSessionTitle(session.getId());
        input.setText(currentTitle != null ? currentTitle : "");
        builder.setView(input);

        builder.setPositiveButton("Enregistrer", (dialog, which) -> {
            String newTitle = input.getText().toString().trim();
            if (!newTitle.isEmpty()) {
                dbHelper.renameSession(session.getId(), newTitle);
                loadHistorySidebar();
            }
        });
        builder.setNegativeButton("Annuler", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopWaveAnimation();
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }
}
