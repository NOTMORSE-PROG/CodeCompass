package com.example.codecompass.ui.adapter;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codecompass.R;
import com.example.codecompass.model.Certification;
import com.example.codecompass.model.UserCertification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CertificationAdapter extends RecyclerView.Adapter<CertificationAdapter.ViewHolder> {

    public interface OnCertActionListener {
        void onTrack(Certification cert);
        void onCardTap(Certification cert);
    }

    // Provider → hex color (matches web PROVIDER_COLORS) — package-private for RecommendationAdapter
    static final Map<String, String> PROVIDER_COLORS = new HashMap<>();
    static {
        PROVIDER_COLORS.put("tesda",           "#0066CC");
        PROVIDER_COLORS.put("google",          "#4285F4");
        PROVIDER_COLORS.put("aws",             "#FF9900");
        PROVIDER_COLORS.put("comptia",         "#C8202F");
        PROVIDER_COLORS.put("microsoft",       "#00A4EF");
        PROVIDER_COLORS.put("cisco",           "#1BA0D7");
        PROVIDER_COLORS.put("meta",            "#0081FB");
        PROVIDER_COLORS.put("oracle",          "#F80000");
        PROVIDER_COLORS.put("freecodecamp",    "#0A0A23");
        PROVIDER_COLORS.put("ibm",             "#054ADA");
        PROVIDER_COLORS.put("mongodb",         "#00ED64");
        PROVIDER_COLORS.put("github",          "#24292F");
        PROVIDER_COLORS.put("kaggle",          "#20BEFF");
        PROVIDER_COLORS.put("harvard",         "#A51C30");
        PROVIDER_COLORS.put("hubspot",         "#FF7A59");
        PROVIDER_COLORS.put("salesforce",      "#00A1E0");
        PROVIDER_COLORS.put("postman",         "#FF6C37");
        PROVIDER_COLORS.put("scrum",           "#009FDA");
        PROVIDER_COLORS.put("linux_foundation","#003366");
        PROVIDER_COLORS.put("fortinet",        "#EE3124");
        PROVIDER_COLORS.put("hackerrank",      "#2EC866");
        PROVIDER_COLORS.put("other",           "#6B7280");
    }

    private List<Certification> certs = new ArrayList<>();
    private Map<Integer, UserCertification> tracking = new HashMap<>();
    private OnCertActionListener listener;

    public CertificationAdapter(OnCertActionListener listener) {
        this.listener = listener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setData(List<Certification> certs, Map<Integer, UserCertification> tracking) {
        this.certs = certs != null ? certs : new ArrayList<>();
        this.tracking = tracking != null ? tracking : new HashMap<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_certification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Certification cert = certs.get(position);
        UserCertification uc = tracking.get(cert.getId());
        holder.bind(cert, uc, listener);
    }

    @Override
    public int getItemCount() {
        return certs.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvProviderInitial;
        private final TextView tvCertName;
        private final TextView tvAbbreviation;
        private final TextView tvFreeBadge;
        private final TextView tvTrackBadge;
        private final TextView tvLevelBadge;
        private final TextView tvStudyHours;
        private final TextView tvStatusBadge;
        private final Button btnTrack;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvProviderInitial = itemView.findViewById(R.id.tvProviderInitial);
            tvCertName        = itemView.findViewById(R.id.tvCertName);
            tvAbbreviation    = itemView.findViewById(R.id.tvAbbreviation);
            tvFreeBadge       = itemView.findViewById(R.id.tvFreeBadge);
            tvTrackBadge      = itemView.findViewById(R.id.tvTrackBadge);
            tvLevelBadge      = itemView.findViewById(R.id.tvLevelBadge);
            tvStudyHours      = itemView.findViewById(R.id.tvStudyHours);
            tvStatusBadge     = itemView.findViewById(R.id.tvStatusBadge);
            btnTrack          = itemView.findViewById(R.id.btnTrack);
        }

        void bind(Certification cert, UserCertification uc, OnCertActionListener listener) {
            // Provider circle
            String colorHex = PROVIDER_COLORS.getOrDefault(cert.getProvider(), "#6B7280");
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(Color.parseColor(colorHex));
            tvProviderInitial.setBackground(circle);
            String label = cert.getAbbreviation() != null && !cert.getAbbreviation().isEmpty()
                    ? cert.getAbbreviation().substring(0, 1).toUpperCase(java.util.Locale.getDefault())
                    : cert.getName().substring(0, 1).toUpperCase(java.util.Locale.getDefault());
            tvProviderInitial.setText(label);

            // Name
            tvCertName.setText(cert.getName());

            // Abbreviation
            if (cert.getAbbreviation() != null && !cert.getAbbreviation().isEmpty()) {
                tvAbbreviation.setText(cert.getAbbreviation());
                tvAbbreviation.setVisibility(View.VISIBLE);
            } else {
                tvAbbreviation.setVisibility(View.GONE);
            }

            // FREE badge
            tvFreeBadge.setVisibility(cert.isFree() ? View.VISIBLE : View.GONE);

            // Track display badge
            String trackDisplay = cert.getTrackDisplay() != null ? cert.getTrackDisplay() : cert.getTrack();
            tvTrackBadge.setText(trackDisplay);

            // Level display badge
            String levelDisplay = cert.getLevelDisplay() != null ? cert.getLevelDisplay() : cert.getLevel();
            tvLevelBadge.setText(levelDisplay);

            // Study hours
            if (cert.getEstimatedStudyHours() != null && cert.getEstimatedStudyHours() > 0) {
                tvStudyHours.setText(itemView.getContext().getString(R.string.cert_study_hours_format, cert.getEstimatedStudyHours()));
                tvStudyHours.setVisibility(View.VISIBLE);
            } else {
                tvStudyHours.setVisibility(View.GONE);
            }

            // Status badge + Track button
            if (uc != null) {
                String statusLabel = getStatusLabel(uc.getStatus());
                tvStatusBadge.setText(statusLabel);
                tvStatusBadge.setVisibility(View.VISIBLE);
                btnTrack.setText(R.string.cert_update_btn);
            } else {
                tvStatusBadge.setVisibility(View.GONE);
                btnTrack.setText(R.string.certifications_track_btn);
            }
            // Browse tab: no border, transparent bg, black text (plain text button)
            if (btnTrack instanceof com.google.android.material.button.MaterialButton) {
                com.google.android.material.button.MaterialButton mb =
                        (com.google.android.material.button.MaterialButton) btnTrack;
                mb.setStrokeWidth(0);
                mb.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
                mb.setTextColor(Color.BLACK);
            }

            btnTrack.setOnClickListener(v -> {
                if (listener != null) listener.onTrack(cert);
            });

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onCardTap(cert);
            });
        }

        private String getStatusLabel(String status) {
            if (status == null) return "Interested";
            switch (status) {
                case "studying": return "Studying";
                case "passed":   return "Earned \u2713";
                case "expired":  return "Expired";
                default:         return "Interested";
            }
        }
    }
}
