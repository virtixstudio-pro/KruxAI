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
        btnPlus.setOnClickListener(v -> Toast.makeText(this, "Menu d'actions rapides / Fichiers", Toast.LENGTH_SHORT).show());
        btnSend.setOnClickListener(v -> sendMessage());
        btnMic.setOnClickListener(v -> toggleVoiceRecognition());

        setupDrawerNavigation();
        initSpeechRecognizer();
    }

    private void initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    Toast.makeText(MainActivity.this, "Écoute en cours...", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onBeginningOfSpeech() {}

                @Override
                public void onRmsChanged(float rmsdB) {}

                @Override
                public void onBufferReceived(byte[] buffer) {}

                @Override
                public void onEndOfSpeech() {
                    isListening = false;
                }

                @Override
                public void onError(int error) {
                    isListening = false;
                    Toast.makeText(MainActivity.this, "Erreur d'écoute vocale.", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onResults(Bundle results) {
                    isListening = false;
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        etInput.setText(matches.get(0));
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {}

                @Override
                public void onEvent(int eventType, Bundle params) {}
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
            Toast.makeText(this, "Module de personnalisation des couleurs à venir.", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        optMemory.setOnClickListener(v -> {
            Toast.makeText(this, "Mémoire active : Profil utilisateur retenu par KruxAI.", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        optSocials.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Virtixstudio-pro/KruxAI"));
            startActivity(intent);
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

        groqClient.sendMessage("llama-3.3-70b-versatile", "Tu es KruxAI, un assistant intelligent, fluide et direct.", prompt, new GroqApiClient.GroqCallback() {
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
