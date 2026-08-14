package com.virtixstudio.kruxai.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.appcompat.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.virtixstudio.kruxai.R;
import com.virtixstudio.kruxai.database.KruxDatabaseHelper;
import com.virtixstudio.kruxai.models.ChatMessage;
import com.virtixstudio.kruxai.models.ChatSession;

import java.util.ArrayList;
import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    public interface OnSessionActionListener {
        void onSessionClick(ChatSession session);
        void onSessionPinToggle(ChatSession session);
        void onSessionRename(ChatSession session);
        void onSessionDelete(ChatSession session);
    }

    private List<ChatSession> sessionList;
    private final OnSessionActionListener listener;
    private final KruxDatabaseHelper dbHelper;

    public HistoryAdapter(Context context, List<ChatSession> sessionList, OnSessionActionListener listener) {
        this.sessionList = new ArrayList<>(sessionList);
        this.listener = listener;
        this.dbHelper = new KruxDatabaseHelper(context);
    }

    public void updateList(List<ChatSession> newList) {
        this.sessionList = new ArrayList<>(newList);
        notifyDataSetChanged();
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

        String customTitle = dbHelper.getSessionTitle(session.getId());
        boolean isPinned = dbHelper.isSessionPinned(session.getId());

        String title = customTitle;
        if (title == null || title.isEmpty()) {
            title = "Nouvelle discussion";
            if (session.getMessages() != null && !session.getMessages().isEmpty()) {
                for (ChatMessage msg : session.getMessages()) {
                    if (msg.isUser()) {
                        title = msg.getText();
                        break;
                    }
                }
            }
        }

        if (title.length() > 30) {
            title = title.substring(0, 27) + "...";
        }

        if (isPinned) {
            holder.tvTitle.setText("📌 " + title);
        } else {
            holder.tvTitle.setText(title);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSessionClick(session);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            PopupMenu popup = new PopupMenu(v.getContext(), v);
            popup.getMenu().add(0, 1, 1, isPinned ? "Désépingler" : "Épingler");
            popup.getMenu().add(0, 2, 2, "Renommer");
            popup.getMenu().add(0, 3, 3, "Supprimer");

            popup.setOnMenuItemClickListener(item -> {
                if (listener == null) return false;
                switch (item.getItemId()) {
                    case 1:
                        listener.onSessionPinToggle(session);
                        return true;
                    case 2:
                        listener.onSessionRename(session);
                        return true;
                    case 3:
                        listener.onSessionDelete(session);
                        return true;
                    default:
                        return false;
                }
            });
            popup.show();
            return true;
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
