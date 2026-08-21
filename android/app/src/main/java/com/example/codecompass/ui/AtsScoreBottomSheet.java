package com.example.codecompass.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.codecompass.R;
import com.example.codecompass.api.ApiClient;
import com.example.codecompass.api.TokenManager;
import com.example.codecompass.model.AtsScoreRequest;
import com.example.codecompass.model.AtsScoreResponse;
import com.example.codecompass.model.ParseJobRequest;
import com.example.codecompass.model.ParseJobResponse;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AtsScoreBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_RESUME_ID = "resume_id";
    private static final String ARG_CONTENT_JSON = "content_json";

    private int resumeId;

    public static AtsScoreBottomSheet newInstance(int resumeId, String contentJson) {
        AtsScoreBottomSheet f = new AtsScoreBottomSheet();
        Bundle args = new Bundle();
        args.putInt(ARG_RESUME_ID, resumeId);
        args.putString(ARG_CONTENT_JSON, contentJson);
        f.setArguments(args);
        return f;
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
        return inflater.inflate(R.layout.bottom_sheet_ats_scoring, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        EditText etJobDescription = view.findViewById(R.id.etJobDescription);
        LinearLayout layoutInput = view.findViewById(R.id.layoutAtsInput);
        LinearProgressIndicator loadingBar = view.findViewById(R.id.atsLoadingBar);
        LinearLayout layoutResults = view.findViewById(R.id.layoutAtsResults);

        view.findViewById(R.id.btnAnalyze).setOnClickListener(v -> {
            String jd = etJobDescription.getText().toString().trim();
            if (jd.length() < 50) {
                Toast.makeText(requireContext(), R.string.resume_ats_min_chars, Toast.LENGTH_SHORT).show();
                return;
            }

            loadingBar.setVisibility(View.VISIBLE);
            layoutResults.setVisibility(View.GONE);

            String token = TokenManager.getBearerToken(requireContext());

            // Step 1: Parse job description
            ApiClient.getService().parseJobDescription(token, new ParseJobRequest(jd))
                    .enqueue(new Callback<ParseJobResponse>() {
                        @Override
                        public void onResponse(@NonNull Call<ParseJobResponse> call,
                                               @NonNull Response<ParseJobResponse> response) {
                            if (!response.isSuccessful() || response.body() == null) {
                                loadingBar.setVisibility(View.GONE);
                                Toast.makeText(requireContext(), R.string.resume_ats_error,
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }

                            ParseJobResponse parsed = response.body();

                            // Step 2: Score ATS
                            AtsScoreRequest scoreReq = new AtsScoreRequest(
                                    parsed.getJobTitle(),
                                    parsed.getRequiredSkills(),
                                    parsed.getNiceToHaveSkills(),
                                    parsed.getKeywords());

                            ApiClient.getService().scoreAts(token, resumeId, scoreReq)
                                    .enqueue(new Callback<AtsScoreResponse>() {
                                        @Override
                                        public void onResponse(@NonNull Call<AtsScoreResponse> call2,
                                                               @NonNull Response<AtsScoreResponse> resp2) {
                                            loadingBar.setVisibility(View.GONE);
                                            if (resp2.isSuccessful() && resp2.body() != null) {
                                                showResults(view, resp2.body());
                                            } else {
                                                Toast.makeText(requireContext(),
                                                        R.string.resume_ats_error,
                                                        Toast.LENGTH_SHORT).show();
                                            }
                                        }

                                        @Override
                                        public void onFailure(@NonNull Call<AtsScoreResponse> call2,
                                                              @NonNull Throwable t) {
                                            loadingBar.setVisibility(View.GONE);
                                            Toast.makeText(requireContext(),
                                                    R.string.error_network,
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                    });
                        }

                        @Override
                        public void onFailure(@NonNull Call<ParseJobResponse> call,
                                              @NonNull Throwable t) {
                            loadingBar.setVisibility(View.GONE);
                            Toast.makeText(requireContext(), R.string.error_network,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        view.findViewById(R.id.btnClearResults).setOnClickListener(v -> {
            layoutResults.setVisibility(View.GONE);
            layoutInput.setVisibility(View.VISIBLE);
        });
    }

    private void showResults(View view, AtsScoreResponse result) {
        view.findViewById(R.id.layoutAtsResults).setVisibility(View.VISIBLE);

        // Score
        CircularProgressIndicator progressScore = view.findViewById(R.id.progressScore);
        TextView tvScoreValue = view.findViewById(R.id.tvScoreValue);
        TextView tvScoreLabel = view.findViewById(R.id.tvScoreLabel);

        progressScore.setProgress(result.getScore());
        tvScoreValue.setText(String.valueOf(result.getScore()));
        tvScoreLabel.setText(result.getScoreLabel());

        // Color based on score
        int scoreColor;
        if (result.getScore() >= 85) scoreColor = Color.parseColor("#16A34A");
        else if (result.getScore() >= 70) scoreColor = Color.parseColor("#2563EB");
        else if (result.getScore() >= 55) scoreColor = Color.parseColor("#D97706");
        else scoreColor = Color.parseColor("#EF4444");
        progressScore.setIndicatorColor(scoreColor);

        // Breakdown bars
        if (result.getBreakdown() != null) {
            ((ProgressBar) view.findViewById(R.id.barKeywords)).setProgress(result.getBreakdown().getKeywordScore());
            ((ProgressBar) view.findViewById(R.id.barTitle)).setProgress(result.getBreakdown().getTitleScore());
            ((ProgressBar) view.findViewById(R.id.barStructure)).setProgress(result.getBreakdown().getStructureScore());
        }

        // Matched keywords
        fillChipGroup(view, R.id.chipGroupMatched, result.getMatchedKeywords(), "#16A34A");

        // Missing keywords
        fillChipGroup(view, R.id.chipGroupMissingRequired, result.getMissingRequired(), "#EF4444");
        fillChipGroup(view, R.id.chipGroupMissingPreferred, result.getMissingPreferred(), "#D97706");

        // Hide empty sections
        view.findViewById(R.id.tvMissingRequiredLabel).setVisibility(
                result.getMissingRequired() != null && !result.getMissingRequired().isEmpty() ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.tvMissingPreferredLabel).setVisibility(
                result.getMissingPreferred() != null && !result.getMissingPreferred().isEmpty() ? View.VISIBLE : View.GONE);

        // Suggestions
        LinearLayout containerSuggestions = view.findViewById(R.id.containerSuggestions);
        containerSuggestions.removeAllViews();
        if (result.getSuggestions() != null) {
            for (AtsScoreResponse.AtsSuggestion sug : result.getSuggestions()) {
                TextView tv = new TextView(requireContext());
                String text = "• [" + (sug.getPriority() != null ? sug.getPriority().toUpperCase(Locale.getDefault()) : "") + "] "
                        + (sug.getText() != null ? sug.getText() : "");
                tv.setText(text);
                tv.setTextSize(13);
                tv.setTextColor(requireContext().getColor(R.color.colorTextPrimary));
                int pad = (int) (4 * getResources().getDisplayMetrics().density);
                tv.setPadding(0, pad, 0, pad);
                containerSuggestions.addView(tv);
            }
        }
    }

    private void fillChipGroup(View parent, int chipGroupId, List<String> items, String colorHex) {
        ChipGroup group = parent.findViewById(chipGroupId);
        group.removeAllViews();
        if (items == null) return;
        for (String item : items) {
            Chip chip = new Chip(requireContext());
            chip.setText(item);
            chip.setTextSize(11);
            chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(
                    Color.parseColor(colorHex + "20")));
            chip.setTextColor(Color.parseColor(colorHex));
            group.addView(chip);
        }
    }
}
