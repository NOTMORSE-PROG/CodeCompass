package com.example.codecompass.ui.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codecompass.R;
import com.example.codecompass.model.CCSProgram;
import com.example.codecompass.model.University;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class UniversityAdapter extends RecyclerView.Adapter<UniversityAdapter.ViewHolder> {

    public interface OnUniversityTapListener {
        void onCardTap(University university);
    }

    private List<University> universities = new ArrayList<>();
    private final OnUniversityTapListener listener;

    public UniversityAdapter(OnUniversityTapListener listener) {
        this.listener = listener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setData(List<University> newUniversities) {
        universities.clear();
        if (newUniversities != null) universities.addAll(newUniversities);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_university, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(universities.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return universities.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName;
        final TextView tvAbbreviation;
        final TextView tvTypeBadge;
        final TextView tvLocation;
        final TextView tvChedCoe;
        final TextView tvChedCod;
        final LinearLayout layoutPrograms;
        final TextView tvTuition;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvAbbreviation = itemView.findViewById(R.id.tvAbbreviation);
            tvTypeBadge = itemView.findViewById(R.id.tvTypeBadge);
            tvLocation = itemView.findViewById(R.id.tvLocation);
            tvChedCoe = itemView.findViewById(R.id.tvChedCoe);
            tvChedCod = itemView.findViewById(R.id.tvChedCod);
            layoutPrograms = itemView.findViewById(R.id.layoutPrograms);
            tvTuition = itemView.findViewById(R.id.tvTuition);
        }

        void bind(University univ, OnUniversityTapListener listener) {
            Context ctx = itemView.getContext();

            tvName.setText(univ.getName());

            String abbr = univ.getAbbreviation();
            if (abbr != null && !abbr.isEmpty()) {
                tvAbbreviation.setText(abbr);
                tvAbbreviation.setVisibility(View.VISIBLE);
            } else {
                tvAbbreviation.setVisibility(View.GONE);
            }

            tvTypeBadge.setText(getTypeLabel(univ.getUniversityType()));
            if ("state".equals(univ.getUniversityType())) {
                tvTypeBadge.setBackgroundResource(R.drawable.bg_badge_green);
            } else {
                tvTypeBadge.setBackgroundResource(R.drawable.bg_badge_gray);
            }

            tvLocation.setText(ctx.getString(R.string.uni_location_format, univ.getCity(), univ.getRegion()));

            tvChedCoe.setVisibility(univ.isChedCoe() ? View.VISIBLE : View.GONE);
            tvChedCod.setVisibility(univ.isChedCod() ? View.VISIBLE : View.GONE);

            tvTuition.setText(formatTuition(univ));

            buildProgramChips(ctx, univ);

            itemView.setOnClickListener(v -> listener.onCardTap(univ));
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

        private String formatTuition(University u) {
            Integer min = u.getTuitionRangeMin();
            Integer max = u.getTuitionRangeMax();
            if (min == null && max == null) return "Tuition N/A";
            if (min != null && min == 0) return "Free / Minimal fees";
            NumberFormat fmt = NumberFormat.getNumberInstance(Locale.US);
            if (min != null && max != null) {
                return "₱" + fmt.format(min) + " – ₱" + fmt.format(max) + "/yr";
            }
            if (max != null) return "Up to ₱" + fmt.format(max) + "/yr";
            return "₱" + fmt.format(min) + "/yr";
        }

        private void buildProgramChips(Context ctx, University univ) {
            layoutPrograms.removeAllViews();
            List<CCSProgram> programs = univ.getPrograms();
            if (programs == null || programs.isEmpty()) return;

            int limit = Math.min(programs.size(), 4);
            int paddingH = (int) (ctx.getResources().getDisplayMetrics().density * 6);
            int paddingV = (int) (ctx.getResources().getDisplayMetrics().density * 2);
            int marginEnd = (int) (ctx.getResources().getDisplayMetrics().density * 4);

            for (int i = 0; i < limit; i++) {
                CCSProgram program = programs.get(i);
                TextView chip = new TextView(ctx);
                chip.setText(program.getAbbreviation());
                chip.setBackgroundResource(R.drawable.bg_badge_gray);
                chip.setPadding(paddingH, paddingV, paddingH, paddingV);
                chip.setTextSize(10f);
                chip.setTextColor(Color.parseColor("#4B5563"));

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                lp.setMarginEnd(marginEnd);
                layoutPrograms.addView(chip, lp);
            }
        }
    }
}
