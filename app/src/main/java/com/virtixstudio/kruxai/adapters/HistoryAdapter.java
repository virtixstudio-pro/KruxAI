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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.virtixstudio.kruxai.models.ChatSession;
import com.virtixstudio.kruxai.ui.UiIcons;

import java.util.ArrayList;
import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    public interface OnSessionActionListener {
        void onSessionClick(ChatSession session);
        void onSessionPinToggle(ChatSession session);
        void onSessionRename(ChatSession session);
        void onSessionDelete(ChatSession session);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_SESSION = 1;

    /** Une ligne de la liste : soit un titre de groupe, soit une discussion. */
    private static final class Row {
        final String header;
        final ChatSession session;
        Row(String header, ChatSession session) { this.header = header; this.session = session; }
    }

    private List<ChatSession> sessionList;
    private List<Row> rows = new ArrayList<>();
    private final OnSessionActionListener listener;
    private final KruxDatabaseHelper dbHelper;

    public HistoryAdapter(Context context, List<ChatSession> sessionList, OnSessionActionListener listener) {
        this.sessionList = new ArrayList<>(sessionList);
        this.listener = listener;

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user != null) {
            this.dbHelper = new KruxDatabaseHelper(
                    context,
                    user.getUid()
            );
        } else {
            this.dbHelper = new KruxDatabaseHelper(context);
        }
        rebuildRows();
    }

    public void updateList(List<ChatSession> newList) {
        this.sessionList = new ArrayList<>(newList);
        rebuildRows();
        notifyDataSetChanged();
    }

    /** Insère un titre (Épinglées, Aujourd'hui, Hier…) chaque fois que le groupe change. */
    private void rebuildRows() {
        List<Row> out = new ArrayList<>();
        String last = null;
        for (ChatSession s : sessionList) {
            String g = dbHelper.isSessionPinned(s.getId()) ? "Épinglées" : groupOf(s);
            if (!g.equals(last)) {
                out.add(new Row(g, null));
                last = g;
            }
            out.add(new Row(null, s));
        }
        rows = out;
    }

    private static String groupOf(ChatSession s) {
        long t = lastActivity(s);
        if (t <= 0) return "Plus ancien";
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        long today = c.getTimeInMillis();
        long day = 24L * 60 * 60 * 1000;
        if (t >= today) return "Aujourd'hui";
        if (t >= today - day) return "Hier";
        if (t >= today - 6 * day) return "7 derniers jours";
        return "Plus ancien";
    }

    /** Date du dernier message ; si elle est invalide (anciennes lignes), date inscrite dans l'identifiant. */
    private static long lastActivity(ChatSession s) {
        final long MIN_VALID = 1_000_000_000_000L; // en millisecondes, après 2001
        List<ChatMessage> m = s.getMessages();
        if (m != null && !m.isEmpty()) {
            long t = m.get(m.size() - 1).getTimestamp();
            if (t >= MIN_VALID) return t;
        }
        String id = s.getId();
        if (id != null && id.startsWith("session_")) {
            try {
                long t = Long.parseLong(id.substring(8));
                if (t >= MIN_VALID) return t;
            } catch (NumberFormatException ignored) { }
        }
        return 0;
    }

    /** Retire les emoji et symboles placés au début d'un ancien titre (« 📷 Photo jointe… »). */
    private static String cleanTitle(String t) {
        if (t == null) return "";
        String c = t.replaceFirst("^[\\p{So}\\p{Sk}\\uFE0F\\u200D\\s]+", "");
        return c.isEmpty() ? t : c;
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).header != null ? TYPE_HEADER : TYPE_SESSION;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(
                viewType == TYPE_HEADER ? R.layout.item_history_header : R.layout.item_history, parent, false);
        com.virtixstudio.kruxai.ui.KruxTheme.applyTo(view, parent.getContext());
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (row.header != null) {
            holder.tvTitle.setText(row.header);
            return;
        }
        ChatSession session = row.session;

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

        title = cleanTitle(title);

        if (title.length() > 30) {
            title = title.substring(0, 27) + "...";
        }

        if (isPinned) {
            holder.tvTitle.setText(title);
            UiIcons.left(holder.tvTitle, R.drawable.ic_line_pin, holder.tvTitle.getCurrentTextColor(), 14, 6);
        } else {
            holder.tvTitle.setText(title);
            holder.tvTitle.setCompoundDrawablesRelative(null, null, null, null);
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
        return rows != null ? rows.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvHistoryTitle);
        }
    }
}
