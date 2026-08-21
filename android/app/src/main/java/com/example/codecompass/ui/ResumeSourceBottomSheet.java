package com.example.codecompass.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.codecompass.R;
import com.example.codecompass.api.ApiClient;
import com.example.codecompass.api.TokenManager;
import com.example.codecompass.model.Resume;
import com.example.codecompass.model.ResumeListResponse;
import com.example.codecompass.ui.adapter.ResumeRowBinder;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Picker shown when the user taps "Upload Resume" on the Jobs screen. Offers
 * the user two sources for job matching: one of their saved resumes, or a
 * freshly uploaded PDF. Fetches the resume list on open; falls back to an
 * empty-hint state when the user has none saved.
 */
public class ResumeSourceBottomSheet extends BottomSheetDialogFragment {

    public interface Listener {
        void onSavedResumeChosen(int resumeId, String resumeTitle);
        void onUploadPdfChosen();
        void onAuthExpired();
    }

    private Listener listener;
    private boolean dispatched = false;   // double-tap / post-dismiss guard

    private LinearLayout layoutLoading;
    private TextView tvEmpty;
    private LinearLayout layoutList;
    private LinearLayout containerResumeList;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_resume_source, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        layoutLoading       = view.findViewById(R.id.layoutSourceLoading);
        tvEmpty             = view.findViewById(R.id.tvSourceEmpty);
        layoutList          = view.findViewById(R.id.layoutSourceList);
        containerResumeList = view.findViewById(R.id.containerResumeList);

        MaterialButton btnUploadPdf = view.findViewById(R.id.btnSourceUploadPdf);
        btnUploadPdf.setOnClickListener(v -> {
            if (dispatched) return;
            dispatched = true;
            dismiss();
            if (listener != null) listener.onUploadPdfChosen();
        });

        showLoading();
        loadResumes();
    }

    private void showLoading() {
        layoutLoading.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        layoutList.setVisibility(View.GONE);
    }

    private void showEmpty() {
        layoutLoading.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.VISIBLE);
        layoutList.setVisibility(View.GONE);
    }

    private void showList() {
        layoutLoading.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.GONE);
        layoutList.setVisibility(View.VISIBLE);
    }

    private void loadResumes() {
        String token = TokenManager.getBearerToken(requireContext());
        ApiClient.getService().getResumes(token).enqueue(new Callback<ResumeListResponse>() {
            @Override
            public void onResponse(@NonNull Call<ResumeListResponse> call,
                                   @NonNull Response<ResumeListResponse> response) {
                if (!isAdded()) return;

                if (response.code() == 401) {
                    dismissAllowingStateLoss();
                    if (listener != null) listener.onAuthExpired();
                    return;
                }

                if (response.isSuccessful() && response.body() != null) {
                    renderResumes(response.body().getResults());
                } else {
                    // Treat unexpected errors as "no resumes" — the PDF button still works.
                    showEmpty();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResumeListResponse> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                showEmpty();
            }
        });
    }

    private void renderResumes(List<Resume> resumes) {
        containerResumeList.removeAllViews();

        if (resumes == null || resumes.isEmpty()) {
            showEmpty();
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (Resume resume : resumes) {
            View row = inflater.inflate(R.layout.item_resume, containerResumeList, false);
            ResumeRowBinder.bind(row, resume);
            row.setOnClickListener(v -> {
                if (dispatched) return;
                dispatched = true;
                dismiss();
                if (listener != null) {
                    listener.onSavedResumeChosen(resume.getId(), resume.getTitle());
                }
            });
            // No long-press delete in the picker — this is a selection surface only.
            row.setOnLongClickListener(null);
            containerResumeList.addView(row);
        }
        showList();
    }
}
