package com.virtixstudio.kruxai.adapters;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.virtixstudio.kruxai.R;
import com.virtixstudio.kruxai.models.ChatMessage;

import java.util.List;

import io.noties.markwon.Markwon;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_USER = 1;
    private static final int VIEW_TYPE_AI = 2;

    private final List<ChatMessage> messageList;
    private Markwon markwon;

    public ChatAdapter(List<ChatMessage> messageList) {
        this.messageList = messageList;
    }

    @Override
    public int getItemViewType(int position) {
        return messageList.get(position).isUser() ? VIEW_TYPE_USER : VIEW_TYPE_AI;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        if (markwon == null) {
            markwon = Markwon.create(context);
        }

        if (viewType == VIEW_TYPE_USER) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_chat_user, parent, false);
            return new UserViewHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_chat_ai, parent, false);
            return new AiViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messageList.get(position);

        if (holder instanceof UserViewHolder) {
            ((UserViewHolder) holder).tvMessage.setText(message.getText());
        } else if (holder instanceof AiViewHolder) {
            AiViewHolder aiHolder = (AiViewHolder) holder;
            Context context = aiHolder.itemView.getContext();

            // Rendu Markdown du message de l'IA
            markwon.setMarkdown(aiHolder.tvMessage, message.getText());

            // Gestion du bloc de réflexion rétractable
            if (message.getReasoning() != null && !message.getReasoning().trim().isEmpty()) {
                aiHolder.layoutReasoning.setVisibility(View.VISIBLE);
                aiHolder.tvReasoningContent.setText(message.getReasoning());

                aiHolder.btnToggleReasoning.setOnClickListener(v -> {
                    boolean isExpanded = aiHolder.tvReasoningContent.getVisibility() == View.VISIBLE;
                    aiHolder.tvReasoningContent.setVisibility(isExpanded ? View.GONE : View.VISIBLE);
                    aiHolder.ivArrowReasoning.setRotation(isExpanded ? 0 : 90);
                });
            } else {
                aiHolder.layoutReasoning.setVisibility(View.GONE);
            }

            // Boutons d'action (Copier & Partager)
            aiHolder.btnCopy.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("KruxAI Response", message.getText());
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(context, "Texte copié !", Toast.LENGTH_SHORT).show();
                }
            });

            aiHolder.btnShare.setOnClickListener(v -> {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, message.getText());
                context.startActivity(Intent.createChooser(shareIntent, "Partager via"));
            });
        }
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
        }
    }

    static class AiViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage, tvReasoningContent;
        LinearLayout layoutReasoning, btnToggleReasoning;
        ImageView ivArrowReasoning;
        ImageButton btnCopy, btnShare, btnRegenerate;

        AiViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvReasoningContent = itemView.findViewById(R.id.tvReasoningContent);
            layoutReasoning = itemView.findViewById(R.id.layoutReasoning);
            btnToggleReasoning = itemView.findViewById(R.id.btnToggleReasoning);
            ivArrowReasoning = itemView.findViewById(R.id.ivArrowReasoning);
            btnCopy = itemView.findViewById(R.id.btnCopy);
            btnShare = itemView.findViewById(R.id.btnShare);
            btnRegenerate = itemView.findViewById(R.id.btnRegenerate);
        }
    }
}
