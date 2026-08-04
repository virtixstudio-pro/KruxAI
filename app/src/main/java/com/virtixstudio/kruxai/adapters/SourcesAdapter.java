package com.virtixstudio.kruxai.adapters;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.virtixstudio.kruxai.R;
import com.virtixstudio.kruxai.models.SearchResult;

import java.util.List;

public class SourcesAdapter extends RecyclerView.Adapter<SourcesAdapter.SourceViewHolder> {

    private final List<SearchResult> sources;

    public SourcesAdapter(List<SearchResult> sources) {
        this.sources = sources;
    }

    @NonNull
    @Override
    public SourceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_source_card, parent, false);
        return new SourceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SourceViewHolder holder, int position) {
        SearchResult result = sources.get(position);
        holder.tvDomain.setText(result.getDomain() != null ? result.getDomain() : "web");
        holder.tvTitle.setText(result.getTitle());

        holder.itemView.setOnClickListener(v -> {
            Context context = v.getContext();
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(result.getUrl()));
            context.startActivity(browserIntent);
        });
    }

    @Override
    public int getItemCount() {
        return sources != null ? sources.size() : 0;
    }

    static class SourceViewHolder extends RecyclerView.ViewHolder {
        TextView tvDomain, tvTitle;

        SourceViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDomain = itemView.findViewById(R.id.tvDomain);
            tvTitle = itemView.findViewById(R.id.tvTitle);
        }
    }
}
