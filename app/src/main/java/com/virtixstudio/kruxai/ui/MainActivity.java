package com.virtixstudio.kruxai.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.net.Uri;
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
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.virtixstudio.kruxai.core.ToolRouter;
import com.virtixstudio.kruxai.core.KruxState;
import com.virtixstudio.kruxai.models.ChatMessage;
import com.virtixstudio.kruxai.models.Feedback;
import com.virtixstudio.kruxai.models.KruxModel;
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
    private View navSearchChats, navStudio, navCustomize, navLogout;
    private Button btnNewChat;

    // Sélecteur de modèle Krux
    private LinearLayout kruxModelCapsule;
    private TextView tvKruxName;
    private TextView tvSelectedModel;
    private TextView tvTokenRemaining;
    private KruxModel selectedKruxModel = KruxModel.KRUX_35_FLASH;
    private EditText etInput;
    private RecyclerView rvChat, rvHistory;
    private View welcomePanel;
    private Button welcomeNewChat;
    private KruxWelcomeSceneView welcomeScene;
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
    private String voiceBaseText = "";
    private boolean isTtsSpeaking = false;
    private float voiceRmsLevel = 0.0f;

    private boolean isLearningMode = false;
    private boolean isDeepSearchEnabled = true;
    private boolean isThinkingMode = true;
    private String currentSessionId;
    private boolean isGenerating = false;
    private KruxState kruxState = KruxState.IDLE;
    private View kruxStatusContainer;
    private TextView tvKruxStatus;

private final ActivityResultLauncher<String[]> filePicker =
        registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        handleSelectedFile(uri);
                    }
                }
        );


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ChatMessage.initializeContext(this);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        webSearchEngine = new WebSearchEngine();
        dbHelper = new KruxDatabaseHelper(this);

        currentSessionId = getSharedPreferences("krux_chat", MODE_PRIVATE)
                .getString("current_session_id", null);

        if (currentSessionId == null || currentSessionId.isEmpty()) {
            currentSessionId = "session_" + System.currentTimeMillis();
            getSharedPreferences("krux_chat", MODE_PRIVATE)
                    .edit()
                    .putString("current_session_id", currentSessionId)
                    .apply();
        }

        drawerLayout = findViewById(R.id.drawerLayout);
        btnMenu = findViewById(R.id.btnMenu);
        btnAccount = findViewById(R.id.btnAccount);
        btnPlus = findViewById(R.id.btnPlus);
        btnMic = findViewById(R.id.btnMic);
        btnStopMic = findViewById(R.id.btnStopMic);
        btnSend = findViewById(R.id.btnSend);
        btnScrollBottom = findViewById(R.id.btnScrollBottom);
        btnTtsControl = findViewById(R.id.btnTtsControl);

        // Sélecteur de modèle Krux
        kruxModelCapsule = findViewById(R.id.kruxModelCapsule);
        tvKruxName = findViewById(R.id.tvKruxName);
        tvSelectedModel = findViewById(R.id.tvSelectedModel);
        tvTokenRemaining = findViewById(R.id.tvTokenRemaining);

        updateKruxModelUI();

        etInput = findViewById(R.id.etInput);
        rvChat = findViewById(R.id.rvChat);
        rvHistory = findViewById(R.id.rvHistory);
        welcomePanel = findViewById(R.id.welcomePanel);
        welcomeNewChat = findViewById(R.id.welcomeNewChat);
        welcomeScene = findViewById(R.id.welcomeScene);
        setupWelcomePanel();

        llVoiceVisualizer = findViewById(R.id.llVoiceVisualizer);
                kruxStatusContainer = findViewById(R.id.kruxStatusContainer);
        tvKruxStatus = findViewById(R.id.tvKruxStatus);
waveBar1 = findViewById(R.id.waveBar1);
        waveBar2 = findViewById(R.id.waveBar2);
        waveBar3 = findViewById(R.id.waveBar3);
        waveBar4 = findViewById(R.id.waveBar4);

        btnCloseSidebar = findViewById(R.id.btnCloseSidebar);
        btnNewChat = findViewById(R.id.btnNewChat);
        navSearchChats = findViewById(R.id.navSearchChats);
        navStudio = findViewById(R.id.navStudio);
        navCustomize = findViewById(R.id.navCustomize);
        navLogout = findViewById(R.id.navLogout);

        messageList = new ArrayList<>();
        chatAdapter = new ChatAdapter(
                messageList,
                this,
                new ChatAdapter.OnFeedbackRequestedListener() {
                    @Override
                    public void onFeedbackRequested(
                            ChatMessage message,
                            String type
                    ) {
                        handleFeedback(message, type);
                    }
                },
                new ChatAdapter.OnUserActionListener() {
                    @Override
                    public void onEditRequested(ChatMessage message) {
                        handleEditMessage(message);
                    }

                    @Override
                    public void onCopyRequested(ChatMessage message) {
                        handleCopyMessage(message);
                    }

                    @Override
                    public void onRetryRequested(ChatMessage message) {
                        if (message != null && message.getText() != null) {
                            executeAiQuery(
                                    message.getText(),
                                    "",
                                    new ArrayList<>()
                            );
                        }
                    }

                    @Override
                    public void onUserMessageLongPressed(
                            View anchor,
                            ChatMessage message
                    ) {
                        showUserMessageActions(anchor, message);
                    }
                }
        );

            applySavedTheme();

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

        if (kruxModelCapsule != null) {
            kruxModelCapsule.setOnClickListener(v -> showKruxModelSelector());
        }

        if (btnCloseSidebar != null) btnCloseSidebar.setOnClickListener(v -> drawerLayout.closeDrawer(GravityCompat.START));
        btnAccount.setOnClickListener(v -> showAccountBottomSheet());
        btnPlus.setOnClickListener(v -> showPlusBottomSheet());
        btnSend.setOnClickListener(v -> {
            if (isGenerating) {
                ApiClient.cancelCurrentRequest();
            } else {
                sendMessage();
            }
        });
        btnMic.setOnClickListener(v -> toggleVoiceRecognition());
        if (btnStopMic != null) btnStopMic.setOnClickListener(v -> toggleVoiceRecognition());
        if (btnTtsControl != null) btnTtsControl.setOnClickListener(v -> toggleTtsPlayback());

        setupSidebarEvents();
        initSpeechRecognizer();
        initTextToSpeech();
        listenToFirebaseMessages();
        loadHistorySidebar();
        loadCurrentSession();
        syncCloudData();
    }

    private void showKruxModelSelector() {

        final android.app.Dialog dialog = new android.app.Dialog(this);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(16, 16, 16, 16);
        container.setBackgroundResource(R.drawable.bg_model_popup);

        TextView title = new TextView(this);
        title.setText("Modèle Krux");
        title.setTextColor(android.graphics.Color.WHITE);
        title.setTextSize(17);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(8, 4, 8, 12);

        container.addView(title);

        for (KruxModel model : KruxModel.values()) {

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(12, 10, 12, 10);
            row.setClickable(true);
            row.setFocusable(true);

            TextView name = new TextView(this);
            name.setText(model.getDisplayName());
            name.setTextColor(android.graphics.Color.WHITE);
            name.setTextSize(15);

            TextView info = new TextView(this);

            String tokenInfo;

            if (model.getContextTokens() >= 1000000) {
                tokenInfo = (model.getContextTokens() / 1000000) + "M tokens";
            } else {
                tokenInfo = (model.getContextTokens() / 1000) + "K tokens";
            }

            info.setText(tokenInfo);
            info.setTextColor(android.graphics.Color.rgb(148, 163, 184));
            info.setTextSize(12);

            row.addView(name);
            row.addView(info);

            if (model == selectedKruxModel) {
                name.setTypeface(null, android.graphics.Typeface.BOLD);
            }

            row.setOnClickListener(v -> {

                selectedKruxModel = model;

                updateKruxModelUI();

                dialog.dismiss();
            });

            container.addView(row);
        }

        dialog.setContentView(container);

        android.view.Window window = dialog.getWindow();

        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);

            android.view.WindowManager.LayoutParams params =
                    new android.view.WindowManager.LayoutParams();

            params.copyFrom(window.getAttributes());
            params.width =
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.82);
            params.height =
                    android.view.WindowManager.LayoutParams.WRAP_CONTENT;

            window.setAttributes(params);
        }

        dialog.show();
    }

    private void updateKruxModelUI() {
        if (selectedKruxModel == null) return;

        if (tvSelectedModel != null) {
            tvSelectedModel.setText(selectedKruxModel.getShortName());
        }

        if (tvTokenRemaining != null) {
            int tokens = selectedKruxModel.getContextTokens();

            if (tokens >= 1000000) {
                tvTokenRemaining.setText((tokens / 1000000) + "M");
            } else if (tokens >= 1000) {
                tvTokenRemaining.setText((tokens / 1000) + "K");
            } else {
                tvTokenRemaining.setText(String.valueOf(tokens));
            }
        }
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

            getSharedPreferences("krux_chat", MODE_PRIVATE)
                    .edit()
                    .putString("current_session_id", currentSessionId)
                    .apply();
        }

        message.setSessionId(currentSessionId);

        String sender = message.isUser() ? "user" : "ai";
        dbHelper.saveMessage(
                currentSessionId,
                sender,
                message.getText()
        );

        runOnUiThread(() -> {
            if (!messageList.contains(message)) {
                messageList.add(message);
                chatAdapter.notifyItemInserted(messageList.size() - 1);
            }

            rvChat.smoothScrollToPosition(messageList.size() - 1);
            loadHistorySidebar();
        });

        saveMessageToCloud(message, sender);
    }

    private void saveMessageToCloud(ChatMessage message, String sender) {
        if (currentUser == null) {
            FirebaseAuth.getInstance()
                    .signInAnonymously()
                    .addOnSuccessListener(authResult -> {
                        currentUser = authResult.getUser();

                        if (currentUser != null) {
                            saveMessageToCloud(message, sender);
                        }
                    });

            return;
        }

        java.util.Map<String, Object> cloudMessage =
                new java.util.HashMap<>();

        cloudMessage.put("id", message.getId());
        cloudMessage.put("sessionId", currentSessionId);
        cloudMessage.put("sender", sender);
        cloudMessage.put("message", message.getText());
        cloudMessage.put("timestamp", message.getTimestamp());

        if (message.getModel() != null) {
            cloudMessage.put("model", message.getModel());
        }

        db.collection("users")
                .document(currentUser.getUid())
                .collection("chats")
                .document(message.getId())
                .set(cloudMessage);
    }

    private void saveMemoryToCloud(String fact) {
        if (fact == null || fact.trim().isEmpty()) {
            return;
        }

        if (currentUser == null) {
            FirebaseAuth.getInstance()
                    .signInAnonymously()
                    .addOnSuccessListener(authResult -> {
                        currentUser = authResult.getUser();

                        if (currentUser != null) {
                            saveMemoryToCloud(fact);
                        }
                    });

            return;
        }

        String cleanFact = fact.trim();

        java.util.Map<String, Object> memory =
                new java.util.HashMap<>();

        memory.put("fact", cleanFact);
        memory.put(
                "createdAt",
                com.google.firebase.firestore.FieldValue.serverTimestamp()
        );

        db.collection("users")
                .document(currentUser.getUid())
                .collection("memory")
                .document(java.util.UUID.randomUUID().toString())
                .set(memory);
    }

    private void loadCurrentSession() {
        if (currentSessionId == null || currentSessionId.isEmpty()) {
            return;
        }

        List<ChatMessage> restored =
                dbHelper.getMessagesForSession(currentSessionId);

        messageList.clear();

        if (restored != null) {
            messageList.addAll(restored);
        }

        if (chatAdapter != null) {
            chatAdapter.notifyDataSetChanged();
        }
        updateWelcomePanel();

        if (rvChat != null && !messageList.isEmpty()) {
            rvChat.post(() ->
                    rvChat.scrollToPosition(messageList.size() - 1)
            );
        }
    }

    private void syncCloudData() {
        if (currentUser == null) {
            FirebaseAuth.getInstance()
                    .signInAnonymously()
                    .addOnSuccessListener(authResult -> {
                        currentUser = authResult.getUser();

                        if (currentUser != null) {
                            syncCloudData();
                        }
                    });

            return;
        }

        String uid = currentUser.getUid();

        db.collection("users")
                .document(uid)
                .collection("chats")
                .get()
                .addOnSuccessListener(snapshot -> {

                    for (com.google.firebase.firestore.DocumentSnapshot doc
                            : snapshot.getDocuments()) {

                        String sessionId = doc.getString("sessionId");

                        /*
                         * Les anciens messages n'avaient pas de sessionId.
                         * On les rattache à la session actuelle afin de
                         * récupérer l'historique existant sans le perdre.
                         */
                        if (sessionId == null || sessionId.trim().isEmpty()) {
                            sessionId = currentSessionId;
                        }

                        String sender = doc.getString("sender");
                        String text = doc.getString("message");

                        if (sender == null || text == null) {
                            continue;
                        }

                        if (!dbHelper.hasMessage(sessionId, sender, text)) {
                            dbHelper.saveMessage(
                                    sessionId,
                                    sender,
                                    text
                            );
                        }
                    }

                    loadCurrentSession();
                    loadHistorySidebar();
                })
                .addOnFailureListener(error ->
                        android.util.Log.e(
                                "KRUX_SYNC",
                                "Erreur synchronisation chats",
                                error
                        )
                );

        db.collection("users")
                .document(uid)
                .collection("memory")
                .get()
                .addOnSuccessListener(snapshot -> {

                    for (com.google.firebase.firestore.DocumentSnapshot doc
                            : snapshot.getDocuments()) {

                        String fact = doc.getString("fact");

                        if (fact == null || fact.trim().isEmpty()) {
                            continue;
                        }

                        fact = fact.trim();

                        if (!dbHelper.hasMemoryFact(fact)) {
                            dbHelper.addMemoryFact(fact);
                        }
                    }

                    android.util.Log.d(
                            "KRUX_SYNC",
                            "Mémoire cloud synchronisée."
                    );
                })
                .addOnFailureListener(error ->
                        android.util.Log.e(
                                "KRUX_SYNC",
                                "Erreur synchronisation mémoire",
                                error
                        )
                );
    }

    private void handleEditMessage(ChatMessage message) {
        if (message == null) {
            return;
        }

        String text = message.getText();

        if (text == null) {
            text = "";
        }

        etInput.setText(text);
        etInput.setSelection(etInput.length());
        etInput.requestFocus();
    }

    private void handleCopyMessage(ChatMessage message) {
        if (message == null) {
            return;
        }

        String text = message.getText();

        if (text == null || text.isEmpty()) {
            return;
        }

        android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager)
                        getSystemService(CLIPBOARD_SERVICE);

        if (clipboard != null) {
            android.content.ClipData clip =
                    android.content.ClipData.newPlainText(
                            "Message Krux",
                            text
                    );

            clipboard.setPrimaryClip(clip);

            Toast.makeText(
                    this,
                    "Message copié",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void setupWelcomePanel() {
        int[] promptIds = {
                R.id.welcomePromptOne,
                R.id.welcomePromptTwo,
                R.id.welcomePromptThree
        };

        for (int promptId : promptIds) {
            TextView prompt = findViewById(promptId);
            if (prompt != null) {
                prompt.setOnClickListener(v -> {
                    etInput.setText(((TextView) v).getText());
                    etInput.setSelection(etInput.length());
                    etInput.requestFocus();
                });
            }
        }

        if (welcomeNewChat != null) {
            welcomeNewChat.setOnClickListener(v -> startNewDiscussion());
        }
        updateWelcomePanel();
    }

    private void startNewDiscussion() {
        currentSessionId = "session_" + System.currentTimeMillis();
        getSharedPreferences("krux_chat", MODE_PRIVATE)
                .edit()
                .putString("current_session_id", currentSessionId)
                .apply();
        messageList.clear();
        chatAdapter.notifyDataSetChanged();
        updateWelcomePanel();
        if (welcomeScene != null) {
            welcomeScene.resetAnimation();
        }
        etInput.setText("");
        etInput.requestFocus();
    }

    private void updateWelcomePanel() {
        if (welcomePanel != null) {
            welcomePanel.setVisibility(
                    messageList == null || messageList.isEmpty()
                            ? View.VISIBLE
                            : View.GONE
            );
        }
    }

    private void sendMessage() {
        String prompt = etInput.getText().toString().trim();
        if (prompt.isEmpty()) return;

        ChatMessage userMessage = new ChatMessage(prompt, true);
        saveMessageToDatabase(userMessage);
        updateWelcomePanel();
        if (welcomeScene != null) {
            welcomeScene.setAnimating(false);
        }
        etInput.setText("");
        setKruxState(KruxState.THINKING);

        ToolRouter.Tool tool = ToolRouter.decide(prompt, isDeepSearchEnabled);

        if (tool == ToolRouter.Tool.WEB_SEARCH) {
            setKruxState(KruxState.SEARCHING);
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
            setKruxState(KruxState.GENERATING);
            executeAiQuery(prompt, "", new ArrayList<>());
        }
    }

    private void executeAiQuery(String userPrompt, String webContext, List<SearchResult> sources) {
        setKruxState(KruxState.GENERATING);

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

        ChatMessage aiMessage = new ChatMessage("", false, sources);
        aiMessage.setStreaming(true);

        runOnUiThread(() -> {
            messageList.add(aiMessage);
            chatAdapter.notifyItemInserted(messageList.size() - 1);
            rvChat.smoothScrollToPosition(messageList.size() - 1);
        });

        ApiClient.sendRequest(
                selectedKruxModel,
                systemPrompt,
                promptWithContext.toString(),
                new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(String rawResponse, String modelBrand) {
                runOnUiThread(() -> {
                    setGeneratingState(false);
                    setKruxState(KruxState.IDLE);
                });

                String cleanResponse = rawResponse;
                if (cleanResponse.contains("<REMEMBER>") && cleanResponse.contains("</REMEMBER>")) {
                    try {
                        int start = cleanResponse.indexOf("<REMEMBER>") + 10;
                        int end = cleanResponse.indexOf("</REMEMBER>");
                        if (end > start) {
                            String fact = cleanResponse.substring(start, end).trim();
                            setKruxState(KruxState.MEMORY);
                            dbHelper.addMemoryFact(fact);
                            saveMemoryToCloud(fact);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    cleanResponse = cleanResponse.replaceAll("<REMEMBER>.*?</REMEMBER>", "").trim();
                }

                aiMessage.setText(cleanResponse);
                aiMessage.setModel(modelBrand);
                aiMessage.setStreaming(false);

                runOnUiThread(() -> {
                    int position = messageList.indexOf(aiMessage);
                    if (position >= 0) {
                        chatAdapter.notifyItemChanged(position);
                    }
                });
                saveMessageToDatabase(aiMessage);
            }

            @Override
            public void onPartialResponse(String partialResponse, String modelBrand) {
                runOnUiThread(() -> {
                    aiMessage.setText(partialResponse);
                    aiMessage.setModel(modelBrand);
                    int position = messageList.indexOf(aiMessage);
                    if (position >= 0) {
                        chatAdapter.notifyItemChanged(position);
                        rvChat.smoothScrollToPosition(position);
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    setGeneratingState(false);
                    setKruxState(KruxState.IDLE);
                });
                aiMessage.setStreaming(false);
                aiMessage.setText("Erreur : " + errorMessage);

                runOnUiThread(() -> {
                    int position = messageList.indexOf(aiMessage);
                    if (position >= 0) {
                        chatAdapter.notifyItemChanged(position);
                    }
                });
                saveMessageToDatabase(aiMessage);
            }

            @Override
            public void onCancelled() {
                runOnUiThread(() -> {
                    setGeneratingState(false);
                    setKruxState(KruxState.IDLE);
                    aiMessage.setStreaming(false);
                    aiMessage.setText("");
                    int position = messageList.indexOf(aiMessage);
                    if (position >= 0) {
                        messageList.remove(position);
                        chatAdapter.notifyItemRemoved(position);
                    }
                });
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
        TextView optFiles = view.findViewById(R.id.optFiles);

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

        optFiles.setOnClickListener(v -> {
            dialog.dismiss();
            filePicker.launch(new String[]{"*/*"});
        });

        dialog.show();
    }

    private void initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

            speechRecognizer.setRecognitionListener(new RecognitionListener() {

                @Override
                public void onReadyForSpeech(Bundle params) {
                    resetVoiceBars();
                }

                @Override
                public void onBeginningOfSpeech() {
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                    if (!isListening) return;
                    updateVoiceBars(rmsdB);
                }

                @Override
                public void onBufferReceived(byte[] buffer) {
                }

                @Override
                public void onEndOfSpeech() {
                    // Ne pas arrêter l'interface ici.
                    // Une pause peut déclencher cet événement.
                }

                @Override
                public void onError(int error) {
                    stopVoiceUI();
                }

                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> matches =
                            results.getStringArrayList(
                                    SpeechRecognizer.RESULTS_RECOGNITION
                            );

                    if (matches != null && !matches.isEmpty()) {
                        String spoken = matches.get(0).trim();
                        String base = voiceBaseText == null
                                ? ""
                                : voiceBaseText.trim();

                        String finalText = base.isEmpty()
                                ? spoken
                                : spoken.isEmpty()
                                ? base
                                : base + " " + spoken;

                        etInput.setText(finalText);
                        etInput.setSelection(etInput.length());
                    }

                    stopVoiceUI();
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    ArrayList<String> matches =
                            partialResults.getStringArrayList(
                                    SpeechRecognizer.RESULTS_RECOGNITION
                            );

                    if (matches != null && !matches.isEmpty()) {
                        String spoken = matches.get(0).trim();
                        String base = voiceBaseText == null
                                ? ""
                                : voiceBaseText.trim();

                        String partialText = base.isEmpty()
                                ? spoken
                                : spoken.isEmpty()
                                ? base
                                : base + " " + spoken;

                        etInput.setText(partialText);
                        etInput.setSelection(etInput.length());
                    }
                }

                @Override
                public void onEvent(int eventType, Bundle params) {
                }
            });
        }
    }

    private void toggleVoiceRecognition() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISSION_AUDIO_CODE
            );
            return;
        }

        if (speechRecognizer == null) return;

        if (isListening) {
            speechRecognizer.stopListening();
            stopVoiceUI();
            return;
        }

        voiceBaseText = etInput.getText().toString();

        Intent intent = new Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                Locale.getDefault()
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                true
        );

        isListening = true;

        btnMic.setVisibility(View.GONE);

        if (btnStopMic != null) {
            btnStopMic.setVisibility(View.VISIBLE);
        }

        etInput.setVisibility(View.VISIBLE);

        if (llVoiceVisualizer != null) {
            llVoiceVisualizer.setVisibility(View.VISIBLE);
        }

        resetVoiceBars();

        speechRecognizer.startListening(intent);
    }

    private void stopVoiceUI() {
        isListening = false;

        btnMic.setVisibility(View.VISIBLE);

        if (btnStopMic != null) {
            btnStopMic.setVisibility(View.GONE);
        }

        if (llVoiceVisualizer != null) {
            llVoiceVisualizer.setVisibility(View.GONE);
        }

        etInput.setVisibility(View.VISIBLE);

        resetVoiceBars();
    }

    private void updateVoiceBars(float rmsdB) {
        if (!isListening) return;

        float level = (rmsdB + 2.0f) / 12.0f;
        level = Math.max(0.0f, Math.min(1.0f, level));

        voiceRmsLevel =
                voiceRmsLevel * 0.72f
                        + level * 0.28f;

        float normalized = voiceRmsLevel;

        int minHeight = dpToPx(7);
        int maxHeight = dpToPx(34);

        int h1 = (int) (
                minHeight
                        + normalized
                        * (maxHeight - minHeight)
                        * 0.72f
        );

        int h2 = (int) (
                minHeight
                        + normalized
                        * (maxHeight - minHeight)
                        * 1.00f
        );

        int h3 = (int) (
                minHeight
                        + normalized
                        * (maxHeight - minHeight)
                        * 0.84f
        );

        int h4 = (int) (
                minHeight
                        + normalized
                        * (maxHeight - minHeight)
                        * 0.92f
        );

        setBarHeight(waveBar1, h1);
        setBarHeight(waveBar2, h2);
        setBarHeight(waveBar3, h3);
        setBarHeight(waveBar4, h4);
    }

    private void resetVoiceBars() {
        voiceRmsLevel = 0.0f;

        int minHeight = dpToPx(7);

        setBarHeight(waveBar1, minHeight);
        setBarHeight(waveBar2, minHeight);
        setBarHeight(waveBar3, minHeight);
        setBarHeight(waveBar4, minHeight);
    }

    private void setBarHeight(View bar, int height) {
        if (bar == null) return;

        ViewGroup.LayoutParams params = bar.getLayoutParams();

        if (params == null) return;

        params.height = height;
        bar.setLayoutParams(params);
    }

    private int dpToPx(int dp) {
        return (int) (
                dp * getResources().getDisplayMetrics().density
                        + 0.5f
        );
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

    private void showUserMessageActions(View anchor, ChatMessage message) {
        if (anchor == null || message == null) {
            return;
        }

        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6));
        menu.setBackgroundColor(Color.rgb(25, 16, 42));

        PopupWindow popup = new PopupWindow(
                menu,
                dpToPx(220),
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true
        );
        popup.setBackgroundDrawable(new ColorDrawable(Color.rgb(25, 16, 42)));
        popup.setElevation(dpToPx(10));
        popup.setOutsideTouchable(true);

        addUserAction(menu, R.drawable.ic_copy, "Copier", () -> {
            popup.dismiss();
            handleCopyMessage(message);
        });
        addUserAction(menu, R.drawable.ic_edit, "Modifier", () -> {
            popup.dismiss();
            handleEditMessage(message);
        });
        addUserAction(menu, R.drawable.ic_retry, "Réessayer", () -> {
            popup.dismiss();
            if (message.getText() != null) {
                executeAiQuery(message.getText(), "", new ArrayList<>());
            }
        });

        popup.showAsDropDown(
                anchor,
                -dpToPx(220) + anchor.getWidth(),
                dpToPx(6)
        );
    }

    private void addUserAction(
            LinearLayout menu,
            int iconRes,
            String label,
            Runnable action
    ) {
        TextView item = new TextView(this);
        item.setText(label);
        item.setTextColor(Color.WHITE);
        item.setTextSize(14);
        item.setGravity(android.view.Gravity.CENTER_VERTICAL);
        item.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0);
        item.setCompoundDrawablePadding(dpToPx(12));
        item.setPadding(dpToPx(12), 0, dpToPx(12), 0);
        item.setMinHeight(dpToPx(46));
        item.setOnClickListener(v -> action.run());
        menu.addView(item);
    }

    private void showDiscussionSearch() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Mot ou phrase à rechercher");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Rechercher une discussion")
                .setView(input)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Rechercher", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
        ).setOnClickListener(v -> {
            String query = input.getText().toString().trim().toLowerCase(Locale.ROOT);
            if (query.isEmpty()) {
                input.setError("Saisis un mot à rechercher");
                return;
            }

            StringBuilder result = new StringBuilder();
            int matches = 0;
            for (ChatMessage message : messageList) {
                String text = message.getText() == null ? "" : message.getText();
                if (text.toLowerCase(Locale.ROOT).contains(query)) {
                    matches++;
                    result.append(matches)
                            .append(". ")
                            .append(text.replace('\n', ' '))
                            .append("\n\n");
                }
            }

            if (matches == 0) {
                result.append("Aucune discussion trouvée.");
            }

            new AlertDialog.Builder(this)
                    .setTitle(matches + " résultat(s)")
                    .setMessage(result.toString())
                    .setPositiveButton("Fermer", null)
                    .show();
            dialog.dismiss();
        }));

        dialog.show();
    }

    private void applySavedTheme() {
        SharedPreferences preferences = getSharedPreferences(
                "krux_theme",
                MODE_PRIVATE
        );
        String palette = preferences.getString("palette", "midnight");
        String font = preferences.getString("font", "sans-serif");
        String scene = preferences.getString("scene", "blackhole");
        float radius = preferences.getFloat("radius", 12f);
        float size = preferences.getFloat("size", 15f);
        int userBubble = parseThemeColor(
            preferences.getString("userBubble", "#120B24"),
            Color.rgb(18, 11, 36)
        );
        int aiBubble = parseThemeColor(
            preferences.getString("aiBubble", "#00000000"),
            Color.TRANSPARENT
        );
        int accent = parseThemeColor(
            preferences.getString("accent", "#A855F7"),
            Color.rgb(168, 85, 247)
        );

        int background = Color.rgb(7, 5, 15);
        int userText = Color.rgb(250, 247, 255);
        int aiText = Color.rgb(250, 247, 255);

        if ("graphite".equals(palette)) {
            background = Color.rgb(16, 18, 22);
            if (!preferences.contains("userBubble")) {
                userBubble = Color.rgb(38, 43, 52);
            }
            userText = Color.WHITE;
            aiText = Color.rgb(235, 239, 245);
        } else if ("ocean".equals(palette)) {
            background = Color.rgb(5, 16, 28);
            if (!preferences.contains("userBubble")) {
                userBubble = Color.rgb(10, 52, 76);
            }
            userText = Color.rgb(232, 248, 255);
            aiText = Color.rgb(225, 240, 248);
        }

        if (drawerLayout != null) {
            drawerLayout.setBackgroundColor(background);
        }
        if (welcomeScene != null) {
            welcomeScene.setScene(scene);
        }
        if (btnSend != null) {
            btnSend.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        }
        if (tvKruxStatus != null) {
            tvKruxStatus.setTextColor(accent);
        }
        if (welcomeNewChat != null) {
            welcomeNewChat.setBackgroundTintList(ColorStateList.valueOf(accent));
        }
        if (chatAdapter != null) {
            chatAdapter.applyTheme(
                    userBubble,
                    userText,
                    aiBubble,
                    aiText,
                    radius,
                    size,
                    font
            );
        }
    }

    private int parseThemeColor(String value, int fallback) {
        try {
            return Color.parseColor(value);
        } catch (Exception error) {
            return fallback;
        }
    }

    private void showCustomizationSheet() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dpToPx(20), dpToPx(18), dpToPx(20), dpToPx(12));

        TextView title = new TextView(this);
        title.setText("Personnaliser l’app");
        title.setTextSize(20);
        title.setTextColor(Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(title);

        TextView preview = new TextView(this);
        preview.setText("Aperçu de tes messages");
        preview.setTextColor(Color.WHITE);
        preview.setGravity(android.view.Gravity.CENTER_VERTICAL);
        preview.setPadding(dpToPx(14), 0, dpToPx(14), 0);
        content.addView(preview);

        RadioGroup palette = new RadioGroup(this);
        addChoice(palette, "Midnight violet", "midnight");
        addChoice(palette, "Graphite premium", "graphite");
        addChoice(palette, "Ocean focus", "ocean");
        content.addView(sectionLabel("Palette"));
        content.addView(palette);

        RadioGroup font = new RadioGroup(this);
        addChoice(font, "Sans moderne", "sans-serif");
        addChoice(font, "Serif éditoriale", "serif");
        addChoice(font, "Mono développeur", "monospace");
        content.addView(sectionLabel("Police"));
        content.addView(font);

        RadioGroup shape = new RadioGroup(this);
        addChoice(shape, "Compacte", "8");
        addChoice(shape, "Soft", "16");
        addChoice(shape, "Pill premium", "26");
        content.addView(sectionLabel("Forme des bulles"));
        content.addView(shape);

        RadioGroup userBubble = new RadioGroup(this);
        addChoice(userBubble, "Violet profond", "#120B24");
        addChoice(userBubble, "Bleu océan", "#0A344C");
        addChoice(userBubble, "Graphite", "#262B34");
        content.addView(sectionLabel("Bulle utilisateur"));
        content.addView(userBubble);

        RadioGroup aiBubble = new RadioGroup(this);
        addChoice(aiBubble, "Sans fond", "#00000000");
        addChoice(aiBubble, "Carte sombre", "#171126");
        addChoice(aiBubble, "Carte bleutée", "#102A3A");
        content.addView(sectionLabel("Bulle IA"));
        content.addView(aiBubble);

        RadioGroup accent = new RadioGroup(this);
        addChoice(accent, "Violet", "#A855F7");
        addChoice(accent, "Cyan", "#22D3EE");
        addChoice(accent, "Rose", "#FB7185");
        content.addView(sectionLabel("Couleur d’accent"));
        content.addView(accent);

        RadioGroup scene = new RadioGroup(this);
        addChoice(scene, "Trou noir futuriste", "blackhole");
        addChoice(scene, "Aurora orbitale", "aurora");
        addChoice(scene, "Minimal sombre", "minimal");
        content.addView(sectionLabel("Fond animé de l’accueil"));
        content.addView(scene);

        SeekBar size = new SeekBar(this);
        size.setMax(5);
        size.setProgress(1);
        content.addView(sectionLabel("Taille du texte"));
        content.addView(size);

        Button apply = new Button(this);
        apply.setText("Appliquer");
        content.addView(apply);

        RadioGroup.OnCheckedChangeListener previewListener =
            (group, checkedId) -> updateThemePreview(
                preview,
                selectedChoice(userBubble, "#120B24"),
                selectedChoice(shape, "12"),
                selectedChoice(accent, "#A855F7")
            );
        userBubble.setOnCheckedChangeListener(previewListener);
        shape.setOnCheckedChangeListener(previewListener);
        accent.setOnCheckedChangeListener(previewListener);
        updateThemePreview(preview, "#120B24", "12", "#A855F7");

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(content);
        apply.setOnClickListener(v -> {
            String paletteValue = selectedChoice(palette, "midnight");
            String fontValue = selectedChoice(font, "sans-serif");
            float radiusValue = Float.parseFloat(selectedChoice(shape, "12"));
            float sizeValue = 14f + size.getProgress();

            getSharedPreferences("krux_theme", MODE_PRIVATE)
                    .edit()
                    .putString("palette", paletteValue)
                    .putString("font", fontValue)
                    .putString("userBubble", selectedChoice(userBubble, "#120B24"))
                    .putString("aiBubble", selectedChoice(aiBubble, "#00000000"))
                    .putString("accent", selectedChoice(accent, "#A855F7"))
                    .putString("scene", selectedChoice(scene, "blackhole"))
                    .putFloat("radius", radiusValue)
                    .putFloat("size", sizeValue)
                    .apply();
            applySavedTheme();
            dialog.dismiss();
        });
        dialog.show();
    }

    private void updateThemePreview(
            TextView preview,
            String userBubble,
            String radius,
            String accent
    ) {
        preview.setTextColor(parseThemeColor(accent, Color.WHITE));
        GradientDrawable previewBackground = new GradientDrawable();
        previewBackground.setColor(parseThemeColor(userBubble, Color.DKGRAY));
        previewBackground.setCornerRadius(
                dpToPx((int) Float.parseFloat(radius))
        );
        preview.setBackground(previewBackground);
    }

    private TextView sectionLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(Color.rgb(168, 85, 247));
        label.setTextSize(12);
        label.setPadding(0, dpToPx(14), 0, dpToPx(2));
        return label;
    }

    private void addChoice(RadioGroup group, String label, String value) {
        RadioButton choice = new RadioButton(this);
        choice.setText(label);
        choice.setTag(value);
        choice.setTextColor(Color.WHITE);
        group.addView(choice);
    }

    private String selectedChoice(RadioGroup group, String fallback) {
        int checkedId = group.getCheckedRadioButtonId();
        if (checkedId == -1) return fallback;
        View checked = group.findViewById(checkedId);
        Object value = checked == null ? null : checked.getTag();
        return value == null ? fallback : value.toString();
    }

    private void setupSidebarEvents() {
        if (navSearchChats != null) {
            navSearchChats.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                showDiscussionSearch();
            });
        }
        if (navStudio != null) {
            navStudio.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                startActivity(new Intent(MainActivity.this, StudioActivity.class));
            });
        }
        if (navCustomize != null) {
            navCustomize.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                showCustomizationSheet();
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
                startNewDiscussion();
            });
        }
    }

    private void setKruxState(KruxState state) {
        if (state == null) {
            state = KruxState.IDLE;
        }

        kruxState = state;

        final KruxState displayState = state;

        runOnUiThread(() -> {
            if (kruxStatusContainer == null || tvKruxStatus == null) {
                return;
            }

            switch (displayState) {
                case THINKING:
                    tvKruxStatus.setText("Réflexion…");
                    kruxStatusContainer.setVisibility(View.VISIBLE);
                    break;

                case SEARCHING:
                    tvKruxStatus.setText("Recherche…");
                    kruxStatusContainer.setVisibility(View.VISIBLE);
                    break;

                case GENERATING:
                    tvKruxStatus.setText("Génération…");
                    kruxStatusContainer.setVisibility(View.VISIBLE);
                    break;

                case MEMORY:
                    tvKruxStatus.setText("Mise à jour de la mémoire…");
                    kruxStatusContainer.setVisibility(View.VISIBLE);
                    break;

                case ERROR:
                    tvKruxStatus.setText("Une erreur est survenue");
                    kruxStatusContainer.setVisibility(View.VISIBLE);
                    break;

                case LISTENING:
                case IDLE:
                default:
                    kruxStatusContainer.setVisibility(View.GONE);
                    break;
            }
        });
    }

    private void setGeneratingState(boolean generating) {
        isGenerating = generating;

        if (btnSend != null) {
            btnSend.setImageResource(
                generating ? R.drawable.ic_stop : R.drawable.ic_send
            );
            btnSend.setBackgroundResource(
                    generating
                            ? R.drawable.bg_send_button_active
                            : R.drawable.bg_send_button_round
            );
        }
    }

    private void loadHistorySidebar() {
        if (rvHistory == null) return;
        List<ChatSession> sessions = dbHelper.getAllSessions();

        HistoryAdapter historyAdapter = new HistoryAdapter(this, sessions, new HistoryAdapter.OnSessionActionListener() {
            @Override
            public void onSessionClick(ChatSession session) {
                currentSessionId = session.getId();
                getSharedPreferences("krux_chat", MODE_PRIVATE)
                        .edit()
                        .putString("current_session_id", currentSessionId)
                        .apply();
                messageList.clear();
                messageList.addAll(session.getMessages());
                chatAdapter.notifyDataSetChanged();
                updateWelcomePanel();
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
                    updateWelcomePanel();
                }
                loadHistorySidebar();
            }
        });

        rvHistory.setAdapter(historyAdapter);

        EditText etSearch = findViewById(R.id.etSearchHistory);
        if (etSearch != null) {
            etSearch.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterSessions(s.toString(), sessions, historyAdapter);
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
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


    private void handleFeedback(ChatMessage message, String type) {
        if (message == null) return;

        if ("positive".equals(type)) {
            saveFeedback(message, "positive", "");

            Toast.makeText(
                    this,
                    "Merci pour ton retour 👍",
                    Toast.LENGTH_SHORT
            ).show();

        } else if ("negative".equals(type)) {
            showFeedbackDialog(message);
        }
    }

    private void saveFeedback(
            ChatMessage message,
            String type,
            String comment
    ) {
        if (message == null || db == null) return;

        String userId = currentUser != null
                ? currentUser.getUid()
                : "anonymous";

        Feedback feedback = new Feedback(
                message.getId(),
                currentSessionId,
                userId,
                message.getModel(),
                type,
                comment
        );

        db.collection("feedbacks")
                .add(feedback)
                .addOnFailureListener(e ->
                        Toast.makeText(
                                this,
                                "Erreur lors de l'enregistrement du feedback",
                                Toast.LENGTH_SHORT
                        ).show()
                );
    }

    private void showFeedbackDialog(ChatMessage message) {
        View view = getLayoutInflater().inflate(
                R.layout.dialog_feedback,
                null
        );

        androidx.appcompat.app.AlertDialog dialog =
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setView(view)
                        .create();

        View incorrect =
                view.findViewById(R.id.feedbackReasonIncorrect);

        View incomplete =
                view.findViewById(R.id.feedbackReasonIncomplete);

        View irrelevant =
                view.findViewById(R.id.feedbackReasonIrrelevant);

        View style =
                view.findViewById(R.id.feedbackReasonStyle);

        View other =
                view.findViewById(R.id.feedbackReasonOther);

        incorrect.setOnClickListener(v -> {
            saveFeedback(
                    message,
                    "negative",
                    "Informations incorrectes"
            );
            dialog.dismiss();
        });

        incomplete.setOnClickListener(v -> {
            saveFeedback(
                    message,
                    "negative",
                    "Réponse incomplète"
            );
            dialog.dismiss();
        });

        irrelevant.setOnClickListener(v -> {
            saveFeedback(
                    message,
                    "negative",
                    "Réponse hors sujet"
            );
            dialog.dismiss();
        });

        style.setOnClickListener(v -> {
            saveFeedback(
                    message,
                    "negative",
                    "Style ou explication à améliorer"
            );
            dialog.dismiss();
        });

        other.setOnClickListener(v -> {
            saveFeedback(
                    message,
                    "negative",
                    "Autre"
            );
            dialog.dismiss();
        });

        dialog.show();
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

    private void handleSelectedFile(Uri uri) {
        try {
            String fileName = uri.getLastPathSegment();

            if (fileName == null || fileName.trim().isEmpty()) {
                fileName = "Fichier sélectionné";
            }

            String message =
                    "📎 Fichier sélectionné : " + fileName +
                    "\n\nKrux peut maintenant utiliser ce fichier comme pièce jointe.";

            Toast.makeText(
                    MainActivity.this,
                    "Fichier sélectionné",
                    Toast.LENGTH_SHORT
            ).show();

            etInput.setText(message);

        } catch (Exception e) {
            Toast.makeText(
                    MainActivity.this,
                    "Impossible de lire le fichier",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }


}
