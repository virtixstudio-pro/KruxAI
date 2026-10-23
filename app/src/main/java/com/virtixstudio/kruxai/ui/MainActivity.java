package com.virtixstudio.kruxai.ui;

import com.virtixstudio.kruxai.AppBuilder;
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
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ScrollView;
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
    private ImageButton btnMenu, btnPlus, btnMic, btnStopMic, btnSend, btnCloseSidebar, btnScrollBottom, btnTtsControl;
    private View navSearchChats, navStudio, navCustomize, navLogout;
    private TextView btnNewChat;

    // Sélecteur de modèle Krux
    private LinearLayout kruxModelCapsule;
    private TextView tvKruxName;
    private TextView tvSelectedModel;
    private TextView tvTokenRemaining;
    private KruxModel selectedKruxModel = KruxModel.KRUX_35_FLASH;
    private EditText etInput;
    private RecyclerView rvChat, rvHistory;
    private View welcomePanel;
    private EditText etSearchHistory;
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
    private String currentUserFirstName = "";
    private String currentUserEmail = "";
private String selectedFileName = "";
private String selectedFileContent = "";

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
    private ChatMessage activeAiMessage;
    private View kruxStatusContainer;
    private TextView tvKruxStatus;
    private TextView tvWelcomeTitle, tvWelcomeSubtitle;

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
        android.util.Log.e("KRUX_BOOT", "BOOT 01: super.onCreate");
        setContentView(R.layout.activity_main);
        KruxTheme.apply(this);
        android.util.Log.e("KRUX_BOOT", "BOOT 02: setContentView OK");

        ChatMessage.initializeContext(this);
        android.util.Log.e("KRUX_BOOT", "BOOT 03: ChatMessage context OK");

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();
        android.util.Log.e("KRUX_BOOT", "BOOT 04: FirebaseAuth OK, user=" + (currentUser != null));

        if (currentUser != null) {
            currentUserEmail = currentUser.getEmail() != null
                ? currentUser.getEmail()
                : "";

            db.collection("users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String firstName = document.getString("firstName");
                        if (firstName != null) {
                            currentUserFirstName = firstName.trim();
                        }
                    }
                    android.util.Log.d(
                        "KRUX_PROFILE",
                        "Profil chargé : firstName=" + currentUserFirstName
                            + ", email=" + currentUserEmail
                    );

                    runOnUiThread(() -> updateWelcomePanel());
                })
                .addOnFailureListener(e ->
                    android.util.Log.e("KRUX_PROFILE", "Échec chargement profil Firebase", e)
                );
        }

        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        webSearchEngine = new WebSearchEngine();
        android.util.Log.e("KRUX_BOOT", "BOOT 05: WebSearchEngine OK");

        String accountId = currentUser.getUid();

        dbHelper = new KruxDatabaseHelper(this, accountId);
        android.util.Log.e("KRUX_BOOT", "BOOT 06: DatabaseHelper OK");

        String sessionPrefsName = "krux_chat_" + accountId;

        currentSessionId = getSharedPreferences(
                sessionPrefsName,
                MODE_PRIVATE
        ).getString("current_session_id", null);

        if (currentSessionId == null || currentSessionId.isEmpty()) {
            currentSessionId = "session_" + System.currentTimeMillis();

            getSharedPreferences(
                    sessionPrefsName,
                    MODE_PRIVATE
            )
                    .edit()
                    .putString("current_session_id", currentSessionId)
                    .apply();
        }

        drawerLayout = findViewById(R.id.drawerLayout);
        btnMenu = findViewById(R.id.btnMenu);
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
        android.util.Log.e("KRUX_BOOT", "BOOT 07: updateKruxModelUI OK");

        etInput = findViewById(R.id.etInput);
        rvChat = findViewById(R.id.rvChat);
        rvHistory = findViewById(R.id.rvHistory);
        welcomePanel = findViewById(R.id.welcomePanel);
        tvWelcomeTitle = findViewById(R.id.tvWelcomeTitle);
        tvWelcomeSubtitle = findViewById(R.id.tvWelcomeSubtitle);
        welcomeScene = findViewById(R.id.welcomeScene);

        llVoiceVisualizer = findViewById(R.id.llVoiceVisualizer);
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

        etSearchHistory = findViewById(R.id.etSearchHistory);

        setupKruxSidebar();
        android.util.Log.e("KRUX_BOOT", "BOOT 09: setupKruxSidebar OK");
        android.util.Log.e("KRUX_BOOT", "BOOT 10: welcome UI OK");

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
                                    new ArrayList<>(),
                                    "",
                                    ""
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
        android.util.Log.e("KRUX_BOOT", "BOOT 11: setupSidebarEvents OK");
        initSpeechRecognizer();
        android.util.Log.e("KRUX_BOOT", "BOOT 12: initSpeechRecognizer OK");
        initTextToSpeech();
        android.util.Log.e("KRUX_BOOT", "BOOT 13: initTextToSpeech OK");
        listenToFirebaseMessages();
        android.util.Log.e("KRUX_BOOT", "BOOT 14: listenToFirebaseMessages OK");
        loadHistorySidebar();
        android.util.Log.e("KRUX_BOOT", "BOOT 15: loadHistorySidebar OK");
        loadCurrentSession();
        android.util.Log.e("KRUX_BOOT", "BOOT 16: loadCurrentSession OK");
        syncCloudData();
        android.util.Log.e("KRUX_BOOT", "BOOT 17: syncCloudData OK");
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
        if (currentUser == null || message == null) {
            android.util.Log.w(
                    "KRUX_SYNC",
                    "Sauvegarde locale ignorée : aucun compte authentifié."
            );
            return;
        }

        if (currentSessionId == null || currentSessionId.isEmpty()) {
            currentSessionId = "session_" + System.currentTimeMillis();

            getSharedPreferences("krux_chat_" + (currentUser != null ? currentUser.getUid() : "default"), MODE_PRIVATE)
                    .edit()
                    .putString("current_session_id", currentSessionId)
                    .apply();
        }

        message.setSessionId(currentSessionId);

        String sender = message.isUser() ? "user" : "ai";
        dbHelper.saveMessage(
                currentSessionId,
                sender,
                message.getText(),
                message.getTimestamp()
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
        if (currentUser == null || message == null) {
            android.util.Log.w(
                    "KRUX_SYNC",
                    "Sauvegarde cloud ignorée : aucun compte authentifié."
            );
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
                .set(cloudMessage)
                .addOnSuccessListener(unused ->
                        android.util.Log.d(
                                "KRUX_SYNC",
                                "Message synchronisé : " + message.getId()
                        )
                )
                .addOnFailureListener(e ->
                        android.util.Log.e(
                                "KRUX_SYNC",
                                "Échec synchronisation message : " + message.getId(),
                                e
                        )
                );
    }

    private void saveMemoryToCloud(
            String fact,
            Runnable onComplete
    ) {
        if (fact == null || fact.trim().isEmpty()) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        if (currentUser == null) {
            android.util.Log.w(
                    "KRUX_SYNC",
                    "Sauvegarde mémoire cloud ignorée : aucun compte authentifié."
            );

            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        String cleanFact = fact.trim();

        com.google.firebase.firestore.CollectionReference memoryCollection =
                db.collection("users")
                        .document(currentUser.getUid())
                        .collection("memory");

        memoryCollection
                .whereEqualTo("fact", cleanFact)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {

                    if (!snapshot.isEmpty()) {

                        String memoryId =
                                snapshot.getDocuments()
                                        .get(0)
                                        .getId();

                        java.util.Map<String, Object> update =
                                new java.util.HashMap<>();

                        update.put(
                                "updatedAt",
                                com.google.firebase.firestore.FieldValue.serverTimestamp()
                        );

                        memoryCollection
                                .document(memoryId)
                                .update(update)
                                .addOnSuccessListener(unused -> {

                                    android.util.Log.d(
                                            "KRUX_SYNC",
                                            "Mémoire Firestore mise à jour : "
                                                    + memoryId
                                    );

                                    if (onComplete != null) {
                                        onComplete.run();
                                    }
                                })
                                .addOnFailureListener(e -> {

                                    android.util.Log.e(
                                            "KRUX_SYNC",
                                            "Échec mise à jour mémoire Firestore : "
                                                    + memoryId,
                                            e
                                    );

                                    if (onComplete != null) {
                                        onComplete.run();
                                    }
                                });

                    } else {

                        java.util.Map<String, Object> memory =
                                new java.util.HashMap<>();

                        memory.put("fact", cleanFact);
                        memory.put(
                                "createdAt",
                                com.google.firebase.firestore.FieldValue.serverTimestamp()
                        );
                        memory.put(
                                "updatedAt",
                                com.google.firebase.firestore.FieldValue.serverTimestamp()
                        );

                        memoryCollection
                                .add(memory)
                                .addOnSuccessListener(documentReference -> {

                                    android.util.Log.d(
                                            "KRUX_SYNC",
                                            "Nouvelle mémoire Firestore : "
                                                    + documentReference.getId()
                                    );

                                    if (onComplete != null) {
                                        onComplete.run();
                                    }
                                })
                                .addOnFailureListener(e -> {

                                    android.util.Log.e(
                                            "KRUX_SYNC",
                                            "Échec création mémoire Firestore",
                                            e
                                    );

                                    if (onComplete != null) {
                                        onComplete.run();
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {

                    android.util.Log.e(
                            "KRUX_SYNC",
                            "Échec recherche mémoire Firestore",
                            e
                    );

                    if (onComplete != null) {
                        onComplete.run();
                    }
                });
    }

    private void loadCurrentSession() {
        if (currentUser == null) {
            return;
        }

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
            android.util.Log.w("KRUX_SYNC", "Aucun utilisateur Firebase connecté.");
            return;
        }

        final String accountId = currentUser.getUid();

        db.collection("users")
                .document(accountId)
                .collection("chats")
                .get()
                .addOnSuccessListener(snapshot -> {

                    String latestSessionId = null;
                    long latestTimestamp = Long.MIN_VALUE;

                    for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) {

                        String sessionId = doc.getString("sessionId");

                        if (sessionId == null || sessionId.trim().isEmpty()) {
                            sessionId = currentSessionId;
                        }

                        String sender = doc.getString("sender");
                        String text = doc.getString("message");

                        if (sender == null || text == null) {
                            continue;
                        }

                        long timestamp = 0L;
                        Object rawTimestamp = doc.get("timestamp");

                        if (rawTimestamp instanceof Number) {
                            timestamp = ((Number) rawTimestamp).longValue();
                        } else if (rawTimestamp instanceof com.google.firebase.Timestamp) {
                            timestamp = ((com.google.firebase.Timestamp) rawTimestamp)
                                    .toDate()
                                    .getTime();
                        }

                        if (!dbHelper.hasMessage(sessionId, sender, text, timestamp)) {
                            dbHelper.saveMessage(sessionId, sender, text, timestamp);
                        }

                        if (timestamp >= latestTimestamp) {
                            latestTimestamp = timestamp;
                            latestSessionId = sessionId;
                        }
                    }

                    /*
                     * Ne pas remplacer la session courante si elle existe déjà.
                     * La synchronisation cloud restaure les données, mais ne doit
                     * pas déplacer silencieusement l'utilisateur vers une autre
                     * discussion.
                     */
                    String savedSessionId = getSharedPreferences(
                            "krux_chat_" + accountId,
                            MODE_PRIVATE
                    ).getString("current_session_id", null);

                    if (savedSessionId != null && !savedSessionId.trim().isEmpty()) {
                        currentSessionId = savedSessionId;
                    } else if (latestSessionId != null
                            && !latestSessionId.trim().isEmpty()) {

                        currentSessionId = latestSessionId;

                        getSharedPreferences(
                                "krux_chat_" + accountId,
                                MODE_PRIVATE
                        )
                                .edit()
                                .putString("current_session_id", currentSessionId)
                                .apply();
                    }

                    loadCurrentSession();
                    loadHistorySidebar();

                    db.collection("users")
                            .document(accountId)
                            .collection("memory")
                            .get()
                            .addOnSuccessListener(memorySnapshot -> {

                                for (com.google.firebase.firestore.DocumentSnapshot doc
                                        : memorySnapshot.getDocuments()) {

                                    String fact = doc.getString("fact");

                                    if (fact != null
                                            && !fact.trim().isEmpty()
                                            && !dbHelper.hasMemoryFact(fact)) {
                                        dbHelper.addMemoryFact(fact);
                                    }
                                }

                                android.util.Log.d(
                                        "KRUX_SYNC",
                                        "Synchronisation terminée pour " + accountId
                                );

                            })
                            .addOnFailureListener(e ->
                                    android.util.Log.e(
                                            "KRUX_SYNC",
                                            "Erreur synchronisation mémoire",
                                            e
                                    )
                            );

                })
                .addOnFailureListener(e ->
                        android.util.Log.e(
                                "KRUX_SYNC",
                                "Erreur synchronisation conversations",
                                e
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



    private void startNewDiscussion() {
        if (currentUser == null) {
            return;
        }

        currentSessionId = "session_" + System.currentTimeMillis();

        if (currentUser != null) {
            getSharedPreferences(
                    "krux_chat_" + currentUser.getUid(),
                    MODE_PRIVATE
            )
                    .edit()
                    .putString("current_session_id", currentSessionId)
                    .apply();
        }
        messageList.clear();
        chatAdapter.notifyDataSetChanged();
        updateWelcomePanel();
        if (welcomeScene != null) {
            welcomeScene.resetAnimation();
        }
        etInput.setText("");
        etInput.requestFocus();
    }

    private void exportConversation() {
        if (messageList == null || messageList.isEmpty()) {
            Toast.makeText(this, "Rien à exporter : la discussion est vide.", Toast.LENGTH_SHORT).show();
            return;
        }
        java.text.DateFormat df = java.text.DateFormat.getDateTimeInstance(
                java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT);
        StringBuilder sb = new StringBuilder("# Discussion KRUX AI\n");
        sb.append("Exportée le ").append(df.format(new java.util.Date())).append("\n\n");
        for (ChatMessage m : messageList) {
            String text = m.getText();
            if (text == null || text.trim().isEmpty()) continue;
            sb.append(m.isUser() ? "**Moi**" : "**KRUX**");
            long ts = m.getTimestamp();
            if (ts > 100000000000L) sb.append(" · ").append(df.format(new java.util.Date(ts)));
            else if (ts > 1000000000L) sb.append(" · ").append(df.format(new java.util.Date(ts * 1000L)));
            sb.append("\n\n").append(text.trim()).append("\n\n---\n\n");
        }
        String out = sb.toString();
        if (out.length() > 100000) {
            out = "(début de la discussion tronqué)\n\n" + out.substring(out.length() - 100000);
        }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "Discussion KRUX AI");
        i.putExtra(Intent.EXTRA_TEXT, out);
        startActivity(Intent.createChooser(i, "Exporter la discussion"));
    }

    private void setupStartCards() {
        if (tvWelcomeSubtitle == null || !(tvWelcomeSubtitle.getParent() instanceof android.widget.LinearLayout)) return;
        final android.widget.LinearLayout parent = (android.widget.LinearLayout) tvWelcomeSubtitle.getParent();
        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            if ("krux_cards".equals(parent.getChildAt(i).getTag())) parent.removeViewAt(i);
        }
        android.widget.LinearLayout cards = new android.widget.LinearLayout(this);
        cards.setTag("krux_cards");
        cards.setOrientation(android.widget.LinearLayout.VERTICAL);
        cards.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        cards.setPadding(0, dpToPx(18), 0, 0);

        final String[] labels = {
                "Fais-moi une app de liste de courses",
                "Explique-moi un concept simplement",
                "Aide-moi à corriger mon code",
                "Cherche sur internet…"};
        final String[] fills = {
                "Fais-moi une app de liste de courses",
                "Explique-moi simplement : ",
                "Aide-moi à corriger ce code : ",
                "Cherche sur internet : "};

        for (int i = 0; i < labels.length; i++) {
            final String fill = fills[i];
            TextView chip = new TextView(this);
            chip.setText(labels[i]);
            chip.setTextSize(13);
            chip.setTextColor(KruxTheme.get(this, "text"));
            chip.setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10));
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setColor(KruxTheme.get(this, "input"));
            gd.setCornerRadius(dpToPx(20));
            chip.setBackground(gd);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dpToPx(8);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> {
                android.widget.EditText in = findViewById(R.id.etInput);
                if (in == null) return;
                in.setText(fill);
                in.setSelection(in.getText().length());
                in.requestFocus();
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(in, 0);
            });
            cards.addView(chip);
        }
        parent.addView(cards, parent.indexOfChild(tvWelcomeSubtitle) + 1);
    }

    private void updateWelcomePanel() {
        if (welcomePanel == null) {
            return;
        }

        boolean empty = messageList == null || messageList.isEmpty();

        welcomePanel.setVisibility(empty ? View.VISIBLE : View.GONE);

        if (!empty && welcomeScene != null) {
            welcomeScene.setAnimating(false);
        }

        if (tvWelcomeTitle == null || tvWelcomeSubtitle == null) {
            return;
        }

        String name = currentUserFirstName != null
                ? currentUserFirstName.trim()
                : "";

        if (name.isEmpty() && currentUserEmail != null) {
            String email = currentUserEmail.trim();
            int at = email.indexOf('@');

            if (at > 0) {
                name = email.substring(0, at).trim();
            } else if (!email.isEmpty()) {
                name = email;
            }
        }

        if (name.isEmpty()) {
            name = "toi";
        }

        int hour = java.util.Calendar.getInstance()
                .get(java.util.Calendar.HOUR_OF_DAY);

        String title;
        String subtitle;

        if (hour >= 5 && hour < 12) {
            title = "Bonjour " + name;
            subtitle = "Prêt à commencer quelque chose ?";
        } else if (hour >= 12 && hour < 18) {
            title = "Bon après-midi " + name;
            subtitle = "On avance sur quoi aujourd’hui ?";
        } else if (hour >= 18 && hour < 24) {
            title = "Bonsoir " + name;
            subtitle = "On fait quoi de beau ce soir ?";
        } else {
            title = "Bonne nuit " + name;
            subtitle = "Encore debout ? On termine quelque chose ?";
        }

        setupStartCards();
        tvWelcomeTitle.setText(title);
        tvWelcomeSubtitle.setText(subtitle);
    }

    private void sendMessage() {
        String prompt = etInput.getText().toString().trim();
        if (prompt.isEmpty()) return;

        final String fileNameForRequest = selectedFileName;
        final String fileContentForRequest = selectedFileContent;

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
                    executeAiQuery(
                            prompt,
                            formattedContext,
                            results,
                            fileNameForRequest,
                            fileContentForRequest
                    );
                }

                @Override
                public void onError(String error) {
                    executeAiQuery(
                            prompt,
                            "",
                            new ArrayList<>(),
                            fileNameForRequest,
                            fileContentForRequest
                    );
                }
            });
        } else {
            setKruxState(KruxState.GENERATING);
            executeAiQuery(
                    prompt,
                    "",
                    new ArrayList<>(),
                    fileNameForRequest,
                    fileContentForRequest
            );
        }
    }

    private List<String> selectRelevantMemories(
            String userPrompt,
            List<String> memoryFacts
    ) {
        List<String> selected = new java.util.ArrayList<>();
        if (memoryFacts == null || memoryFacts.isEmpty()) {
            return selected;
        }

        java.util.Set<String> queryWords = new java.util.HashSet<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("[\\p{L}\\p{N}]{3,}")
                .matcher(userPrompt == null ? "" : userPrompt.toLowerCase(java.util.Locale.ROOT));

        while (matcher.find()) {
            queryWords.add(matcher.group());
        }

        java.util.Set<String> stopWords = new java.util.HashSet<>(
                java.util.Arrays.asList(
                        "avec", "dans", "pour", "mais", "donc", "cette",
                        "cela", "quel", "quelle", "quels", "quelles",
                        "comment", "pourquoi", "faire", "être", "avoir",
                        "est-ce", "plus", "moins", "peux", "peut",
                        "vous", "nous", "the", "and", "for", "with"
                )
        );
        queryWords.removeAll(stopWords);

        class ScoredMemory {
            final String fact;
            final int score;
            ScoredMemory(String fact, int score) {
                this.fact = fact;
                this.score = score;
            }
        }

        List<ScoredMemory> ranked = new java.util.ArrayList<>();
        for (String fact : memoryFacts) {
            if (fact == null || fact.trim().isEmpty()) {
                continue;
            }

            String normalized = fact.toLowerCase(java.util.Locale.ROOT);
            int score = 0;
            for (String word : queryWords) {
                if (normalized.contains(word)) {
                    score++;
                }
            }
            if (score > 0) {
                ranked.add(new ScoredMemory(fact.trim(), score));
            }
        }

        java.util.Collections.sort(ranked, (a, b) ->
                Integer.compare(b.score, a.score));

        // En cas de correspondance, garder au plus six souvenirs.
        for (ScoredMemory item : ranked) {
            if (selected.size() >= 6) {
                break;
            }
            String fact = item.fact;
            if (fact.length() > 350) {
                fact = fact.substring(0, 347) + "...";
            }
            selected.add(fact);
        }

        // Sans correspondance lexicale, garder seulement trois souvenirs
        // récents au sens de l'ordre renvoyé par la base.
        if (selected.isEmpty()) {
            for (int i = memoryFacts.size() - 1;
                    i >= 0 && selected.size() < 3; i--) {
                String fact = memoryFacts.get(i);
                if (fact != null && !fact.trim().isEmpty()) {
                    fact = fact.trim();
                    if (fact.length() > 350) {
                        fact = fact.substring(0, 347) + "...";
                    }
                    selected.add(fact);
                }
            }
        }

        return selected;
    }

    private void executeAiQuery(
            String userPrompt,
            String webContext,
            List<SearchResult> sources,
            String fileName,
            String fileContent
    ) {
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
        List<String> relevantMemories = selectRelevantMemories(userPrompt, memoryFacts);
        if (!relevantMemories.isEmpty()) {
            historyBuilder.append("\nSouvenirs pertinents sur l'utilisateur :\n");
            for (String fact : relevantMemories) {
                historyBuilder.append("- ").append(fact).append("\n");
            }
        }

        String systemPrompt = new SystemPromptBuilder().withThinking(isThinkingMode)
                .withKruxModel(
                    selectedKruxModel != null
                        ? selectedKruxModel.getDisplayName()
                        : "KRUX Prime"
                )
                .withUserProfile(currentUserFirstName, currentUserEmail)
                .withHistory(historyBuilder.toString())
                .build();

        StringBuilder promptWithContext = new StringBuilder();

        if (fileContent != null && !fileContent.trim().isEmpty()) {
            promptWithContext
                    .append("FICHIER JOINT : ")
                    .append(fileName != null ? fileName : "fichier")
                    .append("\n")
                    .append("Analyse réellement le contenu ci-dessous. ")
                    .append("Ne prétends pas avoir lu le fichier si son contenu n'est pas présent.")
                    .append("\n--- DÉBUT DU FICHIER ---\n")
                    .append(fileContent)
                    .append("--- FIN DU FICHIER ---\n\n");
        }

        promptWithContext
                .append("Utilisateur: ")
                .append(userPrompt);

        setGeneratingState(true);

        selectedFileName = "";
        selectedFileContent = "";

        ChatMessage aiMessage = new ChatMessage("", false, sources);
        aiMessage.setStreaming(true);
        activeAiMessage = aiMessage;

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

                String cleanResponse = stripReflexion(rawResponse);

                java.util.regex.Matcher kruxMedia = java.util.regex.Pattern
                        .compile("<KRUX_TOOL>(?:\\s|\\\\n)*(image_search|video_search)(?:\\s|\\\\n)*(.*?)(?:\\s|\\\\n)*</KRUX_TOOL>",
                                java.util.regex.Pattern.DOTALL)
                        .matcher(cleanResponse);
                if (kruxMedia.find()) {
                    final boolean wantVideo = "video_search".equals(kruxMedia.group(1));
                    final String mediaQuery = kruxMedia.group(2).replace("\\n", " ").trim();
                    cleanResponse = cleanResponse.replace(kruxMedia.group(0), "").trim();
                    if (cleanResponse.isEmpty()) {
                        cleanResponse = wantVideo ? "Je cherche des vidéos libres de droits." : "Je cherche des images libres de droits.";
                    }
                    if (!mediaQuery.isEmpty()) {
                        runOnUiThread(() -> MediaGallery.show(MainActivity.this, wantVideo, mediaQuery));
                    }
                }

                java.util.regex.Matcher kruxCreate = java.util.regex.Pattern
                        .compile("<KRUX_TOOL>(?:\\s|\\\\n)*create_app(?:\\s|\\\\n)*(.*?)(?:\\s|\\\\n)*</KRUX_TOOL>",
                                java.util.regex.Pattern.DOTALL)
                        .matcher(cleanResponse);
                if (kruxCreate.find()) {
                    final String appDesc = kruxCreate.group(1).replace("\\n", " ").trim();
                    cleanResponse = cleanResponse.replace(kruxCreate.group(0), "").trim();
                    if (cleanResponse.isEmpty()) {
                        cleanResponse = "Je lance la création de ton application.";
                    }
                    if (!appDesc.isEmpty()) {
                        runOnUiThread(() -> autoCreateApp(appDesc));
                    }
                }

                boolean memoryUpdateStarted = false;

                if (cleanResponse.contains("<REMEMBER>")
                        && cleanResponse.contains("</REMEMBER>")) {

                    try {
                        int start =
                                cleanResponse.indexOf("<REMEMBER>") + 10;

                        int end =
                                cleanResponse.indexOf("</REMEMBER>");

                        if (end > start) {

                            String fact =
                                    cleanResponse
                                            .substring(start, end)
                                            .trim();

                            if (!fact.isEmpty()) {

                                memoryUpdateStarted = true;

                                setKruxState(KruxState.MEMORY);

                                boolean alreadyExists =
                                        dbHelper.hasMemoryFact(fact);

                                if (!alreadyExists) {
                                    dbHelper.addMemoryFact(fact);
                                }

                                boolean verifiedLocally =
                                        dbHelper.hasMemoryFact(fact);

                                android.util.Log.d(
                                        "KRUX_MEMORY",
                                        "SQLite mémoire "
                                                + (verifiedLocally
                                                ? "vérifiée"
                                                : "NON vérifiée")
                                                + " : "
                                                + fact
                                );

                                saveMemoryToCloud(
                                        fact,
                                        () -> setKruxState(KruxState.IDLE)
                                );
                            }

                        }

                    } catch (Exception e) {

                        android.util.Log.e(
                                "KRUX_MEMORY",
                                "Erreur pendant la mise à jour mémoire",
                                e
                        );

                        setKruxState(KruxState.IDLE);
                    }

                    cleanResponse =
                            cleanResponse
                                    .replaceAll(
                                            "<REMEMBER>.*?</REMEMBER>",
                                            ""
                                    )
                                    .trim();
                }

                if (!memoryUpdateStarted) {
                    setKruxState(KruxState.IDLE);
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
                    aiMessage.setText(hideTool(stripReflexion(partialResponse)));
                    KruxState wantState = isReflecting(partialResponse) ? KruxState.THINKING : KruxState.GENERATING;
                    if (kruxState != wantState) setKruxState(wantState);
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
                    setKruxState(KruxState.ERROR);
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

    private String quotaWeek() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        return c.getWeekYear() + "-W" + c.get(java.util.Calendar.WEEK_OF_YEAR);
    }

    private int quotaUsed() {
        android.content.SharedPreferences sp = getSharedPreferences("krux_builder", MODE_PRIVATE);
        return quotaWeek().equals(sp.getString("quota_week", "")) ? sp.getInt("quota_used", 0) : 0;
    }

    private void consumeQuota() {
        android.content.SharedPreferences sp = getSharedPreferences("krux_builder", MODE_PRIVATE);
        sp.edit().putString("quota_week", quotaWeek()).putInt("quota_used", quotaUsed() + 1).apply();
    }

    private void autoCreateApp(final String desc) {
        String token = "";
        int quota = 2;
        try {
            java.io.InputStream is = getAssets().open("krux_builder.json");
            byte[] buf = new byte[is.available()];
            is.read(buf);
            is.close();
            org.json.JSONObject cfg = new org.json.JSONObject(new String(buf, java.nio.charset.StandardCharsets.UTF_8));
            token = cfg.optString("token", "").trim();
            quota = cfg.optInt("weekly_quota", 2);
        } catch (Exception ignored) { }
        if (token.isEmpty()) {
            Toast.makeText(this, "La création d'apps n'est pas configurée dans cette version de KRUX.", Toast.LENGTH_LONG).show();
            return;
        }
        int used = quotaUsed();
        if (used >= quota) {
            Toast.makeText(this, "Limite atteinte : " + quota + " créations par semaine. Elles reviennent la semaine prochaine.", Toast.LENGTH_LONG).show();
            return;
        }
        final String fToken = token;
        String shown = desc.length() > 300 ? desc.substring(0, 300) + "…" : desc;
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Créer cette application ?")
                .setMessage(shown + "\n\nCréations restantes cette semaine : " + (quota - used) + "/" + quota
                        + "\nApplication simple : 25 fichiers maximum.")
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Lancer", (d, w) -> startAppBuild(fToken, desc))
                .show();
    }

    private void saveBuiltApp(java.io.File apk, String repoUrl) {
        try {
            android.content.SharedPreferences sp = getSharedPreferences("krux_builder", MODE_PRIVATE);
            org.json.JSONArray old = new org.json.JSONArray(sp.getString("apps", "[]"));
            org.json.JSONArray arr = new org.json.JSONArray();
            arr.put(new org.json.JSONObject().put("repo", repoUrl).put("apk", apk.getAbsolutePath())
                    .put("time", System.currentTimeMillis()));
            for (int i = 0; i < old.length(); i++) {
                org.json.JSONObject o = old.getJSONObject(i);
                if (arr.length() < 10) arr.put(o);
                else new java.io.File(o.optString("apk")).delete();
            }
            sp.edit().putString("apps", arr.toString()).apply();
        } catch (Exception e) {
            android.util.Log.e("KRUX_BUILD", "saveBuiltApp", e);
        }
    }

    private void startAppBuild(String token, String desc) {
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        int pad = dpToPx(24);
        box.setPadding(pad, pad, pad, dpToPx(8));
        box.setBackgroundColor(KruxTheme.get(this, "bg"));

        final KruxDotsView anim = new KruxDotsView(this);
        anim.setLayoutParams(new android.widget.LinearLayout.LayoutParams(dpToPx(120), dpToPx(44)));
        anim.setColor(KruxTheme.get(this, "accent"));
        anim.setActive(true);
        box.addView(anim);

        final TextView title = new TextView(this);
        title.setText("J'écris et je compile ton app");
        title.setTextSize(17);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(KruxTheme.get(this, "text"));
        title.setGravity(android.view.Gravity.CENTER);
        title.setPadding(0, dpToPx(14), 0, dpToPx(6));
        box.addView(title);

        final TextView step = new TextView(this);
        step.setText("Démarrage…");
        step.setTextSize(13);
        step.setTextColor(KruxTheme.get(this, "text2"));
        step.setGravity(android.view.Gravity.CENTER);
        box.addView(step);

        final TextView hint = new TextView(this);
        hint.setText("Ça prend 2 à 5 minutes. Attends, et ne ferme pas KRUX.");
        hint.setTextSize(11);
        hint.setTextColor(KruxTheme.get(this, "text2"));
        hint.setGravity(android.view.Gravity.CENTER);
        hint.setPadding(0, dpToPx(12), 0, 0);
        box.addView(hint);

        final android.animation.ObjectAnimator pulse =
                android.animation.ObjectAnimator.ofFloat(title, "alpha", 1f, 0.45f);
        pulse.setDuration(900);
        pulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        pulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);

        final androidx.appcompat.app.AlertDialog progress =
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setView(box)
                        .setCancelable(false)
                        .setPositiveButton("Fermer", null)
                        .create();
        if (progress.getWindow() != null) {
            progress.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(KruxTheme.get(this, "bg")));
        }
        progress.show();
        final android.widget.Button closeBtn = progress.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
        closeBtn.setEnabled(false);
        closeBtn.setTextColor(KruxTheme.get(this, "accent"));
        pulse.start();

        final boolean[] consumed = {false};
        java.io.File outDir = getExternalFilesDir("generated");

        AppBuilder.run(token, desc, outDir,
                (system, prompt) -> {
                    final String[] out = new String[2];
                    final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                    ApiClient.sendRequest(
                            selectedKruxModel,
                            system,
                            prompt,
                            new ApiClient.ApiCallback() {
                                @Override
                                public void onSuccess(String response, String modelBrand) {
                                    out[0] = response;
                                    latch.countDown();
                                }

                                @Override
                                public void onError(String friendlyMessage) {
                                    out[1] = friendlyMessage;
                                    latch.countDown();
                                }

                                @Override
                                public void onCancelled() {
                                    out[1] = "Requête annulée";
                                    latch.countDown();
                                }
                            });
                    if (!latch.await(240, java.util.concurrent.TimeUnit.SECONDS)) {
                        throw new Exception("L'IA a mis trop de temps à répondre");
                    }
                    if (out[0] == null) throw new Exception(out[1]);
                    return out[0];
                },
                new AppBuilder.Listener() {
                    @Override
                    public void onStatus(final String status) {
                        if (!consumed[0] && status.startsWith("Création du repo")) {
                            consumed[0] = true;
                            consumeQuota();
                        }
                        runOnUiThread(() -> {
                            String t = null;
                            if (status.startsWith("Génération")) t = "J'écris ton application…";
                            else if (status.startsWith("Création") || status.startsWith("Envoi")) t = "J'envoie le code sur GitHub…";
                            else if (status.startsWith("Build échoué") || status.startsWith("Correction")) t = "Je corrige les erreurs…";
                            else if (status.contains("ompilation")) t = "Je compile ton application…";
                            else if (status.startsWith("Téléchargement")) t = "Je récupère ton APK…";
                            if (t != null) title.setText(t);
                            step.setText(status.startsWith("Création du repo") ? "Création du projet…" : status);
                        });
                    }

                    @Override
                    public void onDone(final java.io.File apk, final String repoUrl) {
                        runOnUiThread(() -> {
                            pulse.cancel();
                            title.setAlpha(1f);
                            anim.setActive(false);
                            title.setText("Ton application est prête");
                            step.setText("Android va te proposer de l'installer.");
                            hint.setText("Retrouve-la plus tard dans le menu + puis Mes apps.");
                            closeBtn.setEnabled(true);
                            saveBuiltApp(apk, repoUrl);
                            installApk(apk);
                        });
                    }

                    @Override
                    public void onError(final String message) {
                        android.util.Log.e("KRUX_BUILD", String.valueOf(message));
                        runOnUiThread(() -> {
                            pulse.cancel();
                            title.setAlpha(1f);
                            anim.setActive(false);
                            title.setText("Je n'ai pas pu finir");
                            step.setText(friendlyBuildError(message));
                            hint.setText("");
                            closeBtn.setEnabled(true);
                        });
                    }
                });
    }

    private String friendlyBuildError(String m) {
        if (m == null) return "Erreur inconnue.";
        if (m.contains("GitHub 401")) return "Le service de création d'apps de KRUX est momentanément indisponible. Réessaie plus tard.";
        if (m.contains("GitHub 403") || m.contains("GitHub 429")) return "Le quota de création d'apps de KRUX est atteint pour le moment. Réessaie plus tard.";
        return "Erreur : " + m;
    }

    private void installApk(java.io.File apk) {
        try {
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", apk);
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Installation impossible : " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showPlusBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_plus, null);
        dialog.setContentView(view);

        TextView optLearning = view.findViewById(R.id.optLearning);
        TextView optDeepSearch = view.findViewById(R.id.optDeepSearch);
        TextView optThinking = view.findViewById(R.id.optThinking);
        TextView optFiles = view.findViewById(R.id.optFiles);

        TextView optExport = view.findViewById(R.id.optExport);
        optExport.setOnClickListener(v -> {
            dialog.dismiss();
            exportConversation();
        });

        TextView optMyApps = view.findViewById(R.id.optMyApps);
        optMyApps.setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(new Intent(MainActivity.this, MyAppsActivity.class));
        });

        TextView optColors = view.findViewById(R.id.optColors);
        optColors.setOnClickListener(v -> {
            dialog.dismiss();
            KruxTheme.showEditor(this, () -> applySavedTheme());
        });

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
                executeAiQuery(
                        message.getText(),
                        "",
                        new ArrayList<>(),
                        "",
                        ""
                );
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
        String palette = preferences.getString("palette", "noir");
        String font = preferences.getString("font", "sans-serif");
        String scene = preferences.getString("scene", "blackhole");
        float radius = getThemeFloat(preferences, "radius", 12f);
        float size = getThemeFloat(preferences, "size", 15f);
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

        if ("noir".equals(palette)) {
            background = Color.BLACK;
            if (!preferences.contains("userBubble")) {
                userBubble = Color.rgb(34, 34, 34);
            }
            userText = Color.WHITE;
            aiText = Color.WHITE;
        } else if ("blanc".equals(palette)) {
            background = Color.WHITE;
            if (!preferences.contains("userBubble")) {
                userBubble = Color.rgb(226, 226, 226);
            }
            userText = androidx.core.graphics.ColorUtils.calculateLuminance(userBubbif (KruxTheme.hasOverride(this, "bg")) background = KruxTheme.get(this, "bg");
            userText = androidx.core.graphics.ColorUtils.calculateLuminance(userBubbif (KruxTheme.hasOverride(this, "bubble_user")) userBubble = KruxTheme.get(this, "bubble_user");
            userText = androidx.core.graphics.ColorUtils.calculateLuminance(userBubbif (KruxTheme.hasOverride(this, "text")) {
            userText = androidx.core.graphics.ColorUtils.calculateLuminance(userBubb    userText = KruxTheme.get(this, "text");
            userText = androidx.core.graphics.ColorUtils.calculateLuminance(userBubb    aiText = KruxTheme.get(this, "text");
            userText = androidx.core.graphics.ColorUtils.calculateLuminance(userBubb}

            userText = androidx.core.graphics.ColorUtils.calculateLuminance(userBubble) > 0.5 ? Color.BLACK : Color.WHITE;
            boolean aiDark = Color.alpha(aiBubble) >= 128 && androidx.core.graphics.ColorUtils.calculateLuminance(aiBubble) <= 0.5;
            aiText = aiDark ? Color.WHITE : Color.BLACK;
        }

        if (drawerLayout != null) {
            String wallpaperValue = preferences.getString("custom_wallpaper", null);
            boolean wallpaperApplied = false;

            if (wallpaperValue != null && !wallpaperValue.trim().isEmpty()) {
                try {
                    Uri wallpaperUri = Uri.parse(wallpaperValue);

                    try (java.io.InputStream inputStream =
                                 getContentResolver().openInputStream(wallpaperUri)) {

                        android.graphics.drawable.Drawable wallpaper =
                                android.graphics.drawable.Drawable.createFromStream(
                                        inputStream,
                                        wallpaperValue
                                );

                        if (wallpaper != null) {
                            drawerLayout.setBackground(wallpaper);
                            wallpaperApplied = true;
                        }
                    }
                } catch (Exception error) {
                    android.util.Log.e(
                            "KRUX_THEME",
                            "Impossible d'appliquer le fond personnalisé",
                            error
                    );
                }
            }

            if (!wallpaperApplied) {
                drawerLayout.setBackgroundColor(background);
            }
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

    private float getThemeFloat(
            SharedPreferences preferences,
            String key,
            float fallback
    ) {
        try {
            Object value = preferences.getAll().get(key);
            if (value instanceof Number) {
                return ((Number) value).floatValue();
            }
            if (value instanceof String) {
                return Float.parseFloat(((String) value).trim());
            }
        } catch (RuntimeException error) {
            android.util.Log.w(
                    "KRUX_THEME",
                    "Préférence numérique invalide : " + key,
                    error
            );
        }
        return fallback;
    }

    private int parseThemeColor(String value, int fallback) {
        try {
            return Color.parseColor(value);
        } catch (Exception error) {
            return fallback;
        }
    }

        private Uri selectedWallpaperUri = null;

    private void showCustomizationSheet() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(17, 19, 24));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(
                dpToPx(20),
                dpToPx(18),
                dpToPx(20),
                dpToPx(12)
        );
        content.setBackgroundColor(Color.rgb(17, 19, 24));

        scrollView.addView(content);

        root.addView(
                scrollView,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                )
        );

        TextView title = new TextView(this);
        title.setText("Personnaliser l’app");
        title.setTextSize(20);
        title.setTextColor(Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(title);

        // --- BLOC APERÇU STYLE WHATSAPP ---
        LinearLayout previewCard = new LinearLayout(this);
        previewCard.setOrientation(LinearLayout.VERTICAL);
        previewCard.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, dpToPx(12), 0, dpToPx(16));
        previewCard.setLayoutParams(cardParams);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#0F0D15"));
        cardBg.setCornerRadius(dpToPx(16));
        cardBg.setStroke(dpToPx(1), Color.parseColor("#2A2634"));
        previewCard.setBackground(cardBg);
        previewCard.setVisibility(View.GONE);

        // Mini Bulle Utilisateur
        TextView previewUserBubble = new TextView(this);
        previewUserBubble.setText("Krux, montre-moi le style.");
        previewUserBubble.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
        LinearLayout.LayoutParams userParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        userParams.gravity = android.view.Gravity.END;
        userParams.setMargins(dpToPx(32), 0, 0, dpToPx(8));
        previewUserBubble.setLayoutParams(userParams);
        previewCard.addView(previewUserBubble);

        // Mini Bulle IA
        TextView previewAiBubble = new TextView(this);
        previewAiBubble.setText("Aperçu en direct activé !");
        previewAiBubble.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
        LinearLayout.LayoutParams aiParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        aiParams.gravity = android.view.Gravity.START;
        aiParams.setMargins(0, 0, dpToPx(32), dpToPx(6));
        previewAiBubble.setLayoutParams(aiParams);
        previewCard.addView(previewAiBubble);

        // Mini bloc de code
        TextView previewCodeBlock = new TextView(this);
        previewCodeBlock.setText("System.out.println(\"KruxAI Live\");");
        previewCodeBlock.setTextSize(11);
        previewCodeBlock.setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6));
        LinearLayout.LayoutParams codeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        codeParams.setMargins(dpToPx(8), 0, dpToPx(32), 0);
        previewCodeBlock.setLayoutParams(codeParams);
        previewCard.addView(previewCodeBlock);

        content.addView(previewCard);

        // --- SECTIONS DE SÉLECTION ---
        RadioGroup palette = new RadioGroup(this);
        addChoice(palette, "Midnight violet", "midnight");
        addChoice(palette, "Graphite premium", "graphite");
        addChoice(palette, "Ocean focus", "ocean");
        addChoice(palette, "Noir pur", "noir");
        addChoice(palette, "Blanc clair", "blanc");
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
        addChoice(userBubble, "Gris clair", "#E2E2E2");
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
        addChoice(accent, "Blanc", "#FFFFFF");
        content.addView(sectionLabel("Couleur d’accent"));
        content.addView(accent);

        RadioGroup scene = new RadioGroup(this);
        addChoice(scene, "Trou noir futuriste", "blackhole");
        addChoice(scene, "Aurora orbitale", "aurora");
        addChoice(scene, "Minimal sombre", "minimal");
        content.addView(sectionLabel("Fond animé de l’accueil"));
        content.addView(scene);

        // Bouton Fond d écran personnalisé
        content.addView(sectionLabel("Fond d’écran personnalisé"));
        Button btnPickWallpaper = new Button(this);
        btnPickWallpaper.setText("Choisir une image de fond");
        btnPickWallpaper.setTextColor(Color.WHITE);
        btnPickWallpaper.setBackgroundColor(Color.parseColor("#1F192E"));
        btnPickWallpaper.setOnClickListener(v -> openImagePicker());
        content.addView(btnPickWallpaper);

        SeekBar size = new SeekBar(this);
        size.setMax(5);
        size.setProgress(1);
        content.addView(sectionLabel("Taille du texte"));
        content.addView(size);

        // Restaurer les choix actuellement sauvegardés
        SharedPreferences savedTheme = getSharedPreferences(
                "krux_theme",
                MODE_PRIVATE
        );

        selectChoice(
                palette,
                savedTheme.getString("palette", "noir")
        );

        selectChoice(
                font,
                savedTheme.getString("font", "sans-serif")
        );

        selectChoice(
                shape,
                String.valueOf(savedTheme.getAll().containsKey("radius") ? savedTheme.getAll().get("radius") : 16f)
        );

        selectChoice(
                userBubble,
                savedTheme.getString("userBubble", "#120B24")
        );

        selectChoice(
                aiBubble,
                savedTheme.getString("aiBubble", "#00000000")
        );

        selectChoice(
                accent,
                savedTheme.getString("accent", "#A855F7")
        );

        selectChoice(
                scene,
                savedTheme.getString("scene", "blackhole")
        );

        float savedSize = getThemeFloat(savedTheme, "size", 15f);
        int savedProgress = Math.round(savedSize - 14f);
        size.setProgress(Math.max(0, Math.min(5, savedProgress)));

        Button apply = new Button(this);
        apply.setText("Appliquer");

        LinearLayout actionBar = new LinearLayout(this);
        actionBar.setOrientation(LinearLayout.VERTICAL);
        actionBar.setPadding(
                dpToPx(20),
                dpToPx(8),
                dpToPx(20),
                dpToPx(12)
        );

        actionBar.addView(
                apply,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dpToPx(52)
                )
        );

        root.addView(
                actionBar,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        // Écouteurs pour mise à jour instantanée
        Runnable updatePreviewAction = () -> updateThemePreview(
                previewUserBubble,
                previewAiBubble,
                previewCodeBlock,
                selectedChoice(userBubble, "#120B24"),
                selectedChoice(aiBubble, "#00000000"),
                selectedChoice(shape, "16"),
                selectedChoice(accent, "#A855F7"),
                selectedChoice(font, "sans-serif"),
                14f + size.getProgress()
        );

        RadioGroup.OnCheckedChangeListener previewListener = (group, checkedId) -> updatePreviewAction.run();
        userBubble.setOnCheckedChangeListener(previewListener);
        aiBubble.setOnCheckedChangeListener(previewListener);
        shape.setOnCheckedChangeListener(previewListener);
        accent.setOnCheckedChangeListener(previewListener);
        font.setOnCheckedChangeListener(previewListener);
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean b) { updatePreviewAction.run(); }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        updatePreviewAction.run();

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(root);
        View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) {
            bottomSheet.setBackgroundColor(Color.TRANSPARENT);
        }

        apply.setOnClickListener(v -> {
            String paletteValue = selectedChoice(palette, "noir");
            String fontValue = selectedChoice(font, "sans-serif");
            float radiusValue = Float.parseFloat(selectedChoice(shape, "16"));
            float sizeValue = 14f + size.getProgress();

            SharedPreferences.Editor editor = getSharedPreferences("krux_theme", MODE_PRIVATE).edit();
            editor.putString("palette", paletteValue)
                    .putString("font", fontValue)
                    .putString("userBubble", selectedChoice(userBubble, "#120B24"))
                    .putString("aiBubble", selectedChoice(aiBubble, "#00000000"))
                    .putString("accent", selectedChoice(accent, "#A855F7"))
                    .putString("scene", selectedChoice(scene, "blackhole"))
                    .putFloat("radius", radiusValue)
                    .putFloat("size", sizeValue);

            if (selectedWallpaperUri != null) {
                editor.putString("custom_wallpaper", selectedWallpaperUri.toString());
            }

            editor.apply();
            applySavedTheme();
            dialog.dismiss();
        });
        dialog.show();
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );
        startActivityForResult(intent, 1002);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != 1002 || resultCode != RESULT_OK || data == null) {
            return;
        }

        Uri uri = data.getData();

        if (uri == null) {
            Toast.makeText(
                    this,
                    "Aucune image sélectionnée",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        selectedWallpaperUri = uri;

        try {
            int takeFlags = data.getFlags()
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            getContentResolver().takePersistableUriPermission(
                    uri,
                    takeFlags
            );
        } catch (SecurityException error) {
            android.util.Log.w(
                    "KRUX_THEME",
                    "Permission persistante non disponible",
                    error
            );
        }

        getSharedPreferences("krux_theme", MODE_PRIVATE)
                .edit()
                .putString("custom_wallpaper", uri.toString())
                .apply();

        applySavedTheme();

        Toast.makeText(
                this,
                "Fond d’écran appliqué",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void updateThemePreview(
            TextView userBubbleView,
            TextView aiBubbleView,
            TextView codeBlockView,
            String userBgColor,
            String aiBgColor,
            String radiusStr,
            String accentColorStr,
            String fontStr,
            float textSize
    ) {
        int radiusPx = dpToPx((int) Float.parseFloat(radiusStr));
        int accentColor = parseThemeColor(accentColorStr, Color.parseColor("#A855F7"));
        int userColor = parseThemeColor(userBgColor, Color.parseColor("#120B24"));
        int aiColor = parseThemeColor(aiBgColor, Color.TRANSPARENT);

        GradientDrawable userBg = new GradientDrawable();
        userBg.setColor(userColor);
        userBg.setCornerRadius(radiusPx);
        userBubbleView.setBackground(userBg);
        userBubbleView.setTextColor(Color.WHITE);
        userBubbleView.setTextSize(textSize);

        GradientDrawable aiBg = new GradientDrawable();
        aiBg.setColor(aiColor == Color.TRANSPARENT ? Color.parseColor("#14101D") : aiColor);
        aiBg.setCornerRadius(radiusPx);
        aiBubbleView.setBackground(aiBg);
        aiBubbleView.setTextColor(Color.WHITE);
        aiBubbleView.setTextSize(textSize);

        GradientDrawable codeBg = new GradientDrawable();
        codeBg.setColor(Color.parseColor("#08060C"));
        codeBg.setCornerRadius(dpToPx(8));
        codeBg.setStroke(dpToPx(1), accentColor);
        codeBlockView.setBackground(codeBg);
        codeBlockView.setTextColor(accentColor);

        android.graphics.Typeface tf = android.graphics.Typeface.SANS_SERIF;
        if ("serif".equals(fontStr)) tf = android.graphics.Typeface.SERIF;
        else if ("monospace".equals(fontStr)) tf = android.graphics.Typeface.MONOSPACE;

        userBubbleView.setTypeface(tf);
        aiBubbleView.setTypeface(tf);
        codeBlockView.setTypeface(android.graphics.Typeface.MONOSPACE);
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

    private void selectChoice(RadioGroup group, String value) {
        if (group == null || value == null) {
            return;
        }

        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);

            if (child instanceof RadioButton) {
                Object tag = child.getTag();

                if (tag != null && value.equals(tag.toString())) {
                    group.check(child.getId());
                    return;
                }
            }
        }
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

    private String hideTool(String s) {
        if (s == null) return "";
        int a = s.indexOf("<KRUX_TOOL>");
        return a < 0 ? s : s.substring(0, a).trim();
    }

    private String stripReflexion(String s) {
        if (s == null) return "";
        int a = s.indexOf("<REFLEXION>");
        if (a < 0) return s;
        int b = s.indexOf("</REFLEXION>", a);
        if (b < 0) return s.substring(0, a).trim();
        return (s.substring(0, a) + s.substring(b + 12)).trim();
    }

    private boolean isReflecting(String s) {
        return s != null && s.contains("<REFLEXION>") && !s.contains("</REFLEXION>");
    }

    private void setKruxState(KruxState state) {
        if (state == null) {
            state = KruxState.IDLE;
        }

        kruxState = state;

        final KruxState displayState = state;

        runOnUiThread(() -> {

            if (chatAdapter == null || activeAiMessage == null) {
                return;
            }

            String status;

            switch (displayState) {

                case THINKING:
                    status = "Réflexion…";
                    break;

                case SEARCHING:
                    status = "Recherche sur le Web…";
                    break;

                case GENERATING:
                    status = "Génération…";
                    break;

                case MEMORY:
                    status = "Mise à jour de la mémoire…";
                    break;

                case ERROR:
                    status = "Une erreur est survenue";
                    break;

                case LISTENING:
                case IDLE:
                default:

                    final String completedId =
                            activeAiMessage.getId();

                    chatAdapter.setKruxAnswerStatus(
                            completedId,
                            "Terminé"
                    );

                    new android.os.Handler(
                            android.os.Looper.getMainLooper()
                    ).postDelayed(() -> {

                        if (activeAiMessage != null
                                && completedId.equals(
                                        activeAiMessage.getId()
                                )) {

                            chatAdapter.clearKruxAnswerStatus(
                                    completedId
                            );
                        }

                    }, 900);

                    return;
            }

            chatAdapter.setKruxAnswerStatus(
                    activeAiMessage.getId(),
                    status
            );
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
                getSharedPreferences("krux_chat_" + (currentUser != null ? currentUser.getUid() : "default"), MODE_PRIVATE)
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
            String fileName = null;

            android.database.Cursor cursor =
                    getContentResolver().query(
                            uri,
                            null,
                            null,
                            null,
                            null
                    );

            if (cursor != null) {
                int nameIndex =
                        cursor.getColumnIndex(
                                android.provider.OpenableColumns.DISPLAY_NAME
                        );

                if (cursor.moveToFirst() && nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex);
                }

                cursor.close();
            }

            if (fileName == null || fileName.trim().isEmpty()) {
                fileName = uri.getLastPathSegment();
            }

            if (fileName == null || fileName.trim().isEmpty()) {
                fileName = "fichier";
            }

            String lowerName = fileName.toLowerCase(java.util.Locale.ROOT);

            boolean supported =
                    lowerName.endsWith(".txt")
                    || lowerName.endsWith(".java")
                    || lowerName.endsWith(".xml")
                    || lowerName.endsWith(".json")
                    || lowerName.endsWith(".csv")
                    || lowerName.endsWith(".md")
                    || lowerName.endsWith(".html")
                    || lowerName.endsWith(".htm")
                    || lowerName.endsWith(".css")
                    || lowerName.endsWith(".js")
                    || lowerName.endsWith(".ts")
                    || lowerName.endsWith(".kt")
                    || lowerName.endsWith(".kts")
                    || lowerName.endsWith(".gradle")
                    || lowerName.endsWith(".properties")
                    || lowerName.endsWith(".yml")
                    || lowerName.endsWith(".yaml")
                    || lowerName.endsWith(".sql")
                    || lowerName.endsWith(".c")
                    || lowerName.endsWith(".cpp")
                    || lowerName.endsWith(".h")
                    || lowerName.endsWith(".hpp")
                    || lowerName.endsWith(".py")
                    || lowerName.endsWith(".sh");

            if (!supported) {
                selectedFileName = "";
                selectedFileContent = "";

                Toast.makeText(
                        MainActivity.this,
                        "Ce type de fichier n'est pas encore pris en charge",
                        Toast.LENGTH_LONG
                ).show();

                return;
            }

            java.io.InputStream inputStream =
                    getContentResolver().openInputStream(uri);

            if (inputStream == null) {
                throw new Exception("Impossible d'ouvrir le fichier");
            }

            java.io.BufferedReader reader =
                    new java.io.BufferedReader(
                            new java.io.InputStreamReader(
                                    inputStream,
                                    java.nio.charset.StandardCharsets.UTF_8
                            )
                    );

            StringBuilder content = new StringBuilder();
            String line;
            int maxChars = 150000;

            while ((line = reader.readLine()) != null) {
                if (content.length() + line.length() + 1 > maxChars) {
                    content.append("\n[CONTENU TRONQUÉ À 150 000 CARACTÈRES]");
                    break;
                }

                content.append(line).append("\n");
            }

            reader.close();
            inputStream.close();

            selectedFileName = fileName;
            selectedFileContent = content.toString();

            Toast.makeText(
                    MainActivity.this,
                    "Fichier prêt : " + fileName,
                    Toast.LENGTH_SHORT
            ).show();

        } catch (Exception e) {
            selectedFileName = "";
            selectedFileContent = "";

            android.util.Log.e(
                    "KRUX_FILE",
                    "Impossible de lire le fichier",
                    e
            );

            Toast.makeText(
                    MainActivity.this,
                    "Impossible de lire le fichier",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void setupKruxSidebar() {
        if (drawerLayout == null) {
            return;
        }

        if (btnMenu != null) {
            btnMenu.setOnClickListener(v -> {
                if (!drawerLayout.isDrawerOpen(Gravity.LEFT)) {
                    drawerLayout.openDrawer(Gravity.LEFT);
                }
            });
        }

        if (btnCloseSidebar != null) {
            btnCloseSidebar.setOnClickListener(v ->
                    drawerLayout.closeDrawer(Gravity.LEFT)
            );
        }

        if (btnNewChat != null) {
            btnNewChat.setOnClickListener(v -> {
                startNewDiscussion();
                drawerLayout.closeDrawer(Gravity.LEFT);
            });
        }

        if (navSearchChats != null) {
            navSearchChats.setOnClickListener(v -> {
                if (etSearchHistory == null) {
                    return;
                }

                etSearchHistory.setVisibility(
                        etSearchHistory.getVisibility() == View.VISIBLE
                                ? View.GONE
                                : View.VISIBLE
                );

                if (etSearchHistory.getVisibility() == View.VISIBLE) {
                    etSearchHistory.requestFocus();
                }
            });
        }

        if (navLogout != null) {
            navLogout.setOnClickListener(v -> {
                drawerLayout.closeDrawer(Gravity.LEFT);

                if (mAuth != null) {
                    mAuth.signOut();
                }

                startActivity(
                        new Intent(
                                MainActivity.this,
                                LoginActivity.class
                        )
                );

                finish();
            });
        }

        if (navStudio != null) {
            navStudio.setOnClickListener(v -> {
                drawerLayout.closeDrawer(Gravity.LEFT);

                Toast.makeText(
                        MainActivity.this,
                        "Studio Krux — bientôt disponible",
                        Toast.LENGTH_SHORT
                ).show();
            });
        }
    }



}
