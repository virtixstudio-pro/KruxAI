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
    private KruxTerminalView terminal;
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
    private WelcomeGlobeView welcomeGlobe;
    private LinearLayout llVoiceVisualizer;
    private View waveBar1, waveBar2, waveBar3, waveBar4;

    private ChatAdapter chatAdapter;
    private List<ChatMessage> messageList;
    private WebSearchEngine webSearchEngine;
    private KruxDatabaseHelper dbHelper;
    private com.virtixstudio.kruxai.core.MemoryRepository memoryRepo;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private String currentUserFirstName = "";
    private String currentUserEmail = "";
private AttachmentStrip attachmentStrip;
private String selectedFileName = "";
private String selectedFileContent = "";

    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;

    /** Écran Paramètres : renvoie une action (export, personnalisation) à exécuter ici, car elle dépend de la discussion en cours. */
    private final androidx.activity.result.ActivityResultLauncher<Intent> settingsLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), r -> {
                applySavedTheme();
                Intent data = r.getData();
                String action = data == null ? null : data.getStringExtra(SettingsActivity.EXTRA_ACTION);
                if (SettingsActivity.ACTION_EXPORT.equals(action)) exportConversation();
                else if (SettingsActivity.ACTION_CUSTOMIZE.equals(action)) showCustomizationSheet();
            });
    private boolean isListening = false;
    private String voiceBaseText = "";
    private boolean isTtsSpeaking = false;
    private boolean ttsPaused = false;
    private String ttsText = "";
    private int ttsChunk = 0;
    private int ttsTotal = 0;
    private float voiceRmsLevel = 0.0f;

    private boolean isLearningMode = false;
    private boolean isDeepSearchEnabled = true;
    private boolean isThinkingMode = false; // raisonnement autonome : plus de bouton, Krux décide seul
    private String currentSessionId;
    private boolean isGenerating = false;
    private KruxState kruxState = KruxState.IDLE;
    private ChatMessage activeAiMessage;
    private View kruxStatusContainer;
    private TextView tvKruxStatus;
    private TextView tvWelcomeTitle, tvWelcomeSubtitle;

private static final int MAX_IMAGES = 2;
private final java.util.ArrayList<String> selectedImagesB64 = new java.util.ArrayList<>();
private final java.util.ArrayList<android.graphics.Bitmap> selectedThumbs = new java.util.ArrayList<>();
private java.util.List<String> pendingRequestImages = new java.util.ArrayList<>();
private int replaceImageIndex = -1;
private android.net.Uri cameraUri;

private final ActivityResultLauncher<String> imagePicker =
        registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> { if (uri != null) handleSelectedImage(uri); }
        );

private final ActivityResultLauncher<android.net.Uri> cameraLauncher =
        registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                ok -> { if (ok != null && ok && cameraUri != null) handleSelectedImage(cameraUri); }
        );

private final ActivityResultLauncher<String> imageReplacePicker =
        registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> { if (uri != null) handleSelectedImage(uri, replaceImageIndex); replaceImageIndex = -1; }
        );

private final ActivityResultLauncher<android.net.Uri> cameraReplaceLauncher =
        registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                ok -> { if (ok != null && ok && cameraUri != null) handleSelectedImage(cameraUri, replaceImageIndex); replaceImageIndex = -1; }
        );

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
        refreshCloudQuota();
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
        memoryRepo = new com.virtixstudio.kruxai.core.MemoryRepository(dbHelper);
        memoryRepo.setOnChange(() -> {
            if (currentUser != null) {
                com.virtixstudio.kruxai.core.CloudSync.markMemoryReplace(this, currentUser);
            }
        });
        android.util.Log.e("KRUX_BOOT", "BOOT 06: DatabaseHelper OK");

        String sessionPrefsName = "krux_chat_" + accountId;

        currentSessionId = getSharedPreferences(
                sessionPrefsName,
                MODE_PRIVATE
        ).getString("current_session_id", null);

        // Lancement à froid : on ouvre sur une nouvelle conversation (savedInstanceState != null = rotation, thème, etc. : on garde la session).
        if (savedInstanceState == null || currentSessionId == null || currentSessionId.isEmpty()) {
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
        terminal = findViewById(R.id.kruxTerminal);
        initNetwork();
        new Thread(() -> { try { if (dbHelper.removeDuplicateMessages() > 0) runOnUiThread(this::loadHistorySidebar); } catch (Exception ignore) { } }).start();
        rvHistory = findViewById(R.id.rvHistory);
        welcomePanel = findViewById(R.id.welcomePanel);
        setupFloatingChrome();
        tvWelcomeTitle = findViewById(R.id.tvWelcomeTitle);
        tvWelcomeSubtitle = findViewById(R.id.tvWelcomeSubtitle);
        welcomeGlobe = findViewById(R.id.welcomeGlobe);
        welcomeScene = null; // la boule animée de l'accueil est retirée : tous les usages plus bas testent null

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
        chatAdapter.setWaitingRetryListener(this::retryWaiting);
        chatAdapter.setContinueListener(this::continueStopped);

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

        btnMenu.setOnClickListener(v -> { refreshQuotaView(); drawerLayout.openDrawer(GravityCompat.START); });

        View btnNewTop = findViewById(R.id.btnNewChatTop);
        if (btnNewTop != null) btnNewTop.setOnClickListener(v -> startNewDiscussion());
        View btnMoreTop = findViewById(R.id.btnMoreTop);
        if (btnMoreTop != null) btnMoreTop.setOnClickListener(this::showMoreMenu);

        if (kruxModelCapsule != null) {
            kruxModelCapsule.setOnClickListener(v -> showKruxModelSelector());
        }

        if (btnCloseSidebar != null) btnCloseSidebar.setOnClickListener(v -> drawerLayout.closeDrawer(GravityCompat.START));
        btnPlus.setOnClickListener(v -> showPlusBottomSheet());
        android.widget.TextView tvDisclaimer = findViewById(R.id.tvDisclaimer);
        if (tvDisclaimer != null) {
            tvDisclaimer.setPaintFlags(tvDisclaimer.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
            tvDisclaimer.setOnClickListener(v -> LinkOpener.open(this, PrivacyActivity.TIPS_URL));
        }
        btnSend.setOnClickListener(v -> {
            if (isGenerating) {
                ApiClient.cancelCurrentRequest();
            } else if (etInput.getText().toString().trim().isEmpty() && !selectedImagesB64.isEmpty()) {
                etInput.setText("Que vois-tu sur cette image ?");
                sendMessage();
            } else if (etInput.getText().toString().trim().isEmpty()) {
                launchLive();
            } else {
                sendMessage();
            }
        });
        etInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable e) { refreshSendIcon(); }
        });
        refreshSendIcon();
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
        if (etInput != null) etInput.post(() -> handleModifyIntent(getIntent()));
    }

    /** Petit menu sous la capsule du modèle : 3 modèles en vue, le reste se déroule avec « Afficher plus de modèles… ». */
    private void showKruxModelSelector() {
        final android.app.Dialog dialog = new android.app.Dialog(this);
        final float d = getResources().getDisplayMetrics().density;
        final int screenW = getResources().getDisplayMetrics().widthPixels;
        final int screenH = getResources().getDisplayMetrics().heightPixels;
        final int width = Math.min((int) (290 * d), (int) (screenW * 0.86f));
        final int pad = (int) (10 * d);
        final int maxH = (int) (screenH * 0.6f);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, pad, pad, pad);
        android.graphics.drawable.GradientDrawable popupBg = new android.graphics.drawable.GradientDrawable();
        popupBg.setColor(KruxTheme.get(this, "bg"));
        popupBg.setCornerRadius(16 * d);
        popupBg.setStroke((int) d, KruxTheme.get(this, "bubble_user"));
        container.setBackground(popupBg);

        TextView title = new TextView(this);
        title.setText("Modèle Krux");
        title.setTextColor(KruxTheme.get(this, "text"));
        title.setTextSize(15);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding((int) (12 * d), (int) (6 * d), (int) (12 * d), (int) (8 * d));
        container.addView(title);

        // la liste défile si elle devient longue (hauteur plafonnée à 60 % de l'écran)
        android.widget.ScrollView scroll = new android.widget.ScrollView(this) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(maxH, View.MeasureSpec.AT_MOST));
            }
        };
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        container.addView(scroll, new LinearLayout.LayoutParams(-1, -2));

        final KruxModel[] featured = { KruxModel.MISTRAL_MEDIUM, KruxModel.GEMINI_36_FLASH, KruxModel.HF_QWEN_CODER_32B };
        final java.util.List<KruxModel> featuredList = java.util.Arrays.asList(featured);
        final LinearLayout extra = new LinearLayout(this);
        extra.setOrientation(LinearLayout.VERTICAL);
        boolean selectedInExtra = false;

        for (KruxModel m : featured) list.addView(kruxModelRow(m, dialog));
        for (KruxModel m : KruxModel.values()) {
            if (featuredList.contains(m)) continue;
            extra.addView(kruxModelRow(m, dialog));
            if (m == selectedKruxModel) selectedInExtra = true;
        }
        list.addView(extra);
        extra.setVisibility(selectedInExtra ? View.VISIBLE : View.GONE);

        final TextView more = new TextView(this);
        more.setText(selectedInExtra ? "Afficher moins" : "Afficher plus de modèles…");
        more.setTextColor(KruxTheme.get(this, "text2"));
        more.setTextSize(13);
        more.setPadding((int) (12 * d), (int) (12 * d), (int) (12 * d), (int) (8 * d));
        more.setClickable(true);
        list.addView(more);

        more.setOnClickListener(v -> {
            final boolean opening = extra.getVisibility() != View.VISIBLE;
            extra.measure(
                    View.MeasureSpec.makeMeasureSpec(width - 2 * pad, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            final int full = extra.getMeasuredHeight();
            final android.view.ViewGroup.LayoutParams lp = extra.getLayoutParams();
            if (opening) {
                lp.height = 0;
                extra.setLayoutParams(lp);
                extra.setVisibility(View.VISIBLE);
                more.setText("Afficher moins");
            } else {
                more.setText("Afficher plus de modèles…");
            }
            android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofInt(opening ? 0 : full, opening ? full : 0);
            anim.setDuration(240);
            anim.setInterpolator(new android.view.animation.DecelerateInterpolator());
            anim.addUpdateListener(an -> {
                lp.height = (Integer) an.getAnimatedValue();
                extra.setLayoutParams(lp);
            });
            anim.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator an) {
                    if (opening) {
                        lp.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
                        extra.setLayoutParams(lp);
                    } else {
                        extra.setVisibility(View.GONE);
                    }
                }
            });
            anim.start();
        });

        dialog.setContentView(container);

        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setDimAmount(0.25f);

            android.view.WindowManager.LayoutParams params = new android.view.WindowManager.LayoutParams();
            params.copyFrom(window.getAttributes());
            // positions comptées depuis le haut de l'écran (pour placer le menu sous la capsule)
            params.flags |= android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            params.width = width;
            params.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;
            params.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
            if (kruxModelCapsule != null) {
                int[] loc = new int[2];
                kruxModelCapsule.getLocationOnScreen(loc);
                params.x = Math.max((int) (12 * d), Math.min(loc[0], screenW - width - (int) (12 * d)));
                params.y = loc[1] + kruxModelCapsule.getHeight() + (int) (6 * d);
            } else {
                params.x = (screenW - width) / 2;
                params.y = (int) (90 * d);
            }
            window.setAttributes(params);
        }

        dialog.show();
        KruxTheme.skin(dialog, this);

        // le menu se déroule vers le bas, comme un parchemin
        container.setPivotY(0f);
        container.setScaleY(0.8f);
        container.setAlpha(0f);
        container.animate().scaleY(1f).alpha(1f).setDuration(200)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }

    /** Une ligne du menu : nom du modèle, petit/grand et taille de contexte, coche si c'est le modèle choisi. */
    private View kruxModelRow(final KruxModel model, final android.app.Dialog dialog) {
        final float d = getResources().getDisplayMetrics().density;
        final boolean selected = model == selectedKruxModel;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding((int) (12 * d), (int) (9 * d), (int) (12 * d), (int) (9 * d));
        row.setClickable(true);
        row.setFocusable(true);
        if (selected) {
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(KruxTheme.get(this, "bubble_user"));
            bg.setCornerRadius(12 * d);
            row.setBackground(bg);
        }

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView name = new TextView(this);
        name.setText(model.getDisplayName());
        name.setTextColor(KruxTheme.get(this, "text"));
        name.setTextSize(15);
        if (selected) name.setTypeface(null, android.graphics.Typeface.BOLD);
        texts.addView(name);

        String tokens = model.getContextTokens() >= 1000000
                ? (model.getContextTokens() / 1000000) + "M tokens"
                : (model.getContextTokens() / 1000) + "K tokens";
        TextView info = new TextView(this);
        info.setText((model.isPetit() ? "Petit" : "Grand") + " · " + tokens);
        info.setTextColor(KruxTheme.get(this, "text2"));
        info.setTextSize(11);
        texts.addView(info);

        if (selected) {
            TextView check = new TextView(this);
            check.setText("✔");
            check.setTextColor(KruxTheme.get(this, "text"));
            check.setTextSize(15);
            row.addView(check);
        }

        row.setOnClickListener(v -> {
            selectedKruxModel = model;
            updateKruxModelUI();
            dialog.dismiss();
        });
        return row;
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
            if (status == TextToSpeech.SUCCESS) {
                com.virtixstudio.kruxai.utils.VoiceStyler.prepare(textToSpeech, Locale.FRENCH);
                textToSpeech.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                    @Override public void onStart(String id) {
                        int idx = com.virtixstudio.kruxai.utils.VoiceStyler.chunkOf(id, ttsTotal);
                        if (idx >= 0) ttsChunk = idx;
                        com.virtixstudio.kruxai.live.FloatingKruxService.setState(com.virtixstudio.kruxai.live.LiveMotion.State.SPEAKING);
                    }
                    @Override public void onDone(String id) {
                        if (com.virtixstudio.kruxai.utils.VoiceStyler.isPart(id)) return;
                        if ("KruxAI_TTS".equals(id) && !ttsPaused) {
                            runOnUiThread(() -> {
                                isTtsSpeaking = false;
                                if (btnTtsControl != null) btnTtsControl.setVisibility(View.GONE);
                            });
                        }
                        com.virtixstudio.kruxai.live.FloatingKruxService.setState(com.virtixstudio.kruxai.live.LiveMotion.State.IDLE);
                    }
                    @Override public void onError(String id) {
                        if (com.virtixstudio.kruxai.utils.VoiceStyler.isPart(id)) return;
                        com.virtixstudio.kruxai.live.FloatingKruxService.setState(com.virtixstudio.kruxai.live.LiveMotion.State.IDLE);
                    }
                    @Override public void onStop(String id, boolean interrupted) {
                        if (com.virtixstudio.kruxai.utils.VoiceStyler.isPart(id)) return;
                        com.virtixstudio.kruxai.live.FloatingKruxService.setState(com.virtixstudio.kruxai.live.LiveMotion.State.IDLE);
                    }
                    @Override public void onRangeStart(String id, int start, int end, int frame) {
                        com.virtixstudio.kruxai.live.FloatingKruxService.word();
                    }
                });
            }
        });
    }

    @Override
    public void onSpeakRequested(String text) {
        if (textToSpeech != null) {
            if (textToSpeech.isSpeaking() || ttsPaused) {
                textToSpeech.stop();
                isTtsSpeaking = false;
                ttsPaused = false;
                if (btnTtsControl != null) btnTtsControl.setVisibility(View.GONE);
            } else {
                ttsText = text;
                ttsTotal = com.virtixstudio.kruxai.utils.VoiceStyler.count(text);
                ttsChunk = 0;
                ttsPaused = false;
                com.virtixstudio.kruxai.utils.VoiceStyler.speak(textToSpeech, text, "KruxAI_TTS");
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
        if (ttsPaused) {
            // Reprise : on repart au début de la phrase interrompue.
            ttsPaused = false;
            isTtsSpeaking = true;
            com.virtixstudio.kruxai.utils.VoiceStyler.speakFrom(textToSpeech, ttsText, "KruxAI_TTS", ttsChunk);
            if (btnTtsControl != null) btnTtsControl.setImageResource(R.drawable.ic_pause);
        } else if (isTtsSpeaking) {
            // Pause : Android n'a pas de vraie pause, on arrête et on garde la phrase en cours.
            ttsPaused = true;
            textToSpeech.stop();
            if (btnTtsControl != null) btnTtsControl.setImageResource(R.drawable.ic_play);
        } else {
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
        // Les conversations passent maintenant uniquement par la synchro Cloudflare (CloudSync) : écrire aussi dans
        // Firestore créait des doublons. Seule la lecture unique des anciennes conversations (syncCloudData) reste.
        if (true) return;
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

    private long lastMemoryExtract = 0;

    /**
     * Après chaque échange : un petit modèle relit le message et dit ce qu'il faut retenir, mettre à jour ou retirer.
     * Tourne en arrière-plan, au plus une fois toutes les 20 s, sans jamais gêner la discussion.
     */
    private void extractMemoryInBackground(final String userMsg, final String aiReply) {
        try {
            if (!com.virtixstudio.kruxai.utils.MemoryBook.worthExtracting(userMsg)) return;
            if (com.virtixstudio.kruxai.utils.MemoryExtractor.forgetTarget(userMsg) != null) return; // « oublie… » est déjà géré
            long now = System.currentTimeMillis();
            if (now - lastMemoryExtract < 20000L) return;
            lastMemoryExtract = now;
            final String sys = com.virtixstudio.kruxai.utils.MemoryBook.extractionSystemPrompt();
            final String usr = com.virtixstudio.kruxai.utils.MemoryBook.extractionUserPrompt(memoryRepo.all(), userMsg, aiReply);
            ApiClient.sendQuiet(sys, usr, out -> {
                try {
                    if (out == null) return;
                    java.util.List<com.virtixstudio.kruxai.utils.MemoryBook.Op> ops =
                            com.virtixstudio.kruxai.utils.MemoryBook.parseOps(out, System.currentTimeMillis() / 1000L);
                    if (ops.isEmpty()) return;
                    final java.util.List<com.virtixstudio.kruxai.utils.MemoryBook.Entry> before = memoryRepo.all();
                    final java.util.List<String> log = memoryRepo.apply(ops);
                    if (log.isEmpty()) return;
                    showMemoryPill(log, before);
                    runOnUiThread(() -> syncCloud(false));
                } catch (Exception e) {
                    android.util.Log.e("KRUX_MEMORY", "extraction", e);
                }
            });
        } catch (Exception e) {
            android.util.Log.e("KRUX_MEMORY", "extraction", e);
        }
    }

    /**
     * Mémoire automatique : retient ce que l'utilisateur dit de lui et oublie ce qu'il demande d'oublier,
     * sans dépendre du modèle. Les sujets sensibles ne sont jamais retenus.
     */
    private void handleMemoryCommands(String prompt) {
        try {
            final java.util.List<com.virtixstudio.kruxai.utils.MemoryBook.Entry> before = memoryRepo.all();
            final java.util.List<String> log = new java.util.ArrayList<>();
            String target = com.virtixstudio.kruxai.utils.MemoryExtractor.forgetTarget(prompt);
            if (target != null) {
                java.util.List<String> gone = memoryRepo.forget(target);
                for (String f : gone) {
                    log.add("oublié : " + f);
                }
                if (!gone.isEmpty()) com.virtixstudio.kruxai.core.CloudSync.markMemoryReplace(this, currentUser);
            } else {
                for (String fact : com.virtixstudio.kruxai.utils.MemoryExtractor.extract(prompt)) {
                    if (memoryRepo.has(fact)) continue;
                    if (memoryRepo.remember(fact)) {
                        log.add("mémorisé : " + fact);
                    }
                }
            }
            showMemoryPill(log, before);
        } catch (Exception e) {
            android.util.Log.e("KRUX_MEMORY", "mémoire automatique", e);
        }
    }

    // ------------------------------------------------------------------ header et barre de saisie flottants

    private int lastChatBottomInset = -1;

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    /** Le header et la barre de saisie flottent sans plaque : le chat défile dessous, avec la bonne marge en haut et en bas. */
    private void setupFloatingChrome() {
        try {
            final View composer = findViewById(R.id.composerBar);
            final View header = findViewById(R.id.headerBar);
            if (composer == null || rvChat == null) return;
            View.OnLayoutChangeListener l = (v, a, b, c, d, e, f, g, h) -> updateChatInsets();
            composer.addOnLayoutChangeListener(l);
            if (terminal != null) terminal.addOnLayoutChangeListener(l);
            if (header != null) header.addOnLayoutChangeListener(l);
            rvChat.post(this::updateChatInsets);
        } catch (Exception e) {
            android.util.Log.e("KRUX_UI", "marges du chat", e);
        }
    }

    private void updateChatInsets() {
        try {
            final View composer = findViewById(R.id.composerBar);
            final View header = findViewById(R.id.headerBar);
            if (composer == null || rvChat == null || !(rvChat.getParent() instanceof View)) return;
            int parentH = ((View) rvChat.getParent()).getHeight();
            if (parentH <= 0) return;
            View lowest = (terminal != null && terminal.getVisibility() == View.VISIBLE) ? terminal : composer;
            int bottom = Math.max(0, parentH - lowest.getTop()) + dp(8);
            int top = (header != null ? header.getHeight() : dp(64)) + dp(8);
            if (bottom == lastChatBottomInset && rvChat.getPaddingTop() == top) return;
            boolean atEnd = !rvChat.canScrollVertically(1);
            lastChatBottomInset = bottom;
            rvChat.setPadding(rvChat.getPaddingLeft(), top, rvChat.getPaddingRight(), bottom);
            if (btnScrollBottom != null && btnScrollBottom.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
                android.view.ViewGroup.MarginLayoutParams mp = (android.view.ViewGroup.MarginLayoutParams) btnScrollBottom.getLayoutParams();
                mp.bottomMargin = bottom + dp(4);
                btnScrollBottom.setLayoutParams(mp);
            }
            if (atEnd && messageList != null && !messageList.isEmpty()) rvChat.scrollToPosition(messageList.size() - 1);
        } catch (Exception e) {
            android.util.Log.e("KRUX_UI", "marges du chat", e);
        }
    }

    // ------------------------------------------------------------------ hors connexion

    private com.virtixstudio.kruxai.core.NetworkWatcher net;
    private TextView offlineBanner;
    private final android.os.Handler netHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private int pendingAttempt = 0;

    private static final class NetPending {
        final String prompt, fileName, fileContent;
        ChatMessage placeholder;
        NetPending(String p, String n, String c, ChatMessage m) { prompt = p; fileName = n; fileContent = c; placeholder = m; }
    }
    private final java.util.ArrayDeque<NetPending> netQueue = new java.util.ArrayDeque<>();

    private void initNetwork() {
        try {
            offlineBanner = findViewById(R.id.offlineBanner);
            if (offlineBanner != null) {
                offlineBanner.setOnClickListener(v -> {
                    if (netQueue.isEmpty()) return;
                    android.app.AlertDialog d = new android.app.AlertDialog.Builder(this)
                            .setTitle("Messages en attente")
                            .setMessage("Annuler l'envoi des " + netQueue.size() + " message(s) en attente ?")
                            .setPositiveButton("Annuler l'envoi", (x, w) -> clearNetQueue())
                            .setNegativeButton("Garder", null)
                            .create();
                    d.show();
                    KruxTheme.skin(d, this);
                });
            }
            net = new com.virtixstudio.kruxai.core.NetworkWatcher(this, online -> onNetChanged(online));
            net.start();
            if (!net.isOnline()) showBanner(false);
        } catch (Exception e) {
            android.util.Log.e("KRUX_NET", "réseau", e);
        }
    }

    private void showBanner(boolean back) {
        if (offlineBanner == null) return;
        offlineBanner.setBackgroundColor(KruxTheme.get(this, "input"));
        offlineBanner.setTextColor(KruxTheme.get(this, back ? "accent" : "text"));
        offlineBanner.setText(back ? com.virtixstudio.kruxai.utils.NetPolicy.BANNER_BACK : com.virtixstudio.kruxai.utils.NetPolicy.BANNER_OFFLINE);
        offlineBanner.setVisibility(android.view.View.VISIBLE);
        netHandler.removeCallbacks(hideBanner);
        if (back) netHandler.postDelayed(hideBanner, 2200);
    }

    private final Runnable hideBanner = () -> { if (offlineBanner != null) offlineBanner.setVisibility(android.view.View.GONE); };

    private void onNetChanged(boolean online) {
        if (online) {
            showBanner(true);
            // le réseau est revenu : les attentes en cours repartent tout de suite
            netHandler.postDelayed(this::drainNet, 600);
            syncCloud(false);
        } else {
            showBanner(false);
        }
    }

    private void persistNetQueue() {
        try {
            if (currentUser == null) return;
            java.util.List<String> l = new java.util.ArrayList<>();
            for (NetPending p : netQueue) l.add(p.prompt);
            getSharedPreferences("krux_pending_" + currentUser.getUid(), MODE_PRIVATE).edit()
                    .putString("q", com.virtixstudio.kruxai.utils.NetPolicy.encodeQueue(l)).apply();
        } catch (Exception ignore) { }
    }

    /** Remet en attente les messages d'une session précédente qui n'ont jamais pu partir. */
    private void restoreNetQueue() {
        try {
            if (currentUser == null || !netQueue.isEmpty()) return;
            String saved = getSharedPreferences("krux_pending_" + currentUser.getUid(), MODE_PRIVATE).getString("q", "");
            for (String p : com.virtixstudio.kruxai.utils.NetPolicy.decodeQueue(saved)) queueForNet(p, "", "", null);
        } catch (Exception ignore) { }
    }

    /** Garde la demande et affiche, à la place de la réponse, qu'elle partira toute seule. */
    private void queueForNet(String prompt, String fileName, String fileContent, ChatMessage existing) {
        ChatMessage ph = existing;
        if (ph == null) {
            ph = new ChatMessage("", false);
            messageList.add(ph);
            if (chatAdapter != null) chatAdapter.notifyItemInserted(messageList.size() - 1);
        }
        ph.setStreaming(false);
        ph.setWaiting(true);
        ph.setText(com.virtixstudio.kruxai.utils.NetPolicy.waitingText());
        int pos = messageList.indexOf(ph);
        if (pos >= 0 && chatAdapter != null) { chatAdapter.notifyItemChanged(pos); rvChat.smoothScrollToPosition(pos); }
        NetPending np = new NetPending(prompt, fileName, fileContent, ph);
        waitingMap.put(ph, np);
        netQueue.add(np);
        persistNetQueue();
        if (net != null && !net.isOnline()) showBanner(false);
    }

    private final java.util.IdentityHashMap<ChatMessage, NetPending> waitingMap = new java.util.IdentityHashMap<>();

    /** Bouton « Réessayer » : renvoie tout de suite ce message en attente, s'il y a du réseau. */
    private void retryWaiting(ChatMessage m) {
        NetPending p = waitingMap.get(m);
        if (p == null) return;
        if (net != null && !net.isOnline()) {
            Toast.makeText(this, "Toujours pas de connexion. Réessaie dans un instant.", Toast.LENGTH_SHORT).show();
            return;
        }
        netQueue.remove(p);
        persistNetQueue();
        removePlaceholder(m);
        executeAiQuery(p.prompt, "", new ArrayList<>(), p.fileName, p.fileContent);
    }

    /** Bouton « Continuer » : Krux reprend sa réponse arrêtée à l'endroit où elle s'est coupée. */
    private void continueStopped(ChatMessage m) {
        if (m == null || !m.isStopped() || isGenerating) return;
        String t = m.getText() == null ? "" : m.getText();
        String tail = t.length() > 1500 ? t.substring(t.length() - 1500) : t;
        executeAiQuery(
                "Continue ta réponse exactement là où elle s'est arrêtée, sans la recommencer ni répéter ce qui est déjà écrit. "
                        + "Elle se terminait par :\n\"\"\"\n" + tail + "\n\"\"\"",
                "", new ArrayList<>(), "", "");
    }

    private void removePlaceholder(ChatMessage ph) {
        waitingMap.remove(ph);
        int pos = messageList.indexOf(ph);
        if (pos >= 0) { messageList.remove(pos); if (chatAdapter != null) chatAdapter.notifyItemRemoved(pos); }
    }

    private void clearNetQueue() {
        for (NetPending p : netQueue) removePlaceholder(p.placeholder);
        netQueue.clear();
        persistNetQueue();
        if (net != null && net.isOnline() && offlineBanner != null) offlineBanner.setVisibility(android.view.View.GONE);
    }

    /** Envoie la prochaine demande en attente, si le réseau est là et que KRUX n'est pas déjà en train de répondre. */
    private void drainNet() {
        if (netQueue.isEmpty() || net == null || !net.isOnline()) return;
        if (isGenerating) { netHandler.postDelayed(this::drainNet, 1500); return; }
        NetPending p = netQueue.poll();
        persistNetQueue();
        removePlaceholder(p.placeholder);
        executeAiQuery(p.prompt, "", new ArrayList<>(), p.fileName, p.fileContent);
    }

    /** Connexion instable : compte à rebours visible, puis nouvel essai tout seul (3, 8, 20, 45 s). */
    private void autoRetry(final String prompt, final String fileName, final String fileContent,
                           final ChatMessage ph, final int attempt) {
        final int[] left = { com.virtixstudio.kruxai.utils.NetPolicy.backoffSeconds(attempt) };
        final Runnable[] tick = new Runnable[1];
        ph.setWaiting(true);
        waitingMap.put(ph, new NetPending(prompt, fileName, fileContent, ph));
        tick[0] = () -> {
            if (!messageList.contains(ph)) return; // la discussion a changé : on arrête
            if (net != null && !net.isOnline()) { queueForNet(prompt, fileName, fileContent, ph); return; }
            if (left[0] <= 0) {
                removePlaceholder(ph);
                pendingAttempt = attempt + 1;
                executeAiQuery(prompt, "", new ArrayList<>(), fileName, fileContent);
                return;
            }
            ph.setText(com.virtixstudio.kruxai.utils.NetPolicy.retryText(left[0], attempt));
            int pos = messageList.indexOf(ph);
            if (pos >= 0 && chatAdapter != null) chatAdapter.notifyItemChanged(pos);
            left[0]--;
            netHandler.postDelayed(tick[0], 1000);
        };
        tick[0].run();
    }

    /** Pastille sous la discussion : ce que KRUX vient de retenir ou d'oublier, avec Annuler ; toucher ouvre l'écran Mémoire. */
    private void showMemoryPill(final java.util.List<String> log,
                                final java.util.List<com.virtixstudio.kruxai.utils.MemoryBook.Entry> before) {
        if (log == null || log.isEmpty()) return;
        runOnUiThread(() -> {
            try {
                String msg;
                if (log.size() == 1) {
                    String l = log.get(0);
                    msg = "Mémoire · " + (l.startsWith("oublié") ? "Oublié" : "Retenu") + l.substring(l.indexOf(':'));
                } else {
                    msg = "Mémoire mise à jour · " + log.size() + " changements";
                }
                com.google.android.material.snackbar.Snackbar sb =
                        com.google.android.material.snackbar.Snackbar.make(findViewById(android.R.id.content), msg, 7000);
                android.view.View anchor = findViewById(R.id.composerBar);
                if (anchor != null) sb.setAnchorView(anchor);
                sb.setBackgroundTint(KruxTheme.get(this, "input"));
                sb.setTextColor(KruxTheme.get(this, "text"));
                sb.setActionTextColor(KruxTheme.get(this, "accent"));
                sb.setAction("Annuler", v -> {
                    memoryRepo.restore(before);
                    com.virtixstudio.kruxai.core.CloudSync.markMemoryReplace(this, currentUser);
                    syncCloud(false);
                });
                sb.getView().setOnClickListener(v -> startActivity(new android.content.Intent(this, MemoryActivity.class)));
                sb.show();
            } catch (Exception e) {
                android.util.Log.e("KRUX_MEMORY", "pastille", e);
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

        // Reprise des anciennes conversations Firestore : une seule fois par compte, ensuite Cloudflare seul.
        final android.content.SharedPreferences legacy = getSharedPreferences("krux_legacy_" + accountId, MODE_PRIVATE);
        if (legacy.getBoolean("chats_done", false)) {
            loadCurrentSession();
            loadHistorySidebar();
            return;
        }

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
                    legacy.edit().putBoolean("chats_done", true).apply();
                    syncCloud(true); // envoie ces anciennes conversations vers Cloudflare

                    android.util.Log.d(
                            "KRUX_SYNC",
                            "Synchronisation terminée pour " + accountId
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

    /** Petit menu du haut à droite : exporter, mémoire, personnaliser, réglages. Suit le thème. */
    private void showMoreMenu(View anchor) {
        final float d = getResources().getDisplayMetrics().density;
        final int tx = KruxTheme.get(this, "text");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding((int) (6 * d), (int) (6 * d), (int) (6 * d), (int) (6 * d));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(KruxTheme.get(this, "input"));
        bg.setCornerRadius(16 * d);
        bg.setStroke((int) d, androidx.core.graphics.ColorUtils.setAlphaComponent(tx, 40));
        box.setBackground(bg);
        final android.widget.PopupWindow pw = new android.widget.PopupWindow(box, (int) (230 * d),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pw.setElevation(10 * d);
        pw.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        Object[][] rows = {
            {"Exporter la discussion", R.drawable.ic_download, (Runnable) this::exportConversation},
            {"Mémoire", R.drawable.ic_memory, (Runnable) () -> startActivity(new Intent(this, MemoryActivity.class))},
            {"Personnaliser", R.drawable.ic_palette, (Runnable) this::showCustomizationSheet},
            {"Réglages", R.drawable.ic_opt_settings, (Runnable) () -> settingsLauncher.launch(new Intent(this, SettingsActivity.class))},
        };
        for (Object[] r : rows) {
            TextView t = new TextView(this);
            t.setText((String) r[0]);
            t.setTextSize(15);
            t.setTextColor(tx);
            t.setGravity(android.view.Gravity.CENTER_VERTICAL);
            t.setCompoundDrawablesWithIntrinsicBounds((int) r[1], 0, 0, 0);
            t.setCompoundDrawablePadding((int) (14 * d));
            androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(t, android.content.res.ColorStateList.valueOf(tx));
            t.setPadding((int) (14 * d), 0, (int) (14 * d), 0);
            t.setBackgroundResource(android.R.drawable.list_selector_background);
            final Runnable run = (Runnable) r[2];
            t.setOnClickListener(v -> { pw.dismiss(); run.run(); });
            box.addView(t, new LinearLayout.LayoutParams(-1, (int) (48 * d)));
        }
        pw.showAsDropDown(anchor, 0, (int) (4 * d), android.view.Gravity.END);
    }

    private void exportConversation() {
        com.virtixstudio.kruxai.utils.ChatExporter.showSheet(this, messageList);
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
        if (welcomeGlobe != null) {
            if (empty) {
                if (welcomeGlobe.getVisibility() != View.VISIBLE || welcomeGlobe.getAlpha() < 1f) {
                    welcomeGlobe.showAnimated();
                }
            } else {
                welcomeGlobe.hideAnimated();
            }
        }

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

    /** Galerie d'images libres dans le chat, avec un message « Je cherche… » visible tout de suite. */
    private void runImageGallery(final String query, final ChatMessage existing, final Runnable onFail) {
        runOnUiThread(() -> {
            ChatMessage h = existing;
            if (h == null) {
                h = new ChatMessage("", false);
                messageList.add(h);
                chatAdapter.notifyItemInserted(messageList.size() - 1);
            }
            h.setText("Je cherche des images de « " + query + " »…");
            h.setStreaming(true);
            int pos = messageList.indexOf(h);
            if (pos >= 0) { chatAdapter.notifyItemChanged(pos); rvChat.smoothScrollToPosition(pos); }
            final ChatMessage holder = h;
            com.virtixstudio.kruxai.api.ImageSearch.search(query, new com.virtixstudio.kruxai.api.ImageSearch.Callback() {
                private void removeHolder() {
                    int p = messageList.indexOf(holder);
                    if (p >= 0) { messageList.remove(p); chatAdapter.notifyItemRemoved(p); }
                }
                @Override public void onResult(java.util.List<com.virtixstudio.kruxai.utils.ImageGallery.Hit> hits) {
                    runOnUiThread(() -> {
                        removeHolder();
                        String text = "Voici des images de « " + query + " ». Touche une image pour l'agrandir, la télécharger ou voir sa source.\n\n"
                                + com.virtixstudio.kruxai.utils.ImageGallery.encode(hits);
                        ChatMessage reply = new ChatMessage(text, false);
                        reply.setStreaming(false);
                        saveMessageToDatabase(reply);
                        setGeneratingState(false);
                        setKruxState(KruxState.IDLE);
                    });
                }
                @Override public void onError(String message) {
                    runOnUiThread(() -> {
                        removeHolder();
                        setGeneratingState(false);
                        setKruxState(KruxState.IDLE);
                        if (onFail != null) onFail.run();
                        else Toast.makeText(MainActivity.this, "Aucune image trouvée pour « " + query + " ».", Toast.LENGTH_LONG).show();
                    });
                }
            });
        });
    }

    private void sendMessage() {
        String prompt = etInput.getText().toString().trim();
        if (prompt.isEmpty()) return;

        final String fileNameForRequest = selectedFileName;
        final String fileContentForRequest = selectedFileContent;

        final boolean withPhoto = !selectedImagesB64.isEmpty();
        final int photoCount = selectedImagesB64.size();
        if (withPhoto) {
            pendingRequestImages = new java.util.ArrayList<>(selectedImagesB64);
            selectedImagesB64.clear();
            selectedThumbs.clear();
            refreshAttachmentStrip();
            refreshSendIcon();
        }
        ChatMessage userMessage = new ChatMessage((withPhoto ? (photoCount > 1 ? "📷 " + photoCount + " photos jointes\n" : "📷 Photo jointe\n") : "") + prompt, true);
        saveMessageToDatabase(userMessage);
        handleMemoryCommands(prompt);
        updateWelcomePanel();
        if (welcomeScene != null) {
            welcomeScene.setAnimating(false);
        }
        etInput.setText("");
        // L'état ("Réflexion…", "Génération…") appartient à la NOUVELLE réponse : on détache l'ancienne
        // avant le premier changement d'état, sinon il s'afficherait au-dessus de la réponse précédente.
        if (activeAiMessage != null && chatAdapter != null) {
            chatAdapter.clearKruxAnswerStatus(activeAiMessage.getId());
        }
        activeAiMessage = null;
        setKruxState(KruxState.THINKING);

        // « Montre-moi des photos de … » : galerie d'images libres, sans passer par le modèle
        final String imageQuery = com.virtixstudio.kruxai.utils.ImageGallery.extractQuery(prompt);
        if (imageQuery != null && (net == null || net.isOnline())) {
            setKruxState(KruxState.SEARCHING);
            runImageGallery(imageQuery, null, () -> executeAiQuery(prompt, "", new ArrayList<>(), fileNameForRequest, fileContentForRequest));
            return;
        }

        ToolRouter.Tool tool = ToolRouter.decide(prompt, isDeepSearchEnabled);

        if (tool == ToolRouter.Tool.WEB_SEARCH && (net == null || net.isOnline())) {
            setKruxState(KruxState.SEARCHING);
            if (terminal != null) {
                terminal.clear();
                terminal.println("krux web \"" + (prompt.length() > 60 ? prompt.substring(0, 57) + "…" : prompt.replace('\n', ' ')) + "\"", KruxTerminalView.CMD);
            }
            webSearchEngine.search(prompt, new WebSearchEngine.SearchCallback() {
                @Override
                public void onProgress(String line) {
                    if (terminal == null) return;
                    if (line.startsWith("search ") || line.startsWith("read ")) terminal.println("krux " + line, KruxTerminalView.CMD);
                    else terminal.println(line.trim(), KruxTerminalView.INFO);
                }

                @Override
                public void onSuccess(List<SearchResult> results, String formattedContext) {
                    if (terminal != null) {
                        terminal.println(results.size() + " sources prêtes, je rédige la réponse", KruxTerminalView.OK);
                        terminal.hideLater(2500);
                    }
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
                    if (terminal != null) {
                        terminal.println("recherche impossible, je le dirai dans la réponse", KruxTerminalView.ERR);
                        terminal.hideLater(2500);
                    }
                    executeAiQuery(
                            prompt,
                            "=== RECHERCHE WEB IMPOSSIBLE ===\nLa recherche n'a rien rapporté. Réponds seulement avec ce dont tu es certain, "
                                    + "dis clairement ce que tu n'as pas pu vérifier, et n'invente aucun fait ni aucune source.\n",
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
        // La mémoire est courte : KRUX reçoit tout ce qui tient (2 200 caractères), le plus lié à la question en premier.
        return com.virtixstudio.kruxai.utils.MemorySelector.select(userPrompt, memoryFacts, 2200, 40);
    }

    /**
     * L'espace de travail de KRUX : les fichiers qu'il écrit sont rangés sur le téléphone, puis envoyés au cloud
     * en une seule archive compressée au maximum (dictionnaire + deflate niveau 9), pour ne pas charger la base.
     */
    private void deliverToWorkspace(String text) {
        final java.util.List<com.virtixstudio.kruxai.utils.FileDelivery.DFile> files = com.virtixstudio.kruxai.utils.FileDelivery.parse(text);
        if (files.isEmpty() || !announcedFiles.add("s:" + Integer.toHexString(String.valueOf(text).hashCode()))) return;
        final long stamp = System.currentTimeMillis();
        final com.google.firebase.auth.FirebaseUser user = currentUser;
        new Thread(() -> {
            try {
                // Tout fichier généré va dans l'onglet Fichiers (dossier à plat) ; le cloud garde les chemins d'origine.
                com.virtixstudio.kruxai.core.WorkspaceStore store = new com.virtixstudio.kruxai.core.WorkspaceStore(new java.io.File(getFilesDir(), "workspace"));
                java.util.List<com.virtixstudio.kruxai.core.WorkspaceBundle.Entry> entries = new java.util.ArrayList<>();
                int raw = 0;
                for (com.virtixstudio.kruxai.utils.FileDelivery.DFile f : files) {
                    try { store.saveGenerated(f.name, f.content); }
                    catch (java.io.IOException e) { android.util.Log.w("KRUX_WS", "Fichiers : " + e.getMessage()); }
                    entries.add(new com.virtixstudio.kruxai.core.WorkspaceBundle.Entry(f.name, f.content));
                    raw += f.bytes();
                }
                if (!com.virtixstudio.kruxai.core.KruxConfig.workerEnabled() || user == null) return;
                byte[] packed = com.virtixstudio.kruxai.core.WorkspaceBundle.pack(entries);
                final String line = "cloud ws/" + stamp + ".krx  " + kb(raw) + " → " + kb(packed.length)
                        + " (-" + (raw == 0 ? 0 : 100 - 100 * packed.length / raw) + " %)";
                com.virtixstudio.kruxai.core.WorkerStore.put(user, "ws/" + stamp + ".krx", packed);
                runOnUiThread(() -> { if (terminal != null) { terminal.println(line, KruxTerminalView.OK); terminal.hideLater(5000); } });
            } catch (Exception e) {
                android.util.Log.w("KRUX_WS", "espace de travail : " + e.getMessage());
            }
        }, "krux-workspace").start();
    }

    private static String kb(int b) {
        return b < 1024 ? b + " o" : String.format(java.util.Locale.FRANCE, "%.1f Ko", b / 1024.0);
    }

    private final java.util.Set<String> announcedFiles = new java.util.HashSet<>();

    /** Montre dans le terminal les fichiers que KRUX est en train d'écrire, puis ceux qui sont terminés. */
    private void announceFiles(String text) {
        if (terminal == null || text == null || text.indexOf("<KRUX_FIL") < 0) return;
        String now = com.virtixstudio.kruxai.utils.FileDelivery.writingNow(text);
        if (now != null && announcedFiles.add("w:" + now)) {
            terminal.println("krux write " + now, KruxTerminalView.CMD);
        }
        for (com.virtixstudio.kruxai.utils.FileDelivery.DFile f : com.virtixstudio.kruxai.utils.FileDelivery.parse(text)) {
            if (announcedFiles.add("d:" + f.name)) {
                int b = f.bytes();
                terminal.println(f.name + "  (" + (b < 1024 ? b + " o" : String.format(java.util.Locale.FRANCE, "%.1f Ko", b / 1024.0)) + ")", KruxTerminalView.OK);
                terminal.hideLater(6000);
            }
        }
    }

    private void executeAiQuery(
            String userPrompt,
            String webContext,
            List<SearchResult> sources,
            String fileName,
            String fileContent
    ) {
        final int netAttempt = pendingAttempt;
        pendingAttempt = 0;
        if (net != null && !net.isOnline()) {
            // Pas d'Internet : on garde le message et on l'enverra tout seul au retour du réseau.
            queueForNet(userPrompt, fileName, fileContent, null);
            setGeneratingState(false);
            setKruxState(KruxState.IDLE);
            return;
        }
        announcedFiles.clear();
        // Une nouvelle demande part : les anciens boutons « Continuer » disparaissent.
        for (int i = 0; i < messageList.size(); i++) {
            ChatMessage old = messageList.get(i);
            if (old.isStopped()) { old.setStopped(false); if (chatAdapter != null) chatAdapter.notifyItemChanged(i); }
        }
        // Autre point d'entrée possible (régénération...) : l'ancienne réponse ne doit pas recevoir l'état.
        if (activeAiMessage != null && !activeAiMessage.isStreaming() && chatAdapter != null) {
            chatAdapter.clearKruxAnswerStatus(activeAiMessage.getId());
            activeAiMessage = null;
        }
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

        String memoryBlock = com.virtixstudio.kruxai.utils.MemoryBook.formatForPrompt(
                memoryRepo.all(), userPrompt, 2200, 40);
        if (!memoryBlock.isEmpty()) {
            historyBuilder.append("\nSOUVENIRS SUR L'UTILISATEUR (vrais, rangés par thème avec leur date ; le plus récent l'emporte ; à utiliser naturellement quand c'est utile) :\n");
            historyBuilder.append(memoryBlock);
        }

        String systemPrompt = new SystemPromptBuilder().withThinking(isThinkingMode).withLearning(isLearningMode)
                .withKruxModel(
                    selectedKruxModel != null
                        ? selectedKruxModel.getDisplayName()
                        : "KRUX Prime"
                )
                .withUserProfile(currentUserFirstName, currentUserEmail)
                .withBuilderQuota(quotaUsed(), weeklyQuota())
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
        hideAttachmentStrip();

        ChatMessage aiMessage = new ChatMessage("", false, sources);
        aiMessage.setStreaming(true);
        activeAiMessage = aiMessage;

        runOnUiThread(() -> {
            messageList.add(aiMessage);
            chatAdapter.notifyItemInserted(messageList.size() - 1);
            rvChat.smoothScrollToPosition(messageList.size() - 1);
        });
        // La nouvelle réponse existe maintenant : l'état s'affiche enfin dessus.
        setKruxState(KruxState.GENERATING);
        aiMessage.setReasoning("");

        final ApiClient.ApiCallback kruxCallback = new ApiClient.ApiCallback() {
            @Override
            public void onWebSearchStarted(String query) {
                kruxStep(aiMessage, "Recherche sur le web : « " + query + " »");
            }

            @Override
            public void onWebSearchFinished(List<SearchResult> results) {
                int n = results == null ? 0 : results.size();
                kruxStep(aiMessage, n == 0 ? "Aucune source trouvée" : n + (n == 1 ? " source trouvée" : " sources trouvées"));
            }

            @Override
            public void onSuccess(String rawResponse, String modelBrand) {
                kruxThought(aiMessage, reflexionOf(rawResponse));
                kruxFinishSteps(aiMessage);
                runOnUiThread(() -> {
                    setGeneratingState(false);
                    setKruxState(KruxState.IDLE);
                });

                String cleanResponse = com.virtixstudio.kruxai.utils.FileDelivery.demoteUnlessAsked(
                        stripReflexion(rawResponse), userPrompt);
                extractMemoryInBackground(userPrompt, cleanResponse);
                final String forFiles = cleanResponse;
                runOnUiThread(() -> { announceFiles(forFiles); deliverToWorkspace(forFiles); });

                boolean dropInterim = false;
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
                    if (!mediaQuery.isEmpty() && !wantVideo) dropInterim = true;
                    if (!mediaQuery.isEmpty()) {
                        runOnUiThread(() -> { if (wantVideo) MediaGallery.show(MainActivity.this, true, mediaQuery); else runImageGallery(mediaQuery, aiMessage, null); });
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
                                        memoryRepo.has(fact);

                                if (!alreadyExists) {
                                    memoryRepo.remember(fact);
                                }

                                boolean verifiedLocally =
                                        memoryRepo.has(fact);

                                android.util.Log.d(
                                        "KRUX_MEMORY",
                                        "SQLite mémoire "
                                                + (verifiedLocally
                                                ? "vérifiée"
                                                : "NON vérifiée")
                                                + " : "
                                                + fact
                                );

                                setKruxState(KruxState.IDLE);
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

                if (dropInterim) return; // la galerie d'images prend la place de ce message (voir runImageGallery)
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
                kruxThought(aiMessage, reflexionOf(partialResponse));
                runOnUiThread(() -> {
                    final String partialShown = com.virtixstudio.kruxai.utils.FileDelivery.demoteUnlessAsked(
                            stripReflexion(partialResponse), userPrompt);
                    aiMessage.setText(hideTool(partialShown));
                    announceFiles(partialShown);
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
                kruxFinishSteps(aiMessage);
                if (com.virtixstudio.kruxai.utils.NetPolicy.isNetworkMessage(errorMessage)) {
                    // Coupure réseau : pas de bouton « Réessayer », KRUX gère tout seul.
                    runOnUiThread(() -> {
                        setGeneratingState(false);
                        setKruxState(KruxState.IDLE);
                        aiMessage.setStreaming(false);
                        if (net != null && !net.isOnline()) {
                            queueForNet(userPrompt, fileName, fileContent, aiMessage);
                        } else if (netAttempt < com.virtixstudio.kruxai.utils.NetPolicy.MAX_AUTO) {
                            autoRetry(userPrompt, fileName, fileContent, aiMessage, netAttempt);
                        } else {
                            queueForNet(userPrompt, fileName, fileContent, aiMessage);
                        }
                    });
                    return;
                }
                runOnUiThread(() -> {
                    setGeneratingState(false);
                    setKruxState(KruxState.ERROR);
                });
                aiMessage.setStreaming(false);
                // Échec propre : message court + un seul bouton « Réessayer », pas de barre d'actions, rien d'enregistré.
                aiMessage.setText(errorMessage);
                aiMessage.setWaiting(true);
                runOnUiThread(() -> waitingMap.put(aiMessage, new NetPending(userPrompt, fileName, fileContent, aiMessage)));

                runOnUiThread(() -> {
                    int position = messageList.indexOf(aiMessage);
                    if (position >= 0) {
                        chatAdapter.notifyItemChanged(position);
                    }
                });
            }

            @Override
            public void onCancelled() {
                kruxFinishSteps(aiMessage);
                runOnUiThread(() -> {
                    setGeneratingState(false);
                    setKruxState(KruxState.IDLE);
                    aiMessage.setStreaming(false);
                    String partial = aiMessage.getText() == null ? "" : aiMessage.getText().trim();
                    int position = messageList.indexOf(aiMessage);
                    if (position >= 0 && partial.length() >= 20 && !partial.contains("<KRUX_")) {
                        // Réponse arrêtée en cours de route : on garde ce qui est écrit et on propose « Continuer ».
                        aiMessage.setStopped(true);
                        chatAdapter.notifyItemChanged(position);
                        saveMessageToDatabase(aiMessage);
                    } else {
                        aiMessage.setText("");
                        if (position >= 0) {
                            messageList.remove(position);
                            chatAdapter.notifyItemRemoved(position);
                        }
                    }
                });
            }
        };

        // Analyse préalable de la demande (seulement si elle le mérite), puis envoi avec les consignes.
        ApiClient.beginPending(kruxCallback);
        final java.util.List<String> imagesB64 = pendingRequestImages;
        pendingRequestImages = new java.util.ArrayList<>();
        final boolean withImage = !imagesB64.isEmpty();
        final com.virtixstudio.kruxai.core.Reflection.Ready ready =
                guidance -> {
                    if (ApiClient.wasCancelled()) return;
                    final String fullSystem = systemPrompt + guidance;
                    final String fullUser = promptWithContext.toString();
                    // Demande de fond (consignes non vides) : la réponse est relue avant d'être livrée.
                    final ApiClient.ApiCallback target = guidance.isEmpty()
                            ? kruxCallback
                            : com.virtixstudio.kruxai.core.AnswerCheck.wrap(kruxCallback, selectedKruxModel, fullSystem, fullUser,
                                    new com.virtixstudio.kruxai.core.AnswerCheck.Hook() {
                                        @Override public void onChecking() { }
                                        @Override public void onRevising() { }
                                    });
                    if (withImage) ApiClient.attachNextImages(imagesB64);
                    ApiClient.sendRequest(selectedKruxModel, fullSystem, fullUser, target);
                };
        // Avec une photo : ni analyse préalable ni relecture (elles ne voient pas l'image), envoi direct.
        if (withImage) ready.go("");
        else com.virtixstudio.kruxai.core.Reflection.analyze(
                userPrompt,
                fileContent != null && !fileContent.trim().isEmpty(),
                isThinkingMode,
                ready);
    }

    private static final String THOUGHT_MARK = "\u200B";

    /** Action réelle de Krux (ex. une recherche web) : ajoutée avant la réflexion, la précédente passe en ✓. */
    private void kruxStep(final ChatMessage m, final String step) {
        if (m == null || step == null) return;
        runOnUiThread(() -> {
            String r = m.getReasoning() == null ? "" : m.getReasoning();
            int cut = r.indexOf(THOUGHT_MARK);
            String actions = (cut < 0 ? r : r.substring(0, cut)).replaceAll("(?m)^• ", "✓ ");
            String thought = cut < 0 ? "" : r.substring(cut);
            m.setReasoning((actions.isEmpty() ? "" : actions + "\n") + "• " + step + thought);
            int pos = messageList.indexOf(m);
            if (pos >= 0 && chatAdapter != null) chatAdapter.notifyItemChanged(pos);
        });
    }

    /** La vraie réflexion écrite par le modèle (bloc <REFLEXION>), mise à jour en direct. */
    private void kruxThought(final ChatMessage m, final String thought) {
        if (m == null || thought == null) return;
        runOnUiThread(() -> {
            String r = m.getReasoning() == null ? "" : m.getReasoning();
            int cut = r.indexOf(THOUGHT_MARK);
            String actions = cut < 0 ? r : r.substring(0, cut);
            String t = thought.trim();
            String next = t.isEmpty() ? actions : actions + THOUGHT_MARK + t;
            if (next.equals(r)) return;
            m.setReasoning(next);
            int pos = messageList.indexOf(m);
            if (pos >= 0 && chatAdapter != null) chatAdapter.notifyItemChanged(pos);
        });
    }

    private String reflexionOf(String s) {
        if (s == null) return "";
        int a = s.indexOf("<REFLEXION>");
        if (a < 0) return "";
        a += 11;
        int b = s.indexOf("</REFLEXION>", a);
        return (b < 0 ? s.substring(a) : s.substring(a, b)).trim();
    }

    /** Fin du travail : les actions passent en ✓ (le bloc se replie tout seul). */
    private void kruxFinishSteps(final ChatMessage m) {
        if (m == null) return;
        runOnUiThread(() -> {
            String r = m.getReasoning();
            if (r == null || r.isEmpty()) return;
            m.setReasoning(r.replaceAll("(?m)^• ", "✓ "));
            int pos = messageList.indexOf(m);
            if (pos >= 0 && chatAdapter != null) chatAdapter.notifyItemChanged(pos);
        });
    }

    private String quotaWeek() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        return c.getWeekYear() + "-W" + c.get(java.util.Calendar.WEEK_OF_YEAR);
    }

    private int cloudQuotaUsed = -1;
    private String cloudQuotaWeek = "";

    private int weeklyQuota() {
        try {
            java.io.InputStream is = getAssets().open("krux_builder.json");
            byte[] buf = new byte[is.available()];
            is.read(buf);
            is.close();
            return new org.json.JSONObject(new String(buf, java.nio.charset.StandardCharsets.UTF_8)).optInt("weekly_quota", 3);
        } catch (Exception e) {
            return 2;
        }
    }

    /** Quota utilisé cette semaine : le plus grand entre le compteur local et celui du serveur (Firestore). */
    private int quotaUsed() {
        android.content.SharedPreferences sp = getSharedPreferences("krux_builder", MODE_PRIVATE);
        int local = quotaWeek().equals(sp.getString("quota_week", "")) ? sp.getInt("quota_used", 0) : 0;
        int cloud = quotaWeek().equals(cloudQuotaWeek) ? cloudQuotaUsed : -1;
        return Math.max(local, cloud);
    }

    /** Met à jour la carte « Créations d'apps cette semaine » du menu. */
    private void refreshQuotaView() {
        try {
            android.widget.TextView detail = findViewById(R.id.tvQuotaDetail);
            android.widget.ProgressBar bar = findViewById(R.id.quotaBar);
            if (detail == null || bar == null) return;
            int max = Math.max(1, weeklyQuota());
            int used = Math.min(quotaUsed(), max);
            int left = max - used;
            bar.setMax(max);
            bar.setProgress(used);
            bar.setProgressTintList(android.content.res.ColorStateList.valueOf(KruxTheme.get(this, "accent")));
            bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(KruxTheme.get(this, "bg")));
            detail.setText(used + " / " + max + " utilisées · "
                    + (left > 0 ? left + (left > 1 ? " restantes" : " restante") : "limite atteinte, retour la semaine prochaine")
                    + "\nSeules les applications qui compilent sont comptées.");
        } catch (Exception ignored) { }
    }

    private void refreshCloudQuota() {
        if (currentUser == null) return;
        final String week = quotaWeek();
        com.virtixstudio.kruxai.core.QuotaStore.fetch(currentUser.getUid(), week, used -> {
            cloudQuotaUsed = used;
            cloudQuotaWeek = week;
            runOnUiThread(this::refreshQuotaView);
        });
    }

    private static final int MAX_FAILED_BUILDS = 6;

    private int failedBuilds() {
        android.content.SharedPreferences sp = getSharedPreferences("krux_builder", MODE_PRIVATE);
        return quotaWeek().equals(sp.getString("fail_week", "")) ? sp.getInt("fail_used", 0) : 0;
    }

    /** Un échec de compilation ne coûte pas une création, mais on en tolère un nombre limité par semaine pour ne pas saturer le service. */
    private void countFailedBuild(String message) {
        if (message != null && (message.contains("annul") || message.contains("GitHub 401") || message.contains("GitHub 403") || message.contains("GitHub 429"))) return;
        getSharedPreferences("krux_builder", MODE_PRIVATE).edit()
                .putString("fail_week", quotaWeek()).putInt("fail_used", failedBuilds() + 1).apply();
    }

    private void consumeQuota() {
        android.content.SharedPreferences sp = getSharedPreferences("krux_builder", MODE_PRIVATE);
        int next = quotaUsed() + 1;
        sp.edit().putString("quota_week", quotaWeek()).putInt("quota_used", next).apply();
        cloudQuotaUsed = next;
        cloudQuotaWeek = quotaWeek();
        refreshQuotaView();
        if (currentUser != null) com.virtixstudio.kruxai.core.QuotaStore.consume(currentUser.getUid(), quotaWeek());
    }

    /** Dossier de l'APK de l'app à modifier ; lu puis remis à null au lancement de la création. */
    private java.io.File modifyFrom;

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleModifyIntent(intent);
    }

    /** « Modifier » dans Mes apps : dossier de l'app + texte de la modification. */
    private void handleModifyIntent(android.content.Intent intent) {
        if (intent == null) return;
        String pre = intent.getStringExtra("prefill_text");
        if (pre != null) {
            intent.removeExtra("prefill_text");
            if (!pre.trim().isEmpty() && etInput != null) {
                etInput.setText(pre);
                etInput.setSelection(etInput.getText().length());
                etInput.requestFocus();
            }
        }
        String createDesc = intent.getStringExtra("create_app");
        if (createDesc != null) {
            intent.removeExtra("create_app");
            final String cd = createDesc.trim();
            if (!cd.isEmpty() && etInput != null) etInput.post(() -> autoCreateApp(cd));
        }
        String dir = intent.getStringExtra("modify_dir"), text = intent.getStringExtra("modify_text");
        intent.removeExtra("modify_dir");
        intent.removeExtra("modify_text");
        if (dir == null || text == null || text.trim().isEmpty()) return;
        autoModifyApp(new java.io.File(dir), text.trim());
    }

    private void autoModifyApp(final java.io.File dir, final String request) {
        String token = "";
        int quota = 5;
        try {
            java.io.InputStream is = getAssets().open("krux_builder.json");
            byte[] buf = new byte[is.available()];
            is.read(buf);
            is.close();
            org.json.JSONObject cfg = new org.json.JSONObject(new String(buf, java.nio.charset.StandardCharsets.UTF_8));
            token = cfg.optString("token", "").trim();
            quota = cfg.optInt("weekly_quota", 3);
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
        if (failedBuilds() >= MAX_FAILED_BUILDS) {
            Toast.makeText(this, "Trop d'échecs de compilation cette semaine. Réessaie plus tard ou change la demande.", Toast.LENGTH_LONG).show();
            return;
        }
        final String fToken = token;
        String shown = request.length() > 300 ? request.substring(0, 300) + "…" : request;
        KruxTheme.skin(new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Modifier cette application ?")
                .setMessage(shown + "\n\nLa version modifiée s'installe à côté de l'ancienne. Elle compte comme une création : "
                        + (quota - used) + "/" + quota + " restantes cette semaine.")
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Lancer", (d, w) -> { modifyFrom = dir; runAppBuild(fToken, request); })
                .show(), this);
    }

    private void autoCreateApp(final String desc) {
        String token = "";
        int quota = 5;
        try {
            java.io.InputStream is = getAssets().open("krux_builder.json");
            byte[] buf = new byte[is.available()];
            is.read(buf);
            is.close();
            org.json.JSONObject cfg = new org.json.JSONObject(new String(buf, java.nio.charset.StandardCharsets.UTF_8));
            token = cfg.optString("token", "").trim();
            quota = cfg.optInt("weekly_quota", 3);
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
        if (failedBuilds() >= MAX_FAILED_BUILDS) {
            Toast.makeText(this, "Trop d'échecs de compilation cette semaine. Réessaie plus tard ou change la demande.", Toast.LENGTH_LONG).show();
            return;
        }
        final String fToken = token;
        String shown = desc.length() > 300 ? desc.substring(0, 300) + "…" : desc;
        KruxTheme.skin(new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Créer cette application ?")
                .setMessage(shown + "\n\nCréations restantes cette semaine : " + (quota - used) + "/" + quota
                       )
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Continuer", (d, w) -> showAppQuestions(desc, brief -> startAppBuild(fToken, brief)))
                .show(), this);
    }

    // ---------------------------------------------------------------- panneau de questions avant la création

    private String questionIconB64 = null;
    private android.widget.ImageView questionIconPreview;
    private TextView questionIconLabel;

    private final ActivityResultLauncher<String> appIconPicker =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> { if (uri != null) onAppIconPicked(uri); }
            );

    /** Image importée : recadrée en carré, 192 px, gardée en PNG (base64) pour devenir l'icône de l'app. */
    private void onAppIconPicked(android.net.Uri uri) {
        try {
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                android.graphics.BitmapFactory.decodeStream(in, null, o);
            }
            int sample = 1;
            while (o.outWidth / (sample * 2) >= 512 && o.outHeight / (sample * 2) >= 512) sample *= 2;
            o = new android.graphics.BitmapFactory.Options();
            o.inSampleSize = sample;
            android.graphics.Bitmap src;
            try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                src = android.graphics.BitmapFactory.decodeStream(in, null, o);
            }
            if (src == null) throw new java.io.IOException("image illisible");
            int side = Math.min(src.getWidth(), src.getHeight());
            android.graphics.Bitmap square = android.graphics.Bitmap.createBitmap(
                    src, (src.getWidth() - side) / 2, (src.getHeight() - side) / 2, side, side);
            android.graphics.Bitmap icon = android.graphics.Bitmap.createScaledBitmap(square, 192, 192, true);
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            icon.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bo);
            questionIconB64 = android.util.Base64.encodeToString(bo.toByteArray(), android.util.Base64.NO_WRAP);
            if (questionIconPreview != null) questionIconPreview.setImageBitmap(icon);
            if (questionIconLabel != null) questionIconLabel.setText("Icône importée");
        } catch (Exception e) {
            Toast.makeText(this, "Image impossible à lire : " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void paintChip(TextView c, boolean on, int accent, int text) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(18));
        g.setColor(on ? accent : 0xFF1F1A2E);
        c.setBackground(g);
        c.setTextColor(on ? 0xFF0B0A12 : text);
    }

    /** Une rangée de choix : un seul (multi = false) ou plusieurs ; sel[] garde l'état. */
    private android.widget.HorizontalScrollView chipRow(final String[] opts, final boolean[] sel, final boolean multi,
                                                        final int accent, final int text, final Runnable onChange) {
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        final android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        hs.addView(row);
        final TextView[] chips = new TextView[opts.length];
        for (int i = 0; i < opts.length; i++) {
            final int idx = i;
            TextView c = new TextView(this);
            c.setText(opts[i]);
            c.setTextSize(13);
            c.setGravity(android.view.Gravity.CENTER);
            c.setPadding(dp(14), 0, dp(14), 0);
            c.setClickable(true);
            chips[i] = c;
            paintChip(c, sel[i], accent, text);
            c.setOnClickListener(v -> {
                if (multi) {
                    sel[idx] = !sel[idx];
                } else {
                    for (int k = 0; k < sel.length; k++) sel[k] = (k == idx);
                }
                for (int k = 0; k < chips.length; k++) paintChip(chips[k], sel[k], accent, text);
                if (onChange != null) onChange.run();
            });
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(-2, dp(38));
            lp.rightMargin = dp(8);
            row.addView(c, lp);
        }
        return hs;
    }

    private TextView questionLabel(String title, String sub, int text) {
        TextView t = new TextView(this);
        t.setText(sub == null ? title : title + "\n" + sub);
        t.setTextColor(text);
        t.setTextSize(14);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setPadding(0, dp(16), 0, dp(8));
        return t;
    }

    /** Panneau « quelques questions » avant de créer l'app : style, fonctions, nom, icône, base de données. */
    private void showAppQuestions(final String desc, final java.util.function.Consumer<String> go) {
        final BottomSheetDialog dlg = new BottomSheetDialog(this);
        final int accent = KruxTheme.get(this, "accent");
        final int text = 0xFFF8FAFC;
        final int dim = 0xFF9A90AD;
        questionIconB64 = null;

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        android.widget.LinearLayout col = new android.widget.LinearLayout(this);
        col.setOrientation(android.widget.LinearLayout.VERTICAL);
        col.setBackgroundColor(0xFF111318);
        col.setPadding(dp(18), dp(14), dp(18), dp(20));
        sv.addView(col);

        TextView head = new TextView(this);
        head.setText("Précise ton application");
        head.setTextColor(text);
        head.setTextSize(19);
        head.setTypeface(null, android.graphics.Typeface.BOLD);
        col.addView(head);
        TextView sub = new TextView(this);
        sub.setText("Quelques choix, Krux s'occupe du reste. Tout est facultatif.");
        sub.setTextColor(dim);
        sub.setTextSize(13);
        col.addView(sub);

        // Style
        final String[] styles = {"Moderne", "Sombre", "Coloré", "Minimal"};
        final boolean[] styleSel = {true, false, false, false};
        col.addView(questionLabel("Style visuel", null, text));
        col.addView(chipRow(styles, styleSel, false, accent, text, null));

        // Fonctions en plus
        final String[] extras = {"Mode sombre", "Rappels", "Partage", "Export des données", "Statistiques"};
        final boolean[] extraSel = new boolean[extras.length];
        col.addView(questionLabel("Fonctions en plus", null, text));
        col.addView(chipRow(extras, extraSel, true, accent, text, null));

        // Nom
        col.addView(questionLabel("Nom de l'application", "Si tu laisses vide, Krux choisit.", text));
        final android.widget.EditText etName = new android.widget.EditText(this);
        etName.setHint("Ex. Mon carnet");
        etName.setHintTextColor(dim);
        etName.setTextColor(text);
        etName.setTextSize(14);
        etName.setSingleLine(true);
        etName.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable nb = new GradientDrawable();
        nb.setColor(0xFF1F1A2E);
        nb.setCornerRadius(dp(14));
        etName.setBackground(nb);
        col.addView(etName);

        // Icône
        col.addView(questionLabel("Icône", "Importe une image : elle devient l'icône de l'application.", text));
        android.widget.LinearLayout iconRow = new android.widget.LinearLayout(this);
        iconRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        questionIconPreview = new android.widget.ImageView(this);
        questionIconPreview.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        GradientDrawable ib = new GradientDrawable();
        ib.setColor(0xFF1F1A2E);
        ib.setCornerRadius(dp(14));
        questionIconPreview.setBackground(ib);
        iconRow.addView(questionIconPreview, new android.widget.LinearLayout.LayoutParams(dp(56), dp(56)));
        questionIconLabel = new TextView(this);
        questionIconLabel.setText("Aucune : Krux en crée une");
        questionIconLabel.setTextColor(dim);
        questionIconLabel.setTextSize(13);
        questionIconLabel.setPadding(dp(12), 0, dp(12), 0);
        iconRow.addView(questionIconLabel, new android.widget.LinearLayout.LayoutParams(0, -2, 1f));
        TextView pick = new TextView(this);
        pick.setText("Importer");
        pick.setTextSize(13);
        pick.setGravity(android.view.Gravity.CENTER);
        pick.setPadding(dp(14), 0, dp(14), 0);
        pick.setClickable(true);
        paintChip(pick, true, accent, text);
        pick.setOnClickListener(v -> appIconPicker.launch("image/*"));
        iconRow.addView(pick, new android.widget.LinearLayout.LayoutParams(-2, dp(38)));
        col.addView(iconRow);

        // Base de données
        final String[] dbs = {"Pas de base de données", "Oui, Supabase", "Plus tard"};
        final boolean[] dbSel = {true, false, false};
        col.addView(questionLabel("Base de données", "Pour sauvegarder les données en ligne.", text));
        final TextView dbNote = new TextView(this);
        dbNote.setText("Krux prépare l'app pour se brancher à Supabase (gratuit, sans carte). Le guide pas à pas pour créer ton projet arrive dans une prochaine mise à jour ; en attendant les données restent sur le téléphone.");
        dbNote.setTextColor(dim);
        dbNote.setTextSize(12);
        dbNote.setPadding(0, dp(8), 0, 0);
        dbNote.setVisibility(View.GONE);
        col.addView(chipRow(dbs, dbSel, false, accent, text, () -> dbNote.setVisibility(dbSel[1] ? View.VISIBLE : View.GONE)));
        col.addView(dbNote);

        // Boutons
        TextView create = new TextView(this);
        create.setText("Créer l'application");
        create.setTextSize(15);
        create.setTypeface(null, android.graphics.Typeface.BOLD);
        create.setGravity(android.view.Gravity.CENTER);
        create.setClickable(true);
        paintChip(create, true, accent, text);
        android.widget.LinearLayout.LayoutParams cp = new android.widget.LinearLayout.LayoutParams(-1, dp(48));
        cp.topMargin = dp(22);
        col.addView(create, cp);
        TextView cancel = new TextView(this);
        cancel.setText("Annuler");
        cancel.setTextColor(dim);
        cancel.setTextSize(14);
        cancel.setGravity(android.view.Gravity.CENTER);
        cancel.setClickable(true);
        cancel.setOnClickListener(v -> dlg.dismiss());
        col.addView(cancel, new android.widget.LinearLayout.LayoutParams(-1, dp(44)));

        create.setOnClickListener(v -> {
            StringBuilder b = new StringBuilder(desc);
            b.append("\n\nCHOIX DE L'UTILISATEUR :");
            for (int i = 0; i < styles.length; i++) if (styleSel[i]) b.append(" style ").append(styles[i].toLowerCase()).append(".");
            StringBuilder ex = new StringBuilder();
            for (int i = 0; i < extras.length; i++) if (extraSel[i]) ex.append(ex.length() > 0 ? ", " : "").append(extras[i].toLowerCase());
            if (ex.length() > 0) b.append(" Fonctions en plus : ").append(ex).append(".");
            String nm = etName.getText().toString().trim();
            if (!nm.isEmpty()) b.append(" Nom de l'app : ").append(nm).append(".");
            if (questionIconB64 != null) b.append(" L'icône est fournie par l'utilisateur : n'en crée aucune.");
            if (dbSel[1]) b.append(" Isole l'accès aux données dans un seul fichier (src/data.ts), stockage local pour l'instant, facile à brancher sur Supabase ensuite.");
            com.virtixstudio.kruxai.core.AppOptions.setIcon(questionIconB64);
            dlg.dismiss();
            go.accept(b.toString());
        });

        dlg.setContentView(sv);
        dlg.setOnShowListener(d2 -> dlg.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED));
        dlg.setOnDismissListener(d2 -> { questionIconPreview = null; questionIconLabel = null; });
        dlg.show();
    }

    /** Envoie le code source compressé (et l'APK s'il fait 10 Mo ou moins) sur les serveurs Virtix Studio. */
    private void uploadAppToCloud(final java.io.File apk, final String repoUrl) {
        if (!com.virtixstudio.kruxai.core.KruxConfig.workerEnabled() || currentUser == null) return;
        final com.google.firebase.auth.FirebaseUser user = currentUser;
        final String id = "app-" + System.currentTimeMillis() / 1000;
        new Thread(() -> {
            try {
                java.io.File src = new java.io.File(apk.getParentFile(), "krux-checkpoint.bin");
                String srcPath = null, apkPath = null;
                if (src.isFile()) {
                    srcPath = "apps/" + id + ".krx";
                    com.virtixstudio.kruxai.core.WorkerStore.put(user, srcPath, src);
                }
                if (apk.length() <= com.virtixstudio.kruxai.core.WorkerStore.MAX_BYTES) {
                    apkPath = "apps/" + id + ".apk";
                    try { com.virtixstudio.kruxai.core.WorkerStore.put(user, apkPath, apk); }
                    catch (Exception e) { apkPath = null; android.util.Log.e("KRUX_BUILD", "apk cloud", e); }
                }
                android.content.SharedPreferences sp = getSharedPreferences("krux_builder", MODE_PRIVATE);
                org.json.JSONArray arr = new org.json.JSONArray(sp.getString("apps", "[]"));
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject o = arr.getJSONObject(i);
                    if (repoUrl.equals(o.optString("repo"))) {
                        if (srcPath != null) o.put("cloud_src", srcPath);
                        if (apkPath != null) o.put("cloud_apk", apkPath);
                        break;
                    }
                }
                sp.edit().putString("apps", arr.toString()).apply();
                java.util.HashMap<String, Object> doc = new java.util.HashMap<>();
                doc.put("src", srcPath); doc.put("apk", apkPath); doc.put("time", System.currentTimeMillis());
                db.collection("users").document(user.getUid()).collection("apps").add(doc);
            } catch (Exception e) {
                android.util.Log.e("KRUX_BUILD", "cloud", e);
            }
        }).start();
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

    /** Estime d'abord l'ampleur de l'app : si elle est ambitieuse, propose une version simplifiée. */
    private void startAppBuild(final String token, final String desc) {
        final com.virtixstudio.kruxai.builder.BuildEstimator.Estimate est =
                com.virtixstudio.kruxai.builder.BuildEstimator.estimate(desc);
        if (!est.large) {
            runAppBuild(token, desc);
            return;
        }
        KruxTheme.skin(new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Application ambitieuse")
                .setMessage(est.message())
                .setPositiveButton("Version simplifiée", (d, w) ->
                        runAppBuild(token, com.virtixstudio.kruxai.builder.BuildEstimator.simplified(desc)))
                .setNegativeButton("Tout créer", (d, w) -> runAppBuild(token, desc))
                .show(), this);
    }

    /** La progression s'affiche dans une bulle de KRUX, avec la liste des fichiers au fur et à mesure. */
    private void runAppBuild(String token, String desc) {
        final com.virtixstudio.kruxai.builder.BuildProgress progress = new com.virtixstudio.kruxai.builder.BuildProgress();
        final ChatMessage bubble = new ChatMessage(progress.render(), false);
        bubble.setStreaming(true); // copier, partager, avis : seulement une fois l'app terminée
        if (rvChat.getItemAnimator() instanceof androidx.recyclerview.widget.SimpleItemAnimator)
            ((androidx.recyclerview.widget.SimpleItemAnimator) rvChat.getItemAnimator()).setSupportsChangeAnimations(false);
        runOnUiThread(() -> {
            messageList.add(bubble);
            chatAdapter.notifyItemInserted(messageList.size() - 1);
            rvChat.smoothScrollToPosition(messageList.size() - 1);
        });
        final Runnable refresh = () -> runOnUiThread(() -> {
            bubble.setText(progress.render());
            int pos = messageList.indexOf(bubble);
            if (pos >= 0) {
                boolean atBottom = !rvChat.canScrollVertically(1);
                chatAdapter.notifyItemChanged(pos);
                if (atBottom) rvChat.scrollToPosition(messageList.size() - 1);
            }
        });
        // Mise à jour du chrono : aucun défilement, et rien si le texte n'a pas changé
        final Runnable refreshQuiet = () -> runOnUiThread(() -> {
            String t = progress.render();
            if (t.equals(bubble.getText())) return;
            bubble.setText(t);
            int pos = messageList.indexOf(bubble);
            if (pos >= 0) chatAdapter.notifyItemChanged(pos);
        });

        final boolean[] consumed = {false};
        final String[] agentNote = {""};
        java.io.File outDir = getExternalFilesDir("generated");
        final java.io.File modSrc = modifyFrom;
        modifyFrom = null;

        // Pendant la création : options du bas masquées, bouton carré (stop), et une bulle « vivante »
        final android.os.Handler pulse = new android.os.Handler(android.os.Looper.getMainLooper());
        final long started = System.currentTimeMillis();
        final int[] ticks = {0};
        final boolean[] building = {true};
        pulse.post(new Runnable() {
            @Override public void run() {
                if (!building[0]) return;
                progress.setPulse(ticks[0]++, (int) ((System.currentTimeMillis() - started) / 1000));
                refreshQuiet.run();
                pulse.postDelayed(this, 1000);
            }
        });
        final Runnable endBuild = () -> {
            building[0] = false;
            bubble.setStreaming(false);
            progress.setPulse(0, (int) ((System.currentTimeMillis() - started) / 1000));
            runOnUiThread(() -> setBuildMode(false));
        };
        runOnUiThread(() -> setBuildMode(true));

        com.virtixstudio.kruxai.core.KruxConfig.init(getApplicationContext());
        com.virtixstudio.kruxai.builder.AgentBrief.setPlaybook(readAssetText("AGENT.md"));
        AppBuilder.run(token, desc, outDir, modSrc,
                (system, prompt) -> {
                    final String[] out = new String[2];
                    final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                    ApiClient.sendRequest(
                            // la création d'app exige un grand modèle, même si le chat est réglé sur un petit
                            (selectedKruxModel != null && selectedKruxModel.getSize() == KruxModel.ModelSize.GRAND)
                                    ? selectedKruxModel : KruxModel.KRUX_35_FLASH,
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

                                @Override
                                public void onFallback() {
                                    progress.setStep("Je prends une courte pause…");
                                    refresh.run();
                                }

                                @Override
                                public void onWaiting(final int seconds) {
                                    progress.setStep("Je prends une courte pause, je reprends dans " + seconds + " s…");
                                    refresh.run();
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
                        String t = null;
                        if (status.startsWith("Réflexion")) t = "Je réfléchis à ton application…";
                        else if (status.startsWith("Génération")) t = "J'écris ton application…";
                        else if (status.startsWith("Vérification")) t = "Je vérifie le code…";
                        else if (status.startsWith("Création") || status.startsWith("Envoi")) t = "J'envoie le code sur les serveurs Virtix Studio…";
                        else if (status.startsWith("Build échoué") || status.startsWith("Correction")) t = "Je corrige les erreurs…";
                        else if (status.contains("ompilation")) t = "Je compile ton application…";
                        else if (status.startsWith("Téléchargement")) t = "Je récupère ton APK…";
                        if (t != null) progress.setTitle(t);
                        progress.setStep(status.startsWith("Création du repo")
                                ? "Création du projet…"
                                : status.replace("sur GitHub", "sur les serveurs Virtix Studio").replace("GitHub", "Virtix Studio"));
                        refresh.run();
                    }

                    @Override
                    public void onNote(final String note) {
                        agentNote[0] = note == null ? "" : note;
                    }

                    @Override
                    public boolean confirmPreview(final String html) {
                        progress.setTitle("Voici l'aperçu de ton app");
                        progress.setStep("Teste la maquette, puis valide pour continuer.");
                        refresh.run();
                        final boolean[] accepted = {false};
                        final android.app.Dialog[] shown = {null};
                        final java.util.concurrent.CountDownLatch decided = new java.util.concurrent.CountDownLatch(1);
                        runOnUiThread(() -> {
                            final int bg = KruxTheme.get(MainActivity.this, "bg");
                            final int accent = KruxTheme.get(MainActivity.this, "accent");
                            final int tx = KruxTheme.get(MainActivity.this, "text");
                            final int tx2 = KruxTheme.get(MainActivity.this, "text2");
                            final android.app.Dialog dlg = new android.app.Dialog(MainActivity.this);
                            dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
                            dlg.setCancelable(false);

                            android.widget.LinearLayout root = new android.widget.LinearLayout(MainActivity.this);
                            root.setOrientation(android.widget.LinearLayout.VERTICAL);
                            root.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(14));
                            android.graphics.drawable.GradientDrawable sheet = new android.graphics.drawable.GradientDrawable();
                            sheet.setColor(bg);
                            sheet.setCornerRadius(dpToPx(24));
                            root.setBackground(sheet);

                            TextView title = new TextView(MainActivity.this);
                            title.setText("Aperçu de ton application");
                            title.setTextColor(tx);
                            title.setTextSize(18);
                            title.setTypeface(null, android.graphics.Typeface.BOLD);
                            root.addView(title);
                            TextView hint = new TextView(MainActivity.this);
                            hint.setText("Touche les boutons : l'aperçu est interactif, comme dans l'app finale.");
                            hint.setTextColor(tx2);
                            hint.setTextSize(13);
                            hint.setPadding(0, dpToPx(4), 0, dpToPx(12));
                            root.addView(hint);

                            // Maquette interactive : JavaScript permis, mais réseau, fichiers et stockage bloqués.
                            final android.webkit.WebView web = new android.webkit.WebView(MainActivity.this);
                            android.webkit.WebSettings ws = web.getSettings();
                            ws.setJavaScriptEnabled(true);
                            ws.setJavaScriptCanOpenWindowsAutomatically(false);
                            ws.setSupportMultipleWindows(false);
                            ws.setBlockNetworkLoads(true);
                            ws.setAllowFileAccess(false);
                            ws.setAllowContentAccess(false);
                            ws.setDomStorageEnabled(false);
                            ws.setDatabaseEnabled(false);
                            ws.setGeolocationEnabled(false);
                            web.setWebViewClient(new android.webkit.WebViewClient() {
                                @Override
                                public boolean shouldOverrideUrlLoading(android.webkit.WebView v, android.webkit.WebResourceRequest r) {
                                    return true; // aucune navigation hors de la maquette
                                }
                            });
                            web.setBackgroundColor(android.graphics.Color.WHITE);
                            web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);

                            // cadre arrondi façon téléphone
                            final android.widget.FrameLayout frame = new android.widget.FrameLayout(MainActivity.this);
                            android.graphics.drawable.GradientDrawable fg = new android.graphics.drawable.GradientDrawable();
                            fg.setColor(android.graphics.Color.WHITE);
                            fg.setCornerRadius(dpToPx(20));
                            fg.setStroke(dpToPx(2), (accent & 0x00FFFFFF) | 0x99000000);
                            frame.setBackground(fg);
                            frame.setClipToOutline(true);
                            frame.addView(web, new android.widget.FrameLayout.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT));
                            root.addView(frame, new android.widget.LinearLayout.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

                            android.widget.LinearLayout row = new android.widget.LinearLayout(MainActivity.this);
                            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                            row.setGravity(android.view.Gravity.END);
                            row.setPadding(0, dpToPx(12), 0, 0);
                            TextView cancel = previewButton("Annuler", tx2, android.graphics.Color.TRANSPARENT);
                            int onAccent = androidx.core.graphics.ColorUtils.calculateLuminance(accent) > 0.5
                                    ? android.graphics.Color.BLACK : android.graphics.Color.WHITE;
                            TextView ok = previewButton("Créer l'app", onAccent, accent);
                            cancel.setOnClickListener(v -> { decided.countDown(); dlg.dismiss(); });
                            ok.setOnClickListener(v -> { accepted[0] = true; decided.countDown(); dlg.dismiss(); });
                            row.addView(cancel);
                            android.widget.LinearLayout.LayoutParams okLp = new android.widget.LinearLayout.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
                            okLp.leftMargin = dpToPx(8);
                            row.addView(ok, okLp);
                            root.addView(row);

                            dlg.setContentView(root);
                            dlg.setOnDismissListener(d -> {
                                web.stopLoading();
                                frame.removeAllViews();
                                web.destroy();
                                decided.countDown(); // sécurité : jamais d'attente infinie
                            });
                            shown[0] = dlg;
                            dlg.show();
                            if (dlg.getWindow() != null) {
                                android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
                                dlg.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                                dlg.getWindow().setLayout(dm.widthPixels - dpToPx(20), (int) (dm.heightPixels * 0.88f));
                            }
                        });
                        try {
                            if (!decided.await(10, java.util.concurrent.TimeUnit.MINUTES)) {
                                runOnUiThread(() -> { if (shown[0] != null) shown[0].dismiss(); });
                                return false;
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return false;
                        }
                        if (accepted[0]) {
                            progress.setTitle("J'envoie le code sur les serveurs Virtix Studio…");
                            progress.setStep("Création du projet…");
                            refresh.run();
                        }
                        return accepted[0];
                    }

                    @Override
                    public java.util.Map<String, String> confirmWebPreview(final java.util.Map<String, String> files, final AppBuilder.Refiner refiner) {
                        progress.setTitle("Voici l'aperçu de ton app");
                        progress.setStep("Teste-la, retouche à la main ou demande à Krux, puis valide pour continuer.");
                        refresh.run();
                        java.util.Map<String, String> out = WebPreviewDialog.show(MainActivity.this, files, refiner);
                        if (out != null) {
                            progress.setTitle("J'envoie le code sur les serveurs Virtix Studio…");
                            progress.setStep("Création du projet…");
                            refresh.run();
                        }
                        return out;
                    }

                    @Override
                    public void onFile(final String path, final int bytes, final boolean fixed) {
                        progress.addFile(path, bytes, fixed);
                        refresh.run();
                    }

                    @Override
                    public void onDone(final java.io.File apk, final String repoUrl) {
                        endBuild.run();
                        progress.finish("Ton application est prête",
                                "Android va te proposer de l'installer. Retrouve-la plus tard dans Paramètres, puis Mes apps."
                                + (agentNote[0].isEmpty() ? "" : "\n\n" + agentNote[0]));
                        refresh.run();
                        runOnUiThread(() -> {
                            // seule une application réellement compilée compte dans le quota de la semaine
                            if (!consumed[0]) { consumed[0] = true; consumeQuota(); }
                            saveBuiltApp(apk, repoUrl);
                            uploadAppToCloud(apk, repoUrl);
                            installApk(apk);
                        });
                    }

                    @Override
                    public void onError(final String message) {
                        android.util.Log.e("KRUX_BUILD", String.valueOf(message));
                        countFailedBuild(message);
                        endBuild.run();
                        progress.fail(friendlyBuildError(message));
                        refresh.run();
                    }
                });
    }

    private TextView previewButton(String label, int textColor, int fill) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextSize(15);
        b.setTypeface(null, android.graphics.Typeface.BOLD);
        b.setTextColor(textColor);
        b.setGravity(android.view.Gravity.CENTER);
        b.setPadding(dpToPx(20), dpToPx(12), dpToPx(20), dpToPx(12));
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dpToPx(14));
        b.setBackground(g);
        b.setClickable(true);
        return b;
    }

    private String friendlyBuildError(String m) {
        if (m == null) return "Erreur inconnue.";
        if (m.startsWith("Création annulée")) return m;
        if (m.contains("annulée") || m.contains("annulé")) return "Création arrêtée. Tu peux la relancer, je reprendrai là où j'en étais.";
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

    private android.content.res.ColorStateList plusTintOriginal;
    private boolean plusTintSaved = false;

    /** Le « + » prend la couleur d'accent quand le mode Réflexion ou Apprentissage est actif. */
    private void updatePlusIndicator() {
        if (btnPlus == null) return;
        if (!plusTintSaved) {
            plusTintOriginal = btnPlus.getImageTintList();
            plusTintSaved = true;
        }
        boolean active = isThinkingMode || isLearningMode;
        btnPlus.setImageTintList(active
                ? android.content.res.ColorStateList.valueOf(KruxTheme.get(this, "accent"))
                : plusTintOriginal);
    }

    private void bindPlusSwitch(View row, androidx.appcompat.widget.SwitchCompat sw, boolean on, int accent,
                                android.widget.CompoundButton.OnCheckedChangeListener listener) {
        if (row == null || sw == null) return;
        int[][] states = new int[][]{new int[]{android.R.attr.state_checked}, new int[0]};
        sw.setThumbTintList(new android.content.res.ColorStateList(states, new int[]{accent, 0xFFB9AFCB}));
        sw.setTrackTintList(new android.content.res.ColorStateList(states, new int[]{(accent & 0x00FFFFFF) | 0x66000000, 0x33FFFFFF}));
        sw.setChecked(on);
        sw.setOnCheckedChangeListener(listener);
        row.setOnClickListener(v -> sw.toggle());
    }

    private void showPlusBottomSheet() {
        final BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_plus, null);
        dialog.setContentView(view);
        KruxTheme.applyTo(view, this);
        final int accent = KruxTheme.get(this, "accent");

        // Grandes icônes : joindre une photo ou un fichier
        view.findViewById(R.id.optCamera).setOnClickListener(v -> { dialog.dismiss(); launchCamera(); });
        view.findViewById(R.id.optPhoto).setOnClickListener(v -> { dialog.dismiss(); imagePicker.launch("image/*"); });
        view.findViewById(R.id.optFiles).setOnClickListener(v -> { dialog.dismiss(); filePicker.launch(new String[]{"*/*"}); });

        // Réglages : le menu reste ouvert, l'état se voit tout de suite
        bindPlusSwitch(view.findViewById(R.id.rowWeb), (androidx.appcompat.widget.SwitchCompat) view.findViewById(R.id.swWeb),
                isDeepSearchEnabled, accent, (b, on) -> {
                    isDeepSearchEnabled = on;
                    Toast.makeText(this, on ? "Recherche web activée" : "Recherche web désactivée", Toast.LENGTH_SHORT).show();
                });
        bindPlusSwitch(view.findViewById(R.id.rowThinking), (androidx.appcompat.widget.SwitchCompat) view.findViewById(R.id.swThinking),
                isThinkingMode, accent, (b, on) -> {
                    isThinkingMode = on;
                    updatePlusIndicator();
                    Toast.makeText(this, on ? "Mode Réflexion activé : jusqu'à ×4 de quota par message" : "Mode Réflexion désactivé",
                            Toast.LENGTH_SHORT).show();
                });
        bindPlusSwitch(view.findViewById(R.id.rowLearning), (androidx.appcompat.widget.SwitchCompat) view.findViewById(R.id.swLearning),
                isLearningMode, accent, (b, on) -> {
                    isLearningMode = on;
                    updatePlusIndicator();
                    Toast.makeText(this, on ? "Mode Apprentissage activé" : "Mode Apprentissage désactivé", Toast.LENGTH_SHORT).show();
                });

        // Actions
        view.findViewById(R.id.optLive).setOnClickListener(v -> { dialog.dismiss(); launchLive(); });
        TextView tvFloat = view.findViewById(R.id.tvFloatTitle);
        if (tvFloat != null) {
            tvFloat.setText(com.virtixstudio.kruxai.live.FloatingKruxService.isRunning()
                    ? "Masquer le visage de Krux" : "Visage de Krux sur l'écran");
        }
        view.findViewById(R.id.optFloat).setOnClickListener(v -> {
            dialog.dismiss();
            boolean ok = com.virtixstudio.kruxai.live.FloatingKruxService.toggle(MainActivity.this);
            if (!ok) Toast.makeText(MainActivity.this,
                    "Autorise « Afficher par-dessus les autres apps » pour KRUX, puis reviens et réessaie.",
                    Toast.LENGTH_LONG).show();
        });

        dialog.setOnShowListener(d -> dialog.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED));
        dialog.show();
        KruxTheme.skin(dialog, this);
    }

    private void initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

            speechRecognizer.setRecognitionListener(new RecognitionListener() {

                @Override
                public void onReadyForSpeech(Bundle params) {
                    com.virtixstudio.kruxai.live.FloatingKruxService.setState(com.virtixstudio.kruxai.live.LiveMotion.State.LISTENING);
                    resetVoiceBars();
                }

                @Override
                public void onBeginningOfSpeech() {
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                    if (!isListening) return;
                    com.virtixstudio.kruxai.live.FloatingKruxService.level((rmsdB + 2f) / 12f);
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
        com.virtixstudio.kruxai.live.FloatingKruxService.level(0f);
        com.virtixstudio.kruxai.live.FloatingKruxService.setState(com.virtixstudio.kruxai.live.LiveMotion.State.IDLE);

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
        KruxTheme.applyTo(view, this);

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

        KruxTheme.skin(dialog, this);
    }

    private void showUserMessageActions(View anchor, ChatMessage message) {
        if (anchor == null || message == null) {
            return;
        }

        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6));
        menu.setBackgroundColor(KruxTheme.get(this, "input"));

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
        item.setTextColor(KruxTheme.get(this, "text"));
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

            KruxTheme.skin(new AlertDialog.Builder(this)
                    .setTitle(matches + " résultat(s)")
                    .setMessage(result.toString())
                    .setPositiveButton("Fermer", null)
                    .show(), this);
            dialog.dismiss();
        }));

        dialog.show();

        KruxTheme.skin(dialog, this);
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

        // KruxTheme est la source unique (palette + couleurs modifiées dans "Couleurs et thème").
        // Les anciens réglages de "Personnaliser l'app" ne servent que si KruxTheme n'a rien de plus précis.
        int background = KruxTheme.get(this, "bg");
        if (KruxTheme.hasOverride(this, "bubble_user") || !preferences.contains("userBubble")) {
            userBubble = KruxTheme.get(this, "bubble_user");
        }
        if (KruxTheme.hasOverride(this, "bubble_ai") || !preferences.contains("aiBubble")) {
            aiBubble = KruxTheme.get(this, "bubble_ai");
        }
        if (KruxTheme.hasOverride(this, "accent") || !preferences.contains("accent")) {
            accent = KruxTheme.get(this, "accent");
        }
        int mainText = KruxTheme.get(this, "text");
        int userText = readableOn(userBubble, mainText);
        int aiText = readableOn(Color.alpha(aiBubble) >= 128 ? aiBubble : background, mainText);

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
            welcomeScene.setPalette(background, accent);
            welcomeScene.setScene(scene);
        }
        if (welcomeGlobe != null) {
            welcomeGlobe.setPalette(background, accent);
            welcomeGlobe.setMinimal("minimal".equals(scene));
        }
        if (btnSend != null) {
            btnSend.setImageTintList(ColorStateList.valueOf(KruxTheme.get(this, "buttontext")));
        }
        if (tvKruxStatus != null) {
            tvKruxStatus.setTextColor(accent);
        }
        if (terminal != null) {
            terminal.applyColors(background, accent, KruxTheme.get(this, "text"));
        }
        if (chatAdapter != null) {
            chatAdapter.setAccentColors(accent, KruxTheme.get(this, "text2"));
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

    /** Garde la couleur voulue si elle se lit bien sur ce fond, sinon noir ou blanc. */
    private int readableOn(int surface, int preferred) {
        int opaque = androidx.core.graphics.ColorUtils.setAlphaComponent(surface, 255);
        if (androidx.core.graphics.ColorUtils.calculateContrast(preferred, opaque) >= 4.5) return preferred;
        return androidx.core.graphics.ColorUtils.calculateLuminance(opaque) > 0.5 ? Color.BLACK : Color.WHITE;
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
        root.setBackgroundColor(KruxTheme.get(this, "bg"));

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
        content.setBackgroundColor(KruxTheme.get(this, "bg"));

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
        title.setTextColor(KruxTheme.get(this, "text"));
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
        cardBg.setColor(KruxTheme.get(this, "input"));
        cardBg.setCornerRadius(dpToPx(16));
        cardBg.setStroke(dpToPx(1), KruxTheme.get(this, "text2") & 0x55FFFFFF | 0x33000000);
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
        btnPickWallpaper.setTextColor(KruxTheme.get(this, "buttontext"));
        btnPickWallpaper.setBackgroundColor(KruxTheme.get(this, "button"));
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
        KruxTheme.skin(dialog, this);
    }

    /** Pièce jointe fichier : la bande montre aussi les photos choisies. */
    private void showFileAttachment(String name, int chars) {
        refreshAttachmentStrip();
    }

    private void ensureAttachmentStrip() {
        if (attachmentStrip != null) return;
        android.view.ViewGroup composer = findViewById(R.id.composerBar);
        if (composer == null) return;
        attachmentStrip = new AttachmentStrip(this);
        attachmentStrip.setVisibility(android.view.View.GONE);
        attachmentStrip.setListener(new AttachmentStrip.Listener() {
            @Override public void onRemove(AttachmentStrip.Item item) {
                if (item.tag instanceof Integer) {
                    int i = (Integer) item.tag;
                    if (i >= 0 && i < selectedImagesB64.size()) {
                        selectedImagesB64.remove(i);
                        if (i < selectedThumbs.size()) selectedThumbs.remove(i);
                    }
                } else {
                    selectedFileName = "";
                    selectedFileContent = "";
                }
                refreshAttachmentStrip();
                refreshSendIcon();
            }

            @Override public void onEdit(AttachmentStrip.Item item) {
                if (item.tag instanceof Integer) showEditImageDialog((Integer) item.tag);
            }
        });
        composer.addView(attachmentStrip, 0, new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** Remet la bande au-dessus du champ de saisie à jour : photos (avec « Modifier »), puis fichier. */
    private void refreshAttachmentStrip() {
        ensureAttachmentStrip();
        if (attachmentStrip == null) return;
        java.util.List<AttachmentStrip.Item> items = new java.util.ArrayList<>();
        for (int i = 0; i < selectedImagesB64.size(); i++) {
            AttachmentStrip.Item it = new AttachmentStrip.Item("Photo " + (i + 1), "",
                    i < selectedThumbs.size() ? selectedThumbs.get(i) : null, Integer.valueOf(i));
            it.editable = true;
            items.add(it);
        }
        if (selectedFileName != null && !selectedFileName.isEmpty()) {
            int chars = selectedFileContent == null ? 0 : selectedFileContent.length();
            items.add(new AttachmentStrip.Item(selectedFileName, AttachmentStrip.detailFor(selectedFileName, chars), null, selectedFileName));
        }
        attachmentStrip.setItems(items);
    }

    private void hideAttachmentStrip() {
        // La bande reflète l'état : elle disparaît seulement s'il ne reste ni photo ni fichier.
        if (attachmentStrip != null) refreshAttachmentStrip();
    }

    private static android.graphics.Bitmap thumbOf(android.graphics.Bitmap src) {
        float k = Math.min(1f, 192f / Math.max(src.getWidth(), src.getHeight()));
        return android.graphics.Bitmap.createScaledBitmap(src,
                Math.max(1, Math.round(src.getWidth() * k)), Math.max(1, Math.round(src.getHeight() * k)), true);
    }

    /** « Modifier » une photo : la remplacer, la pivoter ou la rogner au centre (carré). */
    private void showEditImageDialog(final int idx) {
        if (idx < 0 || idx >= selectedImagesB64.size()) return;
        final String[] options = {
                "Remplacer par une photo de la galerie",
                "Remplacer par une nouvelle photo",
                "Pivoter à droite",
                "Rogner au centre (carré)"
        };
        android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(this)
                .setTitle("Modifier la photo")
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        replaceImageIndex = idx;
                        imageReplacePicker.launch("image/*");
                    } else if (which == 1) {
                        replaceImageIndex = idx;
                        launchCamera(cameraReplaceLauncher);
                    } else {
                        transformImage(idx, which == 2);
                    }
                })
                .setNegativeButton("Annuler", null)
                .create();
        dlg.show();
        KruxTheme.skin(dlg, this);
    }

    private void transformImage(int idx, boolean rotate) {
        try {
            byte[] raw = android.util.Base64.decode(selectedImagesB64.get(idx), android.util.Base64.NO_WRAP);
            android.graphics.Bitmap src = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.length);
            if (src == null) throw new Exception("image illisible");
            android.graphics.Bitmap res;
            if (rotate) {
                android.graphics.Matrix m = new android.graphics.Matrix();
                m.postRotate(90);
                res = android.graphics.Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
            } else {
                int side = Math.min(src.getWidth(), src.getHeight());
                res = android.graphics.Bitmap.createBitmap(src, (src.getWidth() - side) / 2, (src.getHeight() - side) / 2, side, side);
            }
            int q = 80;
            byte[] jpg;
            do {
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                res.compress(android.graphics.Bitmap.CompressFormat.JPEG, q, bos);
                jpg = bos.toByteArray();
                q -= 10;
            } while (jpg.length > 600 * 1024 && q >= 40);
            selectedImagesB64.set(idx, android.util.Base64.encodeToString(jpg, android.util.Base64.NO_WRAP));
            selectedThumbs.set(idx, thumbOf(res));
            refreshAttachmentStrip();
        } catch (Throwable t) {
            android.util.Log.e("KRUX_IMAGE", "Modification de photo impossible", t);
            Toast.makeText(this, "Impossible de modifier cette photo", Toast.LENGTH_SHORT).show();
        }
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
        View navMyApps = findViewById(R.id.navMyApps), navFiles = findViewById(R.id.navFiles);
        if (navMyApps != null) {
            navMyApps.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                startActivity(new Intent(MainActivity.this, MyAppsActivity.class));
            });
        }
        if (navFiles != null) {
            navFiles.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                startActivity(new Intent(MainActivity.this, FilesActivity.class));
            });
        }
        View navKruxCode = findViewById(R.id.navKruxCode);
        if (navKruxCode != null) {
            navKruxCode.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                startActivity(new Intent(MainActivity.this, KruxCodeActivity.class));
            });
        }
        if (navCustomize != null) {
            navCustomize.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                settingsLauncher.launch(new Intent(MainActivity.this, SettingsActivity.class));
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

    /** Le visage flottant montre ce que fait KRUX : il réfléchit pendant la recherche et la génération, s'étonne en cas d'erreur. */
    private void syncFloatingFace(KruxState st) {
        try {
            if (!com.virtixstudio.kruxai.live.FloatingKruxService.isRunning()) return;
            com.virtixstudio.kruxai.live.LiveMotion.State target;
            switch (st) {
                case THINKING: case SEARCHING: case GENERATING: case MEMORY:
                    target = com.virtixstudio.kruxai.live.LiveMotion.State.THINKING; break;
                case LISTENING:
                    target = com.virtixstudio.kruxai.live.LiveMotion.State.LISTENING; break;
                case ERROR:
                    com.virtixstudio.kruxai.live.FloatingKruxService.flash(com.virtixstudio.kruxai.live.LiveMotion.Mood.SURPRISED, 1.2f);
                    target = com.virtixstudio.kruxai.live.LiveMotion.State.IDLE; break;
                default:
                    target = isTtsSpeaking ? com.virtixstudio.kruxai.live.LiveMotion.State.SPEAKING
                                           : com.virtixstudio.kruxai.live.LiveMotion.State.IDLE;
            }
            com.virtixstudio.kruxai.live.FloatingKruxService.setState(target);
        } catch (Exception ignore) { }
    }

    private void setKruxState(KruxState state) {
        if (state == null) {
            state = KruxState.IDLE;
        }

        kruxState = state;
        syncFloatingFace(state);

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

    @Override
    public void onConfigurationChanged(@androidx.annotation.NonNull android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (terminal != null) terminal.applyOrientation();
    }

    private void syncCloud(boolean force) {
        com.virtixstudio.kruxai.core.CloudSync.run(this, currentUser, dbHelper, force,
                this::loadHistorySidebar, new android.os.Handler(android.os.Looper.getMainLooper()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshQuotaView();
        syncCloud(false);
        restoreNetQueue();
        if (net != null && net.isOnline() && !netQueue.isEmpty()) netHandler.postDelayed(this::drainNet, 600);
    }

    @Override
    protected void onStop() {
        super.onStop();
        syncCloud(true);
    }

    private int sendOrLiveIcon() {
        boolean empty = etInput == null || etInput.getText().toString().trim().isEmpty();
        return empty && selectedImagesB64.isEmpty() ? R.drawable.ic_live : R.drawable.ic_send;
    }

    private void refreshSendIcon() {
        if (btnSend == null || isGenerating) return;
        btnSend.setImageResource(sendOrLiveIcon());
        btnSend.setContentDescription(
                sendOrLiveIcon() == R.drawable.ic_live ? "Mode Live" : "Envoyer");
    }

    private void launchLive() {
        Intent live = new Intent(MainActivity.this, com.virtixstudio.kruxai.live.LiveActivity.class);
        if (selectedKruxModel != null) live.putExtra(com.virtixstudio.kruxai.live.LiveActivity.EXTRA_MODEL, selectedKruxModel.name());
        startActivity(live);
    }

    /** Mode « création en cours » : options du bas masquées, bouton carré pour arrêter, champ verrouillé. */
    private void setBuildMode(boolean on) {
        int vis = on ? View.GONE : View.VISIBLE;
        if (btnPlus != null) btnPlus.setVisibility(vis);
        if (btnMic != null) btnMic.setVisibility(vis);
        if (etInput != null) {
            etInput.setEnabled(!on);
            etInput.setHint(on ? "Krux crée ton application…" : "Message Krux...");
            if (on) etInput.setText("");
        }
        setGeneratingState(on);
    }

    private void setGeneratingState(boolean generating) {
        isGenerating = generating;
        if (!generating) { syncCloud(false); if (!netQueue.isEmpty()) netHandler.postDelayed(this::drainNet, 900); }

        if (btnSend != null) {
            btnSend.setImageResource(
                generating ? R.drawable.ic_stop : sendOrLiveIcon()
            );
            btnSend.setBackgroundResource(
                    generating
                            ? R.drawable.bg_send_button_active
                            : R.drawable.bg_send_button_round
            );
            // Les ressources sont violettes (#9B5CFF) : sans ça le bouton change de couleur à chaque envoi.
            if (btnSend.getBackground() instanceof android.graphics.drawable.GradientDrawable) {
                android.graphics.drawable.GradientDrawable bgSend =
                        (android.graphics.drawable.GradientDrawable) btnSend.getBackground().mutate();
                bgSend.setColor(KruxTheme.get(this, "button"));
                bgSend.setCornerRadius(KruxTheme.radiusDp(this) * getResources().getDisplayMetrics().density);
            }
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
        KruxTheme.skin(builder.show(), this);
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
        KruxTheme.applyTo(view, this);

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

        KruxTheme.skin(dialog, this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        netHandler.removeCallbacksAndMessages(null);
        if (net != null) net.stop();
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }

    private void launchCamera() { launchCamera(cameraLauncher); }

    private void launchCamera(ActivityResultLauncher<android.net.Uri> launcher) {
        try {
            java.io.File dir = new java.io.File(getCacheDir(), "shared");
            if (!dir.exists()) dir.mkdirs();
            java.io.File f = new java.io.File(dir, "photo_" + System.currentTimeMillis() + ".jpg");
            cameraUri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            launcher.launch(cameraUri);
        } catch (Exception e) {
            Toast.makeText(this, "Impossible d'ouvrir l'appareil photo", Toast.LENGTH_SHORT).show();
        }
    }

    /** Réduit la photo (1280 px au plus, JPEG, moins de 600 Ko), corrige l'orientation, la garde pour le prochain envoi. */
    private void handleSelectedImage(final Uri uri) { handleSelectedImage(uri, -1); }

    /** replaceIdx >= 0 : remplace la photo à cet emplacement ; sinon ajoute (2 photos au maximum). */
    private void handleSelectedImage(final Uri uri, final int replaceIdx) {
        if (replaceIdx < 0 && selectedImagesB64.size() >= MAX_IMAGES) {
            Toast.makeText(this, MAX_IMAGES + " photos au maximum par message", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            try {
                android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                    android.graphics.BitmapFactory.decodeStream(in, null, o);
                }
                int longest = Math.max(o.outWidth, o.outHeight);
                if (longest <= 0) throw new Exception("image illisible");
                int sample = 1;
                while (longest / (sample * 2) >= 1280) sample *= 2;
                o.inJustDecodeBounds = false;
                o.inSampleSize = sample;
                android.graphics.Bitmap bmp;
                try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                    bmp = android.graphics.BitmapFactory.decodeStream(in, null, o);
                }
                if (bmp == null) throw new Exception("image illisible");

                int deg = 0;
                try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                    int ori = new android.media.ExifInterface(in).getAttributeInt(
                            android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL);
                    deg = ori == android.media.ExifInterface.ORIENTATION_ROTATE_90 ? 90
                            : ori == android.media.ExifInterface.ORIENTATION_ROTATE_180 ? 180
                            : ori == android.media.ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
                } catch (Exception ignored) { }

                float scale = Math.min(1f, 1280f / Math.max(bmp.getWidth(), bmp.getHeight()));
                android.graphics.Matrix m = new android.graphics.Matrix();
                m.postScale(scale, scale);
                if (deg != 0) m.postRotate(deg);
                android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);

                int q = 80;
                byte[] jpg;
                do {
                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                    out.compress(android.graphics.Bitmap.CompressFormat.JPEG, q, bos);
                    jpg = bos.toByteArray();
                    q -= 10;
                } while (jpg.length > 600 * 1024 && q >= 40);
                if (jpg.length > 700 * 1024) throw new Exception("image trop lourde");

                final String b64 = android.util.Base64.encodeToString(jpg, android.util.Base64.NO_WRAP);
                final android.graphics.Bitmap thumb = thumbOf(out);
                runOnUiThread(() -> {
                    if (replaceIdx >= 0 && replaceIdx < selectedImagesB64.size()) {
                        selectedImagesB64.set(replaceIdx, b64);
                        selectedThumbs.set(replaceIdx, thumb);
                    } else if (selectedImagesB64.size() < MAX_IMAGES) {
                        selectedImagesB64.add(b64);
                        selectedThumbs.add(thumb);
                    }
                    refreshAttachmentStrip();
                    refreshSendIcon();
                });
            } catch (Throwable t) {
                android.util.Log.e("KRUX_IMAGE", "Photo illisible", t);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Impossible de lire cette photo", Toast.LENGTH_SHORT).show());
            }
        }, "krux-image").start();
    }

    /** Lit un fichier texte de assets/ ; chaîne vide si absent (Krux garde alors ses règles intégrées). */
    private String readAssetText(String name) {
        try (java.io.InputStream in = getAssets().open(name)) {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
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
                hideAttachmentStrip();

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
            showFileAttachment(fileName, selectedFileContent.length());

            Toast.makeText(
                    MainActivity.this,
                    "Fichier prêt : " + fileName,
                    Toast.LENGTH_SHORT
            ).show();

        } catch (Exception e) {
            selectedFileName = "";
            selectedFileContent = "";
            hideAttachmentStrip();

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
