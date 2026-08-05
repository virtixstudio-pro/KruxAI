package com.virtixstudio.kruxai.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.virtixstudio.kruxai.R;
import com.virtixstudio.kruxai.api.ApiClient;
import com.virtixstudio.kruxai.history.ChatHistoryManager;
import com.virtixstudio.kruxai.web.WebScraper;

import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private LinearLayout welcomeView;
    private RecyclerView recyclerChat;
    private EditText etMessage;
    private EditText etSearchHistory;
    private TextView tvCurrentModel;

    private String currentSessionId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        drawerLayout = findViewById(R.id.drawer_layout);
        welcomeView = findViewById(R.id.welcome_view);
        recyclerChat = findViewById(R.id.recycler_chat);
        etMessage = findViewById(R.id.et_message);
        etSearchHistory = findViewById(R.id.et_search_history);
        tvCurrentModel = findViewById(R.id.tv_current_model);

        ImageButton btnOpenDrawer = findViewById(R.id.btn_open_drawer);
        ImageButton btnNewChat = findViewById(R.id.btn_new_chat);
        ImageButton btnSend = findViewById(R.id.btn_send);
        Button chipCode = findViewById(R.id.chip_code);
        Button chipMedia = findViewById(R.id.chip_media);

        currentSessionId = UUID.randomUUID().toString();

        // Ouverture du menu latéral Hamburger
        btnOpenDrawer.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        // Nouvelle discussion (Réinitialisation vers l'écran d'accueil style Gemini)
        btnNewChat.setOnClickListener(v -> startNewChat());

        // Suggestions d'accueil rapide
        chipCode.setOnClickListener(v -> {
            etMessage.setText("Écris un script Python optimisé pour...");
            sendMessage();
        });

        chipMedia.setOnClickListener(v -> {
            etMessage.setText("Trouve-moi des vidéos YouTube sur ");
            etMessage.setSelection(etMessage.getText().length());
        });

        // Envoi d'un message
        btnSend.setOnClickListener(v -> sendMessage());

        // Recherche dynamique dans l'historique du Drawer
        etSearchHistory.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Filtrage dynamique de l'historique
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void startNewChat() {
        currentSessionId = UUID.randomUUID().toString();
        welcomeView.setVisibility(View.VISIBLE);
        recyclerChat.setVisibility(View.GONE);
        etMessage.setText("");
        Toast.makeText(this, "Nouvelle conversation démarrée", Toast.LENGTH_SHORT).show();
    }

    private void sendMessage() {
        String query = etMessage.getText().toString().trim();
        if (query.isEmpty()) return;

        // Bascule de l'interface : masquage de l'accueil, affichage du chat
        welcomeView.setVisibility(View.GONE);
        recyclerChat.setVisibility(View.VISIBLE);

        etMessage.setText("");

        // Sauvegarde dans l'historique local
        ChatHistoryManager.ChatItem item = new ChatHistoryManager.ChatItem(
                currentSessionId,
                query.length() > 30 ? query.substring(0, 30) + "..." : query,
                false,
                System.currentTimeMillis()
        );
        ChatHistoryManager.saveChat(this, item);

        // Si la requête demande une recherche de vidéo
        if (query.toLowerCase().contains("vidéo") || query.toLowerCase().contains("youtube")) {
            new Thread(() -> {
                String videoResults = WebScraper.searchVideos(query);
                runOnUiThread(() -> {
                    // Affichage des résultats vidéos
                });
            }).start();
        }

        // Appel vers le moteur Multi-IA Krux
        ApiClient.sendRequest("Tu es KRUX AI, un assistant hautement qualifié.", query, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(String response, String modelBrand) {
                runOnUiThread(() -> {
                    tvCurrentModel.setText(modelBrand);
                    // Mise à jour de la vue chat avec la réponse
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show());
            }
        });
    }
}
