package com.virtixstudio.kruxai.adapters;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.virtixstudio.kruxai.R;
import com.virtixstudio.kruxai.models.SearchResult;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SourcesAdapter
        extends RecyclerView.Adapter<SourcesAdapter.SourceViewHolder> {

    private final List<SearchResult> sources;

    private final ExecutorService imageExecutor =
            Executors.newFixedThreadPool(3);

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    public SourcesAdapter(List<SearchResult> sources) {
        this.sources = sources;
    }

    @NonNull
    @Override
    public SourceViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_source_card, parent, false);

        return new SourceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull SourceViewHolder holder,
            int position
    ) {
        SearchResult result = sources.get(position);

        holder.tvDomain.setText(
                result.getDomain() != null
                        ? result.getDomain()
                        : "web"
        );

        holder.tvTitle.setText(
                result.getTitle() == null
                        ? "Source Web"
                        : result.getTitle()
        );

        String snippet = result.getSnippet();

        if (snippet == null || snippet.trim().isEmpty()) {
            holder.tvSnippet.setVisibility(View.GONE);
        } else {
            holder.tvSnippet.setVisibility(View.VISIBLE);
            holder.tvSnippet.setText(snippet);
        }

        holder.ivPreview.setImageResource(R.drawable.ic_globe);
        holder.ivPreview.setAlpha(0.35f);

        holder.itemView.setOnClickListener(v -> {
            Context context = v.getContext();

            try {
                Intent browserIntent =
                        new Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(result.getUrl())
                        );

                context.startActivity(browserIntent);
            } catch (Exception ignored) {
            }
        });

        loadPreview(holder, result);
    }

    private void loadPreview(
            SourceViewHolder holder,
            SearchResult result
    ) {
        final String directImage = result.getImageUrl();

        if (directImage != null
                && !directImage.trim().isEmpty()) {

            loadBitmap(
                    holder,
                    directImage
            );

            return;
        }

        final String pageUrl = result.getUrl();

        if (pageUrl == null
                || pageUrl.trim().isEmpty()) {
            return;
        }

        imageExecutor.execute(() -> {
            String imageUrl = extractPreviewUrl(pageUrl);

            if (imageUrl == null || imageUrl.isEmpty()) {
                return;
            }

            result.setImageUrl(imageUrl);

            loadBitmap(
                    holder,
                    imageUrl
            );
        });
    }

    private String extractPreviewUrl(String pageUrl) {
        try {
            Document document = Jsoup.connect(pageUrl)
                    .userAgent(
                            "Mozilla/5.0 " +
                            "(Linux; Android) AppleWebKit/537.36 " +
                            "Chrome/120 Mobile Safari/537.36"
                    )
                    .timeout(7000)
                    .followRedirects(true)
                    .get();

            String imageUrl = null;

            if (document.head() != null) {
                org.jsoup.nodes.Element og =
                        document.head().selectFirst(
                                "meta[property=og:image]"
                        );

                if (og != null) {
                    imageUrl = og.attr("content");
                }

                if (imageUrl == null
                        || imageUrl.trim().isEmpty()) {

                    org.jsoup.nodes.Element twitter =
                            document.head().selectFirst(
                                    "meta[name=twitter:image]"
                            );

                    if (twitter != null) {
                        imageUrl = twitter.attr("content");
                    }
                }
            }

            if (imageUrl == null
                    || imageUrl.trim().isEmpty()) {
                return null;
            }

            return new URL(
                    new URL(pageUrl),
                    imageUrl
            ).toString();

        } catch (Exception ignored) {
            return null;
        }
    }

    private void loadBitmap(
            SourceViewHolder holder,
            String imageUrl
    ) {
        imageExecutor.execute(() -> {
            Bitmap bitmap = null;

            try {
                URL url = new URL(imageUrl);

                HttpURLConnection connection =
                        (HttpURLConnection) url.openConnection();

                connection.setConnectTimeout(7000);
                connection.setReadTimeout(7000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0"
                );

                try (InputStream input =
                             connection.getInputStream()) {

                    bitmap =
                            BitmapFactory.decodeStream(input);
                }

                connection.disconnect();

            } catch (Exception ignored) {
            }

            if (bitmap == null) {
                return;
            }

            Bitmap finalBitmap = bitmap;

            mainHandler.post(() -> {
                if (holder.getBindingAdapterPosition()
                        == RecyclerView.NO_POSITION) {
                    return;
                }

                holder.ivPreview.setImageBitmap(finalBitmap);
                holder.ivPreview.setAlpha(1f);
            });
        });
    }

    @Override
    public int getItemCount() {
        return sources != null
                ? sources.size()
                : 0;
    }

    static class SourceViewHolder
            extends RecyclerView.ViewHolder {

        TextView tvDomain;
        TextView tvTitle;
        TextView tvSnippet;
        ImageView ivPreview;

        SourceViewHolder(@NonNull View itemView) {
            super(itemView);

            tvDomain =
                    itemView.findViewById(R.id.tvDomain);

            tvTitle =
                    itemView.findViewById(R.id.tvTitle);

            tvSnippet =
                    itemView.findViewById(R.id.tvSnippet);

            ivPreview =
                    itemView.findViewById(R.id.ivPreview);
        }
    }
}
