package com.example.codecompass.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codecompass.R;
import com.example.codecompass.model.UniversityRecommendation;

import java.util.ArrayList;
import java.util.List;

public class UniversityRecommendationAdapter
        extends RecyclerView.Adapter<UniversityRecommendationAdapter.ViewHolder> {

    public interface OnRecTapListener {
        void onTap(UniversityRecommendation rec);
    }

    private List<UniversityRecommendation> items = new ArrayList<>();
    private final OnRecTapListener listener;

    public UniversityRecommendationAdapter(OnRecTapListener listener) {
        this.listener = listener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setData(List<UniversityRecommendation> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recommendation_university, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvMatchScore;
        final TextView tvRecName;
        final TextView tvRecAbbreviation;
        final TextView tvRecLocation;
        final TextView tvRecTypeBadge;
        final TextView tvRecChedCoe;
        final TextView tvRecChedCod;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMatchScore = itemView.findViewById(R.id.tvMatchScore);
            tvRecName = itemView.findViewById(R.id.tvRecName);
            tvRecAbbreviation = itemView.findViewById(R.id.tvRecAbbreviation);
            tvRecLocation = itemView.findViewById(R.id.tvRecLocation);
            tvRecTypeBadge = itemView.findViewById(R.id.tvRecTypeBadge);
            tvRecChedCoe = itemView.findViewById(R.id.tvRecChedCoe);
            tvRecChedCod = itemView.findViewById(R.id.tvRecChedCod);
        }

        void bind(UniversityRecommendation rec, OnRecTapListener listener) {
            tvMatchScore.setText(itemView.getContext().getString(R.string.uni_match_score_format, rec.getMatchScore()));

            tvRecName.setText(rec.getName());

            String abbr = rec.getAbbreviation();
            if (abbr != null && !abbr.isEmpty()) {
                tvRecAbbreviation.setText(abbr);
                tvRecAbbreviation.setVisibility(View.VISIBLE);
            } else {
                tvRecAbbreviation.setVisibility(View.GONE);
            }

            tvRecLocation.setText(itemView.getContext().getString(R.string.uni_location_format, rec.getCity(), rec.getRegion()));

            tvRecTypeBadge.setText(getTypeLabel(rec.getUniversityType()));

            tvRecChedCoe.setVisibility(rec.isChedCoe() ? View.VISIBLE : View.GONE);
            tvRecChedCod.setVisibility(rec.isChedCod() ? View.VISIBLE : View.GONE);

            itemView.setOnClickListener(v -> listener.onTap(rec));
        }

        private String getTypeLabel(String type) {
            if (type == null) return "";
            switch (type) {
                case "state":   return "State University";
                case "private": return "Private";
                case "local":   return "Local College";
                default:        return type;
            }
        }
    }
}
