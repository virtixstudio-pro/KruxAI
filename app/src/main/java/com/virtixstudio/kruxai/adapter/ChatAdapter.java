package com.virtixstudio.kruxai.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.virtixstudio.kruxai.R;
import com.virtixstudio.kruxai.media.MediaHelper;

import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.MessageViewHolder> {

    public static class Message {
        public String text;
        public boolean isUser;

        public Message(String text, boolean isUser) {
            this.text = text;
            this.isUser = isUser;
        }
    }

    public interface OnMessageActionListener {
        void onEditMessage(String text);
        void onRetryMessage(String text);
    }

    private final List<Message> messageList;
    private final OnMessageActionListener actionListener;

    public ChatAdapter(List<Message> messageList, OnMessageActionListener actionListener) {
        this.messageList = messageList;
        this.actionListener = actionListener;
    }

    @Override
    public MessageViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(
                viewType == 1 ? R.layout.item_message_user : R.layout.item_message_bot,
                parent,
                false
        );
        return new MessageViewHolder(view);
    }

    @Override
    public int getItemViewType(int position) {
        return messageList.get(position).isUser ? 1 : 0;
    }

    @Override
    public void onBindViewHolder(MessageViewHolder holder, int position) {
        Message msg = messageList.get(position);
        holder.tvContent.setText(msg.text);

        // Action sur Clic Long
        holder.itemView.setOnLongClickListener(v -> {
            showContextMenu(v.getContext(), msg);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    private void showContextMenu(Context context, Message msg) {
        String[] options;
        if (msg.isUser) {
            options = new String[]{"📋 Copier", "✏️ Modifier", "🔄 Réessayer"};
        } else {
            options = new String[]{"📋 Copier", "🔄 Régénérer la réponse"};
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Options du message");
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                MediaHelper.copyToClipboard(context, msg.text);
            } else if (which == 1 && msg.isUser) {
                if (actionListener != null) actionListener.onEditMessage(msg.text);
            } else {
                if (actionListener != null) actionListener.onRetryMessage(msg.text);
            }
        });
        builder.show();
    }

    public static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvContent;

        public MessageViewHolder(View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tv_message_text);
        }
    }
}
