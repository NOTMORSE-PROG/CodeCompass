package com.example.codecompass.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.codecompass.R;
import com.example.codecompass.model.JobListing;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.gson.Gson;

import java.util.Set;

public class JobDetailBottomSheet extends BottomSheetDialogFragment {

    public interface OnSaveChangedListener {
        void onSaveToggled(JobListing job, boolean nowSaved);
    }

    private static final String ARG_JOB        = "job_json";
    private static final String ARG_SAVED_IDS  = "saved_ids_json";

    private JobListing job;
    private boolean isSaved;
    private OnSaveChangedListener listener;

    public static JobDetailBottomSheet newInstance(JobListing job, Set<Integer> savedIds) {
        JobDetailBottomSheet f = new JobDetailBottomSheet();
        Bundle args = new Bundle();
        Gson gson = new Gson();
        args.putString(ARG_JOB, gson.toJson(job));
        args.putBoolean(ARG_SAVED_IDS, savedIds != null && savedIds.contains(job.getId()));
        f.setArguments(args);
        return f;
    }

    public void setOnSaveChangedListener(OnSaveChangedListener l) {
        this.listener = l;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            job = new Gson().fromJson(getArguments().getString(ARG_JOB), JobListing.class);
            isSaved = getArguments().getBoolean(ARG_SAVED_IDS, false);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_job_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (job == null) { dismiss(); return; }

        TextView tvTitle    = view.findViewById(R.id.tvDetailTitle);
        TextView tvCompany  = view.findViewById(R.id.tvDetailCompany);
        TextView tvLocation = view.findViewById(R.id.tvDetailLocation);
        TextView tvJobType  = view.findViewById(R.id.tvDetailJobType);
        TextView tvSalary   = view.findViewById(R.id.tvDetailSalary);
        TextView tvDesc     = view.findViewById(R.id.tvDetailDescription);
        MaterialButton btnSave  = view.findViewById(R.id.btnSaveJob);
        MaterialButton btnApply = view.findViewById(R.id.btnApply);

        tvTitle.setText(job.getTitle());
        tvCompany.setText(job.getCompany());
        tvLocation.setText(job.getLocation() != null ? job.getLocation() : "Philippines");
        tvJobType.setText(getJobTypeLabel(job.getJobType()));

        String salary = job.getSalaryRange();
        if (salary != null && !salary.isEmpty()) {
            tvSalary.setText(salary);
            tvSalary.setVisibility(View.VISIBLE);
        }

        String desc = job.getDescription();
        tvDesc.setText(desc != null ? desc.trim() : "No description available.");

        updateSaveButton(btnSave);

        btnSave.setOnClickListener(v -> {
            isSaved = !isSaved;
            updateSaveButton(btnSave);
            if (listener != null) listener.onSaveToggled(job, isSaved);
        });

        if (job.getApplyUrl() != null && !job.getApplyUrl().isEmpty()) {
            btnApply.setOnClickListener(v -> openUrl(job.getApplyUrl()));
        } else {
            btnApply.setEnabled(false);
        }
    }

    private void updateSaveButton(MaterialButton btn) {
        btn.setText(isSaved ? getString(R.string.jobs_unsave) : getString(R.string.jobs_save));
    }

    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        if (intent.resolveActivity(requireActivity().getPackageManager()) != null) {
            startActivity(intent);
        } else {
            Toast.makeText(requireContext(),
                    R.string.error_no_browser, Toast.LENGTH_LONG).show();
        }
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
}
