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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.virtixstudio.kruxai.R;
import com.virtixstudio.kruxai.models.ChatMessage;
import com.virtixstudio.kruxai.utils.FileUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.noties.markwon.Markwon;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_USER = 1;
    private static final int VIEW_TYPE_AI = 2;

    private final List<ChatMessage> messageList;
    private final OnSpeechRequestedListener speechListener;
    private final OnFeedbackRequestedListener feedbackListener;

    private final Map<String, String> feedbackStates = new HashMap<>();

    private Markwon markwon;

    public interface OnSpeechRequestedListener {
        void onSpeakRequested(String text);
    }

    public interface OnFeedbackRequestedListener {
        void onFeedbackRequested(ChatMessage message, String type);
    }

    public ChatAdapter(
            List<ChatMessage> messageList,
            OnSpeechRequestedListener speechListener,
            OnFeedbackRequestedListener feedbackListener
    ) {
        this.messageList = messageList;
        this.speechListener = speechListener;
        this.feedbackListener = feedbackListener;
    }

    @Override
    public int getItemViewType(int position) {
        return messageList.get(position).isUser()
                ? VIEW_TYPE_USER
                : VIEW_TYPE_AI;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        Context context = parent.getContext();

        if (markwon == null) {
            markwon = Markwon.create(context);
        }

        if (viewType == VIEW_TYPE_USER) {
            View view = LayoutInflater.from(context)
                    .inflate(R.layout.item_chat_user, parent, false);

            return new UserViewHolder(view);
        }

        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_chat_ai, parent, false);

        return new AiViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull RecyclerView.ViewHolder holder,
            int position
    ) {
        ChatMessage message = messageList.get(position);

        if (holder instanceof UserViewHolder) {

            ((UserViewHolder) holder)
                    .tvMessage
                    .setText(message.getText());

            return;
        }

        AiViewHolder aiHolder = (AiViewHolder) holder;
        Context context = aiHolder.itemView.getContext();

        markwon.setMarkdown(
                aiHolder.tvMessage,
                message.getText() == null ? "" : message.getText()
        );

        setupSources(aiHolder, context, message);
        setupReasoning(aiHolder, message);
        setupActions(aiHolder, context, message);
        setupFeedback(aiHolder, message);
    }

    private void setupSources(
            AiViewHolder holder,
            Context context,
            ChatMessage message
    ) {
        if (message.getSources() != null
                && !message.getSources().isEmpty()) {

            holder.layoutSources.setVisibility(View.VISIBLE);

            holder.rvSources.setLayoutManager(
                    new LinearLayoutManager(
                            context,
                            LinearLayoutManager.HORIZONTAL,
                            false
                    )
            );

            holder.rvSources.setAdapter(
                    new SourcesAdapter(message.getSources())
            );

        } else {
            holder.layoutSources.setVisibility(View.GONE);
        }
    }

    private void setupReasoning(
            AiViewHolder holder,
            ChatMessage message
    ) {
        String reasoning = message.getReasoning();

        if (reasoning != null && !reasoning.trim().isEmpty()) {

            holder.layoutReasoning.setVisibility(View.VISIBLE);
            holder.tvReasoningContent.setText(reasoning);
            holder.tvReasoningContent.setVisibility(View.GONE);
            holder.ivArrowReasoning.setRotation(0);

            holder.btnToggleReasoning.setOnClickListener(v -> {

                boolean expanded =
                        holder.tvReasoningContent.getVisibility()
                                == View.VISIBLE;

                holder.tvReasoningContent.setVisibility(
                        expanded ? View.GONE : View.VISIBLE
                );

                holder.ivArrowReasoning.setRotation(
                        expanded ? 0 : 90
                );
            });

        } else {

            holder.layoutReasoning.setVisibility(View.GONE);
            holder.tvReasoningContent.setVisibility(View.GONE);
        }
    }

    private void setupActions(
            AiViewHolder holder,
            Context context,
            ChatMessage message
    ) {
        holder.btnCopy.setOnClickListener(v -> {

            ClipboardManager clipboard =
                    (ClipboardManager)
                            context.getSystemService(
                                    Context.CLIPBOARD_SERVICE
                            );

            ClipData clip =
                    ClipData.newPlainText(
                            "KruxAI",
                            message.getText()
                    );

            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);

                Toast.makeText(
                        context,
                        "Copié",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });

        holder.btnShare.setOnClickListener(v -> {

            Intent shareIntent =
                    new Intent(Intent.ACTION_SEND);

            shareIntent.setType("text/plain");

            shareIntent.putExtra(
                    Intent.EXTRA_TEXT,
                    message.getText()
            );

            context.startActivity(
                    Intent.createChooser(
                            shareIntent,
                            "Partager"
                    )
            );
        });

        holder.btnDownload.setOnClickListener(v ->
                FileUtils.saveTextFile(
                        context,
                        message.getText(),
                        "KruxAI_Export"
                )
        );

        holder.btnSpeak.setOnClickListener(v -> {

            if (speechListener != null) {
                speechListener.onSpeakRequested(
                        message.getText()
                );
            }
        });
    }

    private void setupFeedback(
            AiViewHolder holder,
            ChatMessage message
    ) {
        String messageId = message.getId();

        if (messageId == null || messageId.isEmpty()) {
            messageId = String.valueOf(message.getTimestamp());
        }

        String state = feedbackStates.get(messageId);

        resetFeedbackButtons(holder);

        if ("positive".equals(state)) {
            holder.btnFeedbackPositive.setAlpha(1.0f);
            holder.btnFeedbackPositive.setColorFilter(
                    0xFF22C55E
            );
            holder.btnFeedbackNegative.setAlpha(0.35f);

        } else if ("negative".equals(state)) {

            holder.btnFeedbackNegative.setAlpha(1.0f);
            holder.btnFeedbackNegative.setColorFilter(
                    0xFFEF4444
            );
            holder.btnFeedbackPositive.setAlpha(0.35f);
        }

        holder.btnFeedbackPositive.setOnClickListener(v -> {

            if ("positive".equals(feedbackStates.get(messageId))) {
                return;
            }

            feedbackStates.put(messageId, "positive");

            if (feedbackListener != null) {
                feedbackListener.onFeedbackRequested(
                        message,
                        "positive"
                );
            }

            notifyItemChanged(
                    holder.getAdapterPosition()
            );
        });

        holder.btnFeedbackNegative.setOnClickListener(v -> {

            if ("negative".equals(feedbackStates.get(messageId))) {
                return;
            }

            feedbackStates.put(messageId, "negative");

            if (feedbackListener != null) {
                feedbackListener.onFeedbackRequested(
                        message,
                        "negative"
                );
            }

            notifyItemChanged(
                    holder.getAdapterPosition()
            );
        });
    }

    private void resetFeedbackButtons(AiViewHolder holder) {

        holder.btnFeedbackPositive.clearColorFilter();
        holder.btnFeedbackNegative.clearColorFilter();

        holder.btnFeedbackPositive.setAlpha(0.75f);
        holder.btnFeedbackNegative.setAlpha(0.75f);
    }

    public void setFeedbackState(
            String messageId,
            String type
    ) {
        if (messageId == null) return;

        feedbackStates.put(messageId, type);

        for (int i = 0; i < messageList.size(); i++) {

            ChatMessage message = messageList.get(i);

            if (messageId.equals(message.getId())) {
                notifyItemChanged(i);
                break;
            }
        }
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    static class UserViewHolder
            extends RecyclerView.ViewHolder {

        TextView tvMessage;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);

            tvMessage =
                    itemView.findViewById(
                            R.id.tvMessage
                    );
        }
    }

    static class AiViewHolder
            extends RecyclerView.ViewHolder {

        TextView tvMessage;
        TextView tvReasoningContent;

        LinearLayout layoutReasoning;
        LinearLayout btnToggleReasoning;
        LinearLayout layoutSources;

        ImageView ivArrowReasoning;

        RecyclerView rvSources;

        ImageButton btnCopy;
        ImageButton btnShare;
        ImageButton btnDownload;
        ImageButton btnSpeak;

        ImageButton btnFeedbackPositive;
        ImageButton btnFeedbackNegative;

        AiViewHolder(@NonNull View itemView) {
            super(itemView);

            tvMessage =
                    itemView.findViewById(
                            R.id.tvMessage
                    );

            tvReasoningContent =
                    itemView.findViewById(
                            R.id.tvReasoningContent
                    );

            layoutReasoning =
                    itemView.findViewById(
                            R.id.layoutReasoning
                    );

            btnToggleReasoning =
                    itemView.findViewById(
                            R.id.btnToggleReasoning
                    );

            ivArrowReasoning =
                    itemView.findViewById(
                            R.id.ivArrowReasoning
                    );

            layoutSources =
                    itemView.findViewById(
                            R.id.layoutSources
                    );

            rvSources =
                    itemView.findViewById(
                            R.id.rvSources
                    );

            btnCopy =
                    itemView.findViewById(
                            R.id.btnCopy
                    );

            btnShare =
                    itemView.findViewById(
                            R.id.btnShare
                    );

            btnDownload =
                    itemView.findViewById(
                            R.id.btnDownload
                    );

            btnSpeak =
                    itemView.findViewById(
                            R.id.btnSpeak
                    );

            btnFeedbackPositive =
                    itemView.findViewById(
                            R.id.btnFeedbackPositive
                    );

            btnFeedbackNegative =
                    itemView.findViewById(
                            R.id.btnFeedbackNegative
                    );
        }
    }
}
