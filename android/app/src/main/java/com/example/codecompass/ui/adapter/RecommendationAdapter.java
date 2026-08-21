package com.example.codecompass.ui.adapter;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codecompass.R;
import com.example.codecompass.model.Certification;

import java.util.ArrayList;
import java.util.List;

public class RecommendationAdapter extends RecyclerView.Adapter<RecommendationAdapter.ViewHolder> {

    public interface OnRecTapListener {
        void onTap(Certification cert);
    }

    private List<Certification> certs = new ArrayList<>();
    private final OnRecTapListener listener;

    public RecommendationAdapter(OnRecTapListener listener) {
        this.listener = listener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setData(List<Certification> certs) {
        this.certs = certs != null ? certs : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recommendation_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(certs.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return certs.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvProviderInitial;
        private final TextView tvName;
        private final TextView tvTrack;
        private final TextView tvFree;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvProviderInitial = itemView.findViewById(R.id.tvRecProviderInitial);
            tvName            = itemView.findViewById(R.id.tvRecName);
            tvTrack           = itemView.findViewById(R.id.tvRecTrack);
            tvFree            = itemView.findViewById(R.id.tvRecFree);
        }

        void bind(Certification cert, OnRecTapListener listener) {
            String colorHex = CertificationAdapter.PROVIDER_COLORS.getOrDefault(
                    cert.getProvider(), "#6B7280");
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(Color.parseColor(colorHex));
            tvProviderInitial.setBackground(circle);

            String label = cert.getAbbreviation() != null && !cert.getAbbreviation().isEmpty()
                    ? cert.getAbbreviation().substring(0, 1).toUpperCase(java.util.Locale.getDefault())
                    : cert.getName().substring(0, 1).toUpperCase(java.util.Locale.getDefault());
            tvProviderInitial.setText(label);

            tvName.setText(cert.getName());

            String track = cert.getTrackDisplay() != null ? cert.getTrackDisplay() : cert.getTrack();
            String level = cert.getLevelDisplay() != null ? cert.getLevelDisplay() : cert.getLevel();
            if (track != null && level != null) {
                tvTrack.setText(itemView.getContext().getString(R.string.rec_track_level_format, track, level));
            } else if (track != null) {
                tvTrack.setText(track);
            } else {
                tvTrack.setText("");
            }

            tvFree.setVisibility(cert.isFree() ? View.VISIBLE : View.GONE);

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onTap(cert);
            });
        }
    }
}
