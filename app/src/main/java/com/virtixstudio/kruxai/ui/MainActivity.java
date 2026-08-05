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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.virtixstudio.kruxai.R;
import com.virtixstudio.kruxai.adapter.ChatAdapter;
import com.virtixstudio.kruxai.api.ApiClient;
import com.virtixstudio.kruxai.history.ChatHistoryManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements ChatAdapter.OnMessageActionListener {

    private DrawerLayout drawerLayout;
    private LinearLayout welcomeView;
    private RecyclerView recyclerChat;
    private EditText etMessage;
    private TextView tvCurrentModel;
    private ImageButton btnScrollBottom;

    private ChatAdapter chatAdapter;
    private List<ChatAdapter.Message> messageList;
    private String currentSessionId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        drawerLayout = findViewById(R.id.drawer_layout);
        welcomeView = findViewById(R.id.welcome_view);
        recyclerChat = findViewById(R.id.recycler_chat);
        etMessage = findViewById(R.id.et_message);
        tvCurrentModel = findViewById(R.id.tv_current_model);
        btnScrollBottom = findViewById(R.id.btn_scroll_bottom);

        ImageButton btnOpenDrawer = findViewById(R.id.btn_open_drawer);
        ImageButton btnNewChat = findViewById(R.id.btn_new_chat);
        ImageButton btnSend = findViewById(R.id.btn_send);

        messageList = new ArrayList<>();
        chatAdapter = new ChatAdapter(messageList, this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerChat.setLayoutManager(layoutManager);
        recyclerChat.setAdapter(chatAdapter);

        currentSessionId = UUID.randomUUID().toString();

        btnOpenDrawer.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        btnNewChat.setOnClickListener(v -> startNewChat());
        btnSend.setOnClickListener(v -> sendMessage());

        // Gestion de la flèche de défilement automatique vers le bas
        if (btnScrollBottom != null) {
            btnScrollBottom.setOnClickListener(v -> scrollToBottom());
        }

        recyclerChat.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (btnScrollBottom == null) return;
                
                int lastVisible = layoutManager.findLastCompletelyVisibleItemPosition();
                int totalItems = chatAdapter.getItemCount();

                // Affiche le bouton si l'utilisateur est remonté dans la liste
                if (totalItems > 0 && lastVisible < totalItems - 2) {
                    btnScrollBottom.setVisibility(View.VISIBLE);
                } else {
                    btnScrollBottom.setVisibility(View.GONE);
                }
            }
        });
    }

    private void scrollToBottom() {
        if (chatAdapter.getItemCount() > 0) {
            recyclerChat.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
        }
    }

    private void startNewChat() {
        currentSessionId = UUID.randomUUID().toString();
        messageList.clear();
        chatAdapter.notifyDataSetChanged();
        welcomeView.setVisibility(View.VISIBLE);
        recyclerChat.setVisibility(View.GONE);
        if (btnScrollBottom != null) btnScrollBottom.setVisibility(View.GONE);
        etMessage.setText("");
        Toast.makeText(this, "Nouvelle discussion créée", Toast.LENGTH_SHORT).show();
    }

    private void sendMessage() {
        String query = etMessage.getText().toString().trim();
        if (query.isEmpty()) return;

        welcomeView.setVisibility(View.GONE);
        recyclerChat.setVisibility(View.VISIBLE);
        etMessage.setText("");

        messageList.add(new ChatAdapter.Message(query, true));
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        scrollToBottom();

        ApiClient.sendRequest("Tu es KRUX AI, un assistant hautement qualifié.", query, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(String response, String modelBrand) {
                runOnUiThread(() -> {
                    tvCurrentModel.setText(modelBrand);
                    messageList.add(new ChatAdapter.Message(response, false));
                    chatAdapter.notifyItemInserted(messageList.size() - 1);
                    scrollToBottom();
                });
            }

            @Override
            public void onError(String friendlyMessage) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, friendlyMessage, Toast.LENGTH_LONG).show());
            }
        });
    }

    @Override
    public void onEditMessage(String text) {
        etMessage.setText(text);
        etMessage.requestFocus();
        etMessage.setSelection(text.length());
    }

    @Override
    public void onRetryMessage(String text) {
        etMessage.setText(text);
        sendMessage();
    }
}
