package com.example.codecompass.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.codecompass.R;
import com.example.codecompass.api.ApiClient;
import com.example.codecompass.api.TokenManager;
import com.example.codecompass.model.GenerateBulletsRequest;
import com.example.codecompass.model.GenerateBulletsResponse;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AiBulletGeneratorBottomSheet extends BottomSheetDialogFragment {

    public interface OnBulletSelectedListener {
        void onBulletSelected(String bullet);
    }

    private static final String ARG_RESUME_ID = "resume_id";
    private static final String ARG_JOB_TITLE = "job_title";

    private int resumeId;
    private OnBulletSelectedListener listener;

    public static AiBulletGeneratorBottomSheet newInstance(int resumeId, String jobTitle) {
        AiBulletGeneratorBottomSheet f = new AiBulletGeneratorBottomSheet();
        Bundle args = new Bundle();
        args.putInt(ARG_RESUME_ID, resumeId);
        args.putString(ARG_JOB_TITLE, jobTitle != null ? jobTitle : "");
        f.setArguments(args);
        return f;
    }

    public void setOnBulletSelectedListener(OnBulletSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            resumeId = getArguments().getInt(ARG_RESUME_ID);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_ai_bullets, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        EditText etJobTitle = view.findViewById(R.id.etBulletJobTitle);
        EditText etAchievement = view.findViewById(R.id.etBulletAchievement);
        LinearProgressIndicator loadingBar = view.findViewById(R.id.bulletLoadingBar);
        LinearLayout containerResults = view.findViewById(R.id.containerBulletResults);

        if (getArguments() != null) {
            etJobTitle.setText(getArguments().getString(ARG_JOB_TITLE, ""));
        }

        // We need a generate button — add it dynamically before the loading bar
        MaterialButton btnGenerate = new MaterialButton(requireContext());
        btnGenerate.setText(R.string.resume_ai_bullets_generate);
        btnGenerate.setTextColor(requireContext().getColor(R.color.colorTextPrimary));
        btnGenerate.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                requireContext().getColor(R.color.colorPrimary)));

        LinearLayout parent = (LinearLayout) view;
        int loadingIndex = parent.indexOfChild(loadingBar);
        parent.addView(btnGenerate, loadingIndex);

        btnGenerate.setOnClickListener(v -> {
            String jobTitle = etJobTitle.getText().toString().trim();
            String achievement = etAchievement.getText().toString().trim();
            if (jobTitle.isEmpty() || achievement.isEmpty()) {
                Toast.makeText(requireContext(), R.string.error_field_required, Toast.LENGTH_SHORT).show();
                return;
            }

            btnGenerate.setEnabled(false);
            loadingBar.setVisibility(View.VISIBLE);
            containerResults.setVisibility(View.GONE);
            containerResults.removeAllViews();

            String token = TokenManager.getBearerToken(requireContext());
            ApiClient.getService().generateBullets(token, resumeId,
                    new GenerateBulletsRequest(jobTitle, achievement))
                    .enqueue(new Callback<GenerateBulletsResponse>() {
                        @Override
                        public void onResponse(@NonNull Call<GenerateBulletsResponse> call,
                                               @NonNull Response<GenerateBulletsResponse> response) {
                            btnGenerate.setEnabled(true);
                            loadingBar.setVisibility(View.GONE);
                            if (response.isSuccessful() && response.body() != null
                                    && response.body().getBullets() != null) {
                                containerResults.setVisibility(View.VISIBLE);
                                for (String bullet : response.body().getBullets()) {
                                    addBulletResult(containerResults, bullet);
                                }
                            } else {
                                Toast.makeText(requireContext(), R.string.resume_ats_error,
                                        Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<GenerateBulletsResponse> call,
                                              @NonNull Throwable t) {
                            btnGenerate.setEnabled(true);
                            loadingBar.setVisibility(View.GONE);
                            Toast.makeText(requireContext(), R.string.error_network,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        });
    }

    private void addBulletResult(LinearLayout container, String bullet) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int pad = (int) (8 * getResources().getDisplayMetrics().density);
        row.setPadding(0, pad, 0, pad);

        TextView tv = new TextView(requireContext());
        tv.setText(String.format(Locale.getDefault(), "• %s", bullet));
        tv.setTextSize(13);
        tv.setTextColor(requireContext().getColor(R.color.colorTextPrimary));
        LinearLayout.LayoutParams tvParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tv.setLayoutParams(tvParams);
        row.addView(tv);

        MaterialButton btnUse = new MaterialButton(requireContext(),
                null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnUse.setText(R.string.resume_ai_bullets_use);
        btnUse.setTextSize(12);
        btnUse.setOnClickListener(v -> {
            if (listener != null) listener.onBulletSelected(bullet);
            dismiss();
        });
        row.addView(btnUse);

        container.addView(row);
    }
}
