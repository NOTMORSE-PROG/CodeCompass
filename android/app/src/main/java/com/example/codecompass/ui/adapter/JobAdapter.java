package com.example.codecompass.ui.adapter;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codecompass.R;
import com.example.codecompass.model.JobListing;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class JobAdapter extends RecyclerView.Adapter<JobAdapter.JobViewHolder> {

    public interface OnJobActionListener {
        void onJobTap(JobListing job);
        void onSaveToggle(JobListing job, boolean currentlySaved);
    }

    private final List<JobListing> jobs = new ArrayList<>();
    private Set<Integer> savedJobIds;
    private boolean resumeMode = false;
    private final OnJobActionListener listener;

    public JobAdapter(OnJobActionListener listener) {
        this.listener = listener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setData(List<JobListing> newJobs, Set<Integer> savedIds) {
        jobs.clear();
        if (newJobs != null) jobs.addAll(newJobs);
        this.savedJobIds = savedIds;
        notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setSavedIds(Set<Integer> savedIds) {
        this.savedJobIds = savedIds;
        notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setResumeMode(boolean resumeMode) {
        this.resumeMode = resumeMode;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public JobViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_job, parent, false);
        return new JobViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull JobViewHolder holder, int position) {
        JobListing job = jobs.get(position);
        boolean saved = savedJobIds != null && savedJobIds.contains(job.getId());

        holder.tvTitle.setText(job.getTitle());
        holder.tvCompany.setText(job.getCompany());
        holder.tvLocation.setText(job.getLocation() != null ? job.getLocation() : "Philippines");
        holder.tvJobType.setText(getJobTypeLabel(job.getJobType()));

        String salary = job.getSalaryRange();
        if (salary != null && !salary.isEmpty()) {
            holder.tvSalary.setText(salary);
            holder.tvSalary.setVisibility(View.VISIBLE);
        } else {
            holder.tvSalary.setVisibility(View.GONE);
        }

        holder.btnBookmark.setImageResource(
                saved ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark_outline);

        // Resume mode visual indicators
        holder.viewResumeAccent.setVisibility(resumeMode ? View.VISIBLE : View.GONE);
        holder.tvResumeMatchBadge.setVisibility(resumeMode ? View.VISIBLE : View.GONE);

        // Match terms row — only visible in resume mode and if terms exist
        List<String> matchTerms = job.getMatchTerms();
        if (resumeMode && matchTerms != null && !matchTerms.isEmpty()) {
            holder.layoutMatchTerms.setVisibility(View.VISIBLE);
            holder.chipGroupMatchTerms.removeAllViews();

            int primary   = holder.itemView.getContext().getColor(R.color.colorPrimary);
            int onPrimary = holder.itemView.getContext().getColor(R.color.colorOnPrimary);

            for (String term : matchTerms) {
                Chip chip = new Chip(holder.itemView.getContext());
                chip.setText(term);
                chip.setClickable(false);
                chip.setCheckable(false);
                chip.setChipBackgroundColor(ColorStateList.valueOf(primary));
                chip.setTextColor(onPrimary);
                chip.setTextSize(10f);
                chip.setEnsureMinTouchTargetSize(false);
                holder.chipGroupMatchTerms.addView(chip);
            }
        } else {
            holder.layoutMatchTerms.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onJobTap(job);
        });

        holder.btnBookmark.setOnClickListener(v -> {
            if (listener != null) listener.onSaveToggle(job, saved);
        });
    }

    @Override
    public int getItemCount() {
        return jobs.size();
    }

    private static String getJobTypeLabel(String type) {
        if (type == null) return "";
        switch (type) {
            case "full_time":   return "Full-time";
            case "part_time":   return "Part-time";
            case "internship":  return "Internship";
            case "freelance":   return "Freelance";
            case "remote":      return "Remote";
            default:            return type;
        }
    }

    static class JobViewHolder extends RecyclerView.ViewHolder {
        final TextView   tvTitle;
        final TextView   tvCompany;
        final TextView   tvLocation;
        final TextView   tvJobType;
        final TextView   tvSalary;
        final ImageView  btnBookmark;
        final View       viewResumeAccent;
        final TextView   tvResumeMatchBadge;
        final LinearLayout layoutMatchTerms;
        final ChipGroup  chipGroupMatchTerms;

        JobViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle             = itemView.findViewById(R.id.tvJobTitle);
            tvCompany           = itemView.findViewById(R.id.tvCompany);
            tvLocation          = itemView.findViewById(R.id.tvLocation);
            tvJobType           = itemView.findViewById(R.id.tvJobType);
            tvSalary            = itemView.findViewById(R.id.tvSalary);
            btnBookmark         = itemView.findViewById(R.id.btnBookmark);
            viewResumeAccent    = itemView.findViewById(R.id.viewResumeAccent);
            tvResumeMatchBadge  = itemView.findViewById(R.id.tvResumeMatchBadge);
            layoutMatchTerms    = itemView.findViewById(R.id.layoutMatchTerms);
            chipGroupMatchTerms = itemView.findViewById(R.id.chipGroupMatchTerms);
        }
    }
}
