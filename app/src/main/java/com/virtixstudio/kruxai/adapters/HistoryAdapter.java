package com.virtixstudio.kruxai.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.virtixstudio.kruxai.R;
import com.virtixstudio.kruxai.models.ChatMessage;
import com.virtixstudio.kruxai.models.ChatSession;

import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    public interface OnSessionClickListener {
        void onSessionClick(ChatSession session);
    }

    private final List<ChatSession> sessionList;
    private final OnSessionClickListener listener;

    public HistoryAdapter(List<ChatSession> sessionList, OnSessionClickListener listener) {
        this.sessionList = sessionList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatSession session = sessionList.get(position);
        
        String title = "Nouvelle discussion";
        if (session.getMessages() != null && !session.getMessages().isEmpty()) {
            for (ChatMessage msg : session.getMessages()) {
                if (msg.isUser()) {
                    title = msg.getText();
                    break;
                }
            }
        }
        
        if (title.length() > 30) {
            title = title.substring(0, 27) + "...";
        }

        holder.tvTitle.setText(title);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSessionClick(session);
            }
        });
    }

    @Override
    public int getItemCount() {
        return sessionList != null ? sessionList.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvHistoryTitle);
        }
    }
}
