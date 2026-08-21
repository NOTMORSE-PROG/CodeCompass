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
import java.util.List;
import java.util.Map;

public class MyCertsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnCertTapListener {
        void onCardTap(Certification cert, UserCertification uc);
    }

    // ── Item wrapper types ─────────────────────────────────────────────────────

    static final class HeaderItem {
        final String title;
        final int count;
        final int accentColor;
        HeaderItem(String title, int count, int accentColor) {
            this.title = title;
            this.count = count;
            this.accentColor = accentColor;
        }
    }

    static final class CertItem {
        final Certification cert;
        final UserCertification uc;
        CertItem(Certification cert, UserCertification uc) {
            this.cert = cert;
            this.uc = uc;
        }
    }

    // ── Status ordering & display ──────────────────────────────────────────────

    private static final String[] STATUS_ORDER  = {"studying", "interested", "passed", "expired"};
    private static final String[] STATUS_LABELS = {"Currently Studying", "Interested", "Earned ✓", "Expired"};
    private static final int[]    STATUS_COLORS = {
        0xFFFFC300,  // studying  → brand yellow
        0xFF2563EB,  // interested→ blue
        0xFF0A0A0A,  // passed    → black
        0xFF9CA3AF,  // expired   → gray
    };

    // ── View types ─────────────────────────────────────────────────────────────
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_CERT   = 1;

    // ── Data ───────────────────────────────────────────────────────────────────
    private final List<Object> items = new ArrayList<>();
    private OnCertTapListener listener;

    public MyCertsAdapter(OnCertTapListener listener) {
        this.listener = listener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setData(Map<Integer, UserCertification> myCerts,
                        Map<Integer, Certification> allCertsById) {
        items.clear();
        for (int i = 0; i < STATUS_ORDER.length; i++) {
            String status = STATUS_ORDER[i];
            List<CertItem> section = new ArrayList<>();
            for (UserCertification uc : myCerts.values()) {
                if (status.equals(uc.getStatus())) {
                    Certification cert = null;
                    if (uc.getCertification() != null) {
                        cert = allCertsById.get(uc.getCertification().getId());
                    }
                    if (cert != null) section.add(new CertItem(cert, uc));
                }
            }
            if (!section.isEmpty()) {
                items.add(new HeaderItem(STATUS_LABELS[i], section.size(), STATUS_COLORS[i]));
                items.addAll(section);
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof HeaderItem ? TYPE_HEADER : TYPE_CERT;
    }

    @Override
    public int getItemCount() { return items.size(); }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderVH(inf.inflate(R.layout.item_my_certs_header, parent, false));
        }
        return new CertVH(inf.inflate(R.layout.item_certification, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderVH) {
            ((HeaderVH) holder).bind((HeaderItem) items.get(position));
        } else {
            CertItem ci = (CertItem) items.get(position);
            ((CertVH) holder).bind(ci.cert, ci.uc, listener);
        }
    }

    // ── ViewHolders ────────────────────────────────────────────────────────────

    static class HeaderVH extends RecyclerView.ViewHolder {
        private final View viewAccent;
        private final TextView tvTitle;
        private final TextView tvCount;

        HeaderVH(@NonNull View v) {
            super(v);
            viewAccent = v.findViewById(R.id.viewAccent);
            tvTitle    = v.findViewById(R.id.tvSectionTitle);
            tvCount    = v.findViewById(R.id.tvCount);
        }

        void bind(HeaderItem item) {
            tvTitle.setText(item.title);
            tvCount.setText(String.valueOf(item.count));
            GradientDrawable rect = new GradientDrawable();
            rect.setShape(GradientDrawable.RECTANGLE);
            rect.setCornerRadius(4f);
            rect.setColor(item.accentColor);
            viewAccent.setBackground(rect);
        }
    }

    static class CertVH extends RecyclerView.ViewHolder {
        private final TextView tvProviderInitial;
        private final TextView tvCertName;
        private final TextView tvAbbreviation;
        private final TextView tvFreeBadge;
        private final TextView tvTrackBadge;
        private final TextView tvLevelBadge;
        private final TextView tvStudyHours;
        private final TextView tvStatusBadge;
        private final Button   btnTrack;

        CertVH(@NonNull View v) {
            super(v);
            tvProviderInitial = v.findViewById(R.id.tvProviderInitial);
            tvCertName        = v.findViewById(R.id.tvCertName);
            tvAbbreviation    = v.findViewById(R.id.tvAbbreviation);
            tvFreeBadge       = v.findViewById(R.id.tvFreeBadge);
            tvTrackBadge      = v.findViewById(R.id.tvTrackBadge);
            tvLevelBadge      = v.findViewById(R.id.tvLevelBadge);
            tvStudyHours      = v.findViewById(R.id.tvStudyHours);
            tvStatusBadge     = v.findViewById(R.id.tvStatusBadge);
            btnTrack          = v.findViewById(R.id.btnTrack);
        }

        void bind(Certification cert, UserCertification uc, OnCertTapListener listener) {
            // Provider circle
            String colorHex = CertificationAdapter.PROVIDER_COLORS.getOrDefault(
                    cert.getProvider(), "#6B7280");
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(Color.parseColor(colorHex));
            tvProviderInitial.setBackground(circle);
            String initLabel = cert.getAbbreviation() != null && !cert.getAbbreviation().isEmpty()
                    ? cert.getAbbreviation().substring(0, 1).toUpperCase(java.util.Locale.getDefault())
                    : cert.getName().substring(0, 1).toUpperCase(java.util.Locale.getDefault());
            tvProviderInitial.setText(initLabel);

            tvCertName.setText(cert.getName());

            if (cert.getAbbreviation() != null && !cert.getAbbreviation().isEmpty()) {
                tvAbbreviation.setText(cert.getAbbreviation());
                tvAbbreviation.setVisibility(View.VISIBLE);
            } else {
                tvAbbreviation.setVisibility(View.GONE);
            }

            tvFreeBadge.setVisibility(cert.isFree() ? View.VISIBLE : View.GONE);

            tvTrackBadge.setText(cert.getTrackDisplay() != null
                    ? cert.getTrackDisplay() : cert.getTrack());
            tvLevelBadge.setText(cert.getLevelDisplay() != null
                    ? cert.getLevelDisplay() : cert.getLevel());

            if (cert.getEstimatedStudyHours() != null && cert.getEstimatedStudyHours() > 0) {
                tvStudyHours.setText(itemView.getContext().getString(R.string.cert_study_hours_format, cert.getEstimatedStudyHours()));
                tvStudyHours.setVisibility(View.VISIBLE);
            } else {
                tvStudyHours.setVisibility(View.GONE);
            }

            // Status badge — always visible in My Certs
            tvStatusBadge.setText(statusLabel(uc.getStatus()));
            tvStatusBadge.setVisibility(View.VISIBLE);

            // "Update" button: black outlined, black text
            btnTrack.setText(R.string.cert_update_btn);
            btnTrack.setTextColor(Color.BLACK);
            btnTrack.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
            if (btnTrack instanceof com.google.android.material.button.MaterialButton) {
                com.google.android.material.button.MaterialButton mb =
                        (com.google.android.material.button.MaterialButton) btnTrack;
                mb.setStrokeColor(ColorStateList.valueOf(Color.BLACK));
                mb.setStrokeWidth(4);
            }

            btnTrack.setOnClickListener(v -> {
                if (listener != null) listener.onCardTap(cert, uc);
            });
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onCardTap(cert, uc);
            });
        }

        private String statusLabel(String status) {
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
