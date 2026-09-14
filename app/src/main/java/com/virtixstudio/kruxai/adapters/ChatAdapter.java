package com.virtixstudio.kruxai.adapters;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Space;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.virtixstudio.kruxai.R;
import com.virtixstudio.kruxai.models.ChatMessage;
import com.virtixstudio.kruxai.utils.FileUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.core.MarkwonTheme;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.HorizontalScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_USER = 1;
    private static final int VIEW_TYPE_AI = 2;

    private final List<ChatMessage> messageList;
    private final OnSpeechRequestedListener speechListener;
    private final OnFeedbackRequestedListener feedbackListener;
    private final OnUserActionListener userActionListener;

    private final Map<String, String> feedbackStates = new HashMap<>();

    private Markwon markwon;

    public interface OnSpeechRequestedListener {
        void onSpeakRequested(String text);
    }

    public interface OnFeedbackRequestedListener {
        void onFeedbackRequested(ChatMessage message, String type);
    }

    public interface OnUserActionListener {
        void onEditRequested(ChatMessage message);
        void onCopyRequested(ChatMessage message);
        void onRetryRequested(ChatMessage message);
    }

    public ChatAdapter(
            List<ChatMessage> messageList,
            OnSpeechRequestedListener speechListener,
            OnFeedbackRequestedListener feedbackListener,
            OnUserActionListener userActionListener
    ) {
        this.messageList = messageList;
        this.speechListener = speechListener;
        this.feedbackListener = feedbackListener;
        this.userActionListener = userActionListener;
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
            markwon = Markwon.builder(context)
                .usePlugin(TablePlugin.create(context))
                .usePlugin(new AbstractMarkwonPlugin() {
                    @Override
                    public void configureTheme(@NonNull MarkwonTheme.Builder builder) {
                        builder.codeBlockBackgroundColor(Color.parseColor("#1E1E2E"))
                               .codeBlockTextColor(Color.parseColor("#E0DEF4"))
                               .codeBackgroundColor(Color.parseColor("#2A2A3E"))
                               .codeTextColor(Color.parseColor("#E0DEF4"))
                               .blockQuoteColor(Color.parseColor("#9B59B6"))
                               .linkColor(Color.parseColor("#BB86FC"));
                    }
                })
                .build();
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

            UserViewHolder userHolder = (UserViewHolder) holder;

            userHolder.tvMessage.setText(
                    message.getText() == null ? "" : message.getText()
            );

            userHolder.btnEdit.setOnClickListener(v -> {
                if (userActionListener != null) {
                    userActionListener.onEditRequested(message);
                }
            });

            userHolder.btnCopy.setOnClickListener(v -> {
                if (userActionListener != null) {
                    userActionListener.onCopyRequested(message);
                }
            });

            userHolder.btnRetry.setOnClickListener(v -> {
                if (userActionListener != null) {
                    userActionListener.onRetryRequested(message);
                }
            });

            animateActionButton(userHolder.btnEdit);
            animateActionButton(userHolder.btnCopy);
            animateActionButton(userHolder.btnRetry);

            return;
        }

        AiViewHolder aiHolder = (AiViewHolder) holder;
        Context context = aiHolder.itemView.getContext();

        renderMessage(
                aiHolder,
                message.getText() == null ? "" : message.getText()
        );

        setupSources(aiHolder, context, message);
        setupReasoning(aiHolder, message);
        setupActions(aiHolder, context, message);
        setupFeedback(aiHolder, message);
    }

    private void animateActionButton(@NonNull View button) {
        button.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate()
                            .scaleX(0.88f)
                            .scaleY(0.88f)
                            .alpha(0.82f)
                            .setDuration(70)
                            .start();
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(1f)
                            .setDuration(230)
                            .setInterpolator(new OvershootInterpolator(2.4f))
                            .start();
                    break;
            }

            return false;
        });
    }

    private void renderMessage(
            AiViewHolder holder,
            String markdown
    ) {
        holder.messageContainer.removeAllViews();

        List<String> lines = splitLines(markdown);
        StringBuilder normal = new StringBuilder();
        int i = 0;

        while (i < lines.size()) {
            String line = lines.get(i);

            if (line.trim().startsWith("```")) {
                flushMarkdown(holder.messageContainer, normal.toString());
                normal.setLength(0);

                String info = line.trim().substring(3).trim();
                String language = extractLanguage(info);
                String fileName = extractFileName(info, "");

                List<String> codeLines = new ArrayList<>();
                i++;

                while (i < lines.size()
                        && !lines.get(i).trim().equals("```")) {
                    codeLines.add(lines.get(i));
                    i++;
                }

                String code = joinLines(codeLines);

                if (fileName.isEmpty()) {
                    fileName = extractFileNameFromCode(code);
                }

                addCodeBlock(
                        holder.messageContainer,
                        language,
                        fileName,
                        code
                );

                if (i < lines.size()) {
                    i++;
                }
                continue;
            }

            if (isTableStart(lines, i)) {
                flushMarkdown(holder.messageContainer, normal.toString());
                normal.setLength(0);

                int end = i;
                while (end < lines.size()
                        && isTableRow(lines.get(end))) {
                    end++;
                }

                addTable(
                        holder.messageContainer,
                        lines.subList(i, end)
                );

                i = end;
                continue;
            }

            normal.append(line);
            if (i < lines.size() - 1) {
                normal.append('\n');
            }
            i++;
        }

        flushMarkdown(holder.messageContainer, normal.toString());
    }

    private void flushMarkdown(
            LinearLayout container,
            String text
    ) {
        if (text == null || text.trim().isEmpty()) return;

        TextView tv = new TextView(container.getContext());
        tv.setLayoutParams(
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );
        tv.setTextColor(Color.parseColor("#FAF7FF"));
        tv.setTextSize(15);
        tv.setLineSpacing(0, 1.12f);
        tv.setPadding(4, 4, 4, 8);

        markwon.setMarkdown(tv, text);
        container.addView(tv);
    }

    private void addCodeBlock(
            LinearLayout container,
            String language,
            String fileName,
            String code
    ) {
        Context context = container.getContext();

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(0, 0, 0, 0);

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.parseColor("#171126"));
        background.setCornerRadius(22);
        background.setStroke(1, Color.parseColor("#43245E"));
        card.setBackground(background);

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(14, 10, 8, 10);

        TextView languageView = new TextView(context);
        languageView.setText(
                language.isEmpty()
                        ? "CODE"
                        : language.toUpperCase(Locale.ROOT)
        );
        languageView.setTextColor(Color.parseColor("#C084FC"));
        languageView.setTextSize(11);
        languageView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        header.addView(
                languageView,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        if (!fileName.isEmpty()) {
            TextView fileView = new TextView(context);
            fileView.setText(fileName);
            fileView.setTextColor(Color.parseColor("#8E829F"));
            fileView.setTextSize(11);
            fileView.setSingleLine(true);
            fileView.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);

            LinearLayout.LayoutParams fp =
                    new LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f
                    );
            fp.setMargins(10, 0, 8, 0);
            header.addView(fileView, fp);
        } else {
            Space spacer = new Space(context);
            header.addView(
                    spacer,
                    new LinearLayout.LayoutParams(
                            0,
                            1,
                            1f
                    )
            );
        }

        ImageButton share = createCodeAction(
                context,
                R.drawable.ic_share,
                "Partager le code"
        );
        ImageButton copy = createCodeAction(
                context,
                R.drawable.ic_copy,
                "Copier le code"
        );
        ImageButton download = createCodeAction(
                context,
                R.drawable.ic_download,
                "Télécharger le code"
        );

        header.addView(share);
        header.addView(copy);
        header.addView(download);

        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(false);

        TextView codeView = new TextView(context);
        codeView.setText(code);
        codeView.setTextColor(Color.parseColor("#E9E1F5"));
        codeView.setTextSize(13);
        codeView.setTypeface(Typeface.MONOSPACE);
        codeView.setGravity(Gravity.TOP | Gravity.START);
        codeView.setPadding(14, 12, 14, 14);
        codeView.setHorizontallyScrolling(true);
        codeView.setTextIsSelectable(true);

        scroll.addView(
                codeView,
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        card.addView(header);
        card.addView(scroll);

        LinearLayout.LayoutParams cp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        cp.setMargins(4, 5, 4, 10);
        container.addView(card, cp);

        copy.setOnClickListener(v -> copyText(context, code, "Code copié"));

        share.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, code);
            context.startActivity(
                    Intent.createChooser(intent, "Partager le code")
            );
        });

        String extension = extensionForLanguage(language);
        String prefix = sanitizeFileName(fileName);

        if (prefix.isEmpty()) {
            download.setOnClickListener(v ->
                    Toast.makeText(
                            context,
                            "Nom de fichier introuvable",
                            Toast.LENGTH_SHORT
                    ).show()
            );
            return;
        }

        if (prefix.endsWith(extension)) {
            prefix = prefix.substring(
                    0,
                    prefix.length() - extension.length()
            );
        }

        String finalPrefix = prefix;

        download.setOnClickListener(v ->
                FileUtils.saveTextFile(
                        context,
                        code,
                        finalPrefix,
                        extension,
                        mimeForExtension(extension)
                )
        );
    }

    private ImageButton createCodeAction(
            Context context,
            int icon,
            String description
    ) {
        ImageButton button = new ImageButton(context);
        button.setImageResource(icon);
        button.setColorFilter(Color.parseColor("#B79BCB"));
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setContentDescription(description);
        button.setPadding(6, 6, 6, 6);

        LinearLayout.LayoutParams actionParams =
                new LinearLayout.LayoutParams(30, 30);
        actionParams.setMargins(3, 0, 3, 0);
        button.setLayoutParams(actionParams);

        return button;
    }

    private void addTable(
            LinearLayout container,
            List<String> lines
    ) {
        if (lines.size() < 2) return;

        Context context = container.getContext();

        TableLayout table = new TableLayout(context);
        table.setStretchAllColumns(false);
        table.setShrinkAllColumns(false);
        table.setPadding(8, 4, 8, 4);

        int rowIndex = 0;

        for (String line : lines) {
            if (isSeparatorRow(line)) continue;

            List<String> cells = splitTableCells(line);
            TableRow row = new TableRow(context);

            for (String cell : cells) {
                TextView tv = new TextView(context);
                tv.setText(cell.trim());
                tv.setTextColor(
                        rowIndex == 0
                                ? Color.parseColor("#E8DDF3")
                                : Color.parseColor("#D2C5DE")
                );
                tv.setTextSize(13);
                tv.setPadding(14, 10, 14, 10);

                GradientDrawable bg = new GradientDrawable();
                bg.setColor(
                        rowIndex == 0
                                ? Color.parseColor("#29163B")
                                : Color.parseColor("#171126")
                );
                bg.setStroke(1, Color.parseColor("#3B2748"));
                tv.setBackground(bg);

                row.addView(
                        tv,
                        new TableRow.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                );
            }

            table.addView(row);
            rowIndex++;
        }

        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(false);
        scroll.addView(table);

        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        p.setMargins(4, 4, 4, 10);

        container.addView(scroll, p);
    }

    private boolean isTableStart(List<String> lines, int index) {
        if (index + 1 >= lines.size()) return false;

        String first = lines.get(index);
        String second = lines.get(index + 1);

        return isTableRow(first) && isSeparatorRow(second);
    }

    private boolean isTableRow(String line) {
        if (line == null) return false;
        String s = line.trim();
        return s.contains("|") && !s.startsWith("```");
    }

    private boolean isSeparatorRow(String line) {
        if (!isTableRow(line)) return false;

        String s = line.trim()
                .replace("|", "")
                .replace(":", "")
                .replace("-", "")
                .replace(" ", "");

        return s.isEmpty();
    }

    private List<String> splitTableCells(String line) {
        String s = line.trim();

        if (s.startsWith("|")) {
            s = s.substring(1);
        }

        if (s.endsWith("|")) {
            s = s.substring(0, s.length() - 1);
        }

        List<String> result = new ArrayList<>();

        for (String part : s.split("\\|", -1)) {
            result.add(
                    part.trim()
                            .replace("\\|", "|")
            );
        }

        return result;
    }

    private List<String> splitLines(String text) {
        List<String> result = new ArrayList<>();

        String[] parts = text.replace("\r", "").split("\n", -1);

        for (String part : parts) {
            result.add(part);
        }

        return result;
    }

    private String joinLines(List<String> lines) {
        StringBuilder b = new StringBuilder();

        for (int i = 0; i < lines.size(); i++) {
            b.append(lines.get(i));

            if (i < lines.size() - 1) {
                b.append('\n');
            }
        }

        return b.toString();
    }

    private String extractLanguage(String info) {
        if (info == null || info.isEmpty()) return "";

        String[] parts = info.split("\\s+");

        for (String part : parts) {
            if (part.contains("=")
                    || part.toLowerCase(Locale.ROOT).startsWith("filename")) {
                continue;
            }

            return part.replaceAll("[^A-Za-z0-9+#.-]", "");
        }

        return "";
    }

    private String extractFileName(
            String info,
            String fallback
    ) {
        if (info == null) return fallback;

        String lower = info.toLowerCase(Locale.ROOT);

        String[] keys = {
                "filename=",
                "file=",
                "name="
        };

        for (String key : keys) {
            int pos = lower.indexOf(key);

            if (pos >= 0) {
                String value = info.substring(pos + key.length())
                        .trim()
                        .split("\\s+")[0];

                return sanitizeFileName(value);
            }
        }

        return fallback;
    }

    private String extractFileNameFromCode(String code) {
        if (code == null) return "";

        String[] lines = code.split("\n");

        for (String line : lines) {
            String s = line.trim();

            String lower = s.toLowerCase(Locale.ROOT);

            String[] markers = {
                    "filename:",
                    "file:",
                    "name:"
            };

            for (String marker : markers) {
                int pos = lower.indexOf(marker);

                if (pos >= 0) {
                    String value = s.substring(
                            pos + marker.length()
                    ).trim();

                    value = value.replaceAll(
                            "^[#/*<>\\s]+|[#/*<>\\s]+$",
                            ""
                    );

                    if (!value.isEmpty()
                            && value.length() < 120) {
                        return sanitizeFileName(value);
                    }
                }
            }
        }

        return "";
    }

    private String sanitizeFileName(String name) {
        if (name == null) return "";

        String clean = name.trim()
                .replaceAll("[\\\\/:*?\"<>|]", "_");

        return clean.isEmpty() ? "" : clean;
    }

    private String extensionForLanguage(String language) {
        String l = language == null
                ? ""
                : language.toLowerCase(Locale.ROOT);

        if (l.equals("java")) return ".java";
        if (l.equals("kotlin") || l.equals("kt")) return ".kt";
        if (l.equals("javascript") || l.equals("js")) return ".js";
        if (l.equals("typescript") || l.equals("ts")) return ".ts";
        if (l.equals("python") || l.equals("py")) return ".py";
        if (l.equals("html")) return ".html";
        if (l.equals("css")) return ".css";
        if (l.equals("scss")) return ".scss";
        if (l.equals("xml")) return ".xml";
        if (l.equals("json")) return ".json";
        if (l.equals("yaml") || l.equals("yml")) return ".yml";
        if (l.equals("sql")) return ".sql";
        if (l.equals("php")) return ".php";
        if (l.equals("c")) return ".c";
        if (l.equals("cpp") || l.equals("c++")) return ".cpp";
        if (l.equals("csharp") || l.equals("cs")) return ".cs";
        if (l.equals("go") || l.equals("golang")) return ".go";
        if (l.equals("rust") || l.equals("rs")) return ".rs";
        if (l.equals("swift")) return ".swift";
        if (l.equals("dart")) return ".dart";
        if (l.equals("bash") || l.equals("sh") || l.equals("shell")) return ".sh";
        if (l.equals("markdown") || l.equals("md")) return ".md";

        return ".txt";
    }

    private String mimeForExtension(String extension) {
        if (extension == null) return "text/plain";

        if (extension.equals(".html")) return "text/html";
        if (extension.equals(".css")) return "text/css";
        if (extension.equals(".json")) return "application/json";
        if (extension.equals(".xml")) return "application/xml";
        if (extension.equals(".js")) return "text/javascript";
        if (extension.equals(".ts")) return "text/typescript";
        if (extension.equals(".svg")) return "image/svg+xml";

        return "text/plain";
    }

    private void copyText(
            Context context,
            String text,
            String message
    ) {
        ClipboardManager clipboard =
                (ClipboardManager)
                        context.getSystemService(
                                Context.CLIPBOARD_SERVICE
                        );

        if (clipboard != null) {
            clipboard.setPrimaryClip(
                    ClipData.newPlainText("KruxAI", text)
            );

            Toast.makeText(
                    context,
                    message,
                    Toast.LENGTH_SHORT
            ).show();
        }
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
        String rawMessageId = message.getId();

        final String messageId =
                (rawMessageId == null || rawMessageId.isEmpty())
                        ? String.valueOf(message.getTimestamp())
                        : rawMessageId;

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
        ImageButton btnEdit;
        ImageButton btnCopy;
        ImageButton btnRetry;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);

            tvMessage = itemView.findViewById(R.id.tvMessage);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnCopy = itemView.findViewById(R.id.btnCopy);
            btnRetry = itemView.findViewById(R.id.btnRetry);
        }
    }

    static class AiViewHolder
            extends RecyclerView.ViewHolder {

        LinearLayout messageContainer;
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

            messageContainer =
                    itemView.findViewById(
                            R.id.messageContainer
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
