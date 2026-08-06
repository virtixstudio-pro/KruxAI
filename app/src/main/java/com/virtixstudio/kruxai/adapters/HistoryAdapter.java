package com.virtixstudio.kruxai.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.virtixstudio.kruxai.R;
import com.virtixstudio.kruxai.history.ChatHistoryManager;
import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    public interface OnChatClickListener {
        void onChatClick(ChatHistoryManager.ChatItem item);
        void onDeleteClick(ChatHistoryManager.ChatItem item);
    }

    private final Context context;
    private final List<ChatHistoryManager.ChatItem> chatList;
    private final OnChatClickListener listener;

    public HistoryAdapter(Context context, List<ChatHistoryManager.ChatItem> chatList, OnChatClickListener listener) {
        this.context = context;
        this.chatList = chatList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_history_chat, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatHistoryManager.ChatItem item = chatList.get(position);
        holder.tvTitle.setText(item.title);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onChatClick(item);
        });

        if (holder.btnDelete != null) {
            holder.btnDelete.setOnClickListener(v -> {
                if (listener != null) listener.onDeleteClick(item);
            });
        }
    }

    @Override
    public int getItemCount() {
        return chatList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        ImageButton btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvChatTitle);
            btnDelete = itemView.findViewById(R.id.btnDeleteChat);
        }
    }
}
