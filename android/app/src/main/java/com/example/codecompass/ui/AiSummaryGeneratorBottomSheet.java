package com.example.codecompass.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.codecompass.R;
import com.example.codecompass.api.ApiClient;
import com.example.codecompass.api.TokenManager;
import com.example.codecompass.model.GenerateSummaryRequest;
import com.example.codecompass.model.GenerateSummaryResponse;
import com.example.codecompass.model.SummaryOption;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AiSummaryGeneratorBottomSheet extends BottomSheetDialogFragment {

    public interface OnSummarySelectedListener {
        void onSummarySelected(String summary);
    }

    private static final String ARG_RESUME_ID = "resume_id";
    private static final String ARG_CURRENT_ROLE = "current_role";
    private static final String ARG_SKILLS = "skills";

    private int resumeId;
    private OnSummarySelectedListener listener;

    private static final String[] YEARS_OPTIONS = {"entry-level", "1-2 years", "3-5 years", "5+ years"};
    private static final String[] YEARS_LABELS = {"Entry-level", "1–2 years", "3–5 years", "5+ years"};

    public static AiSummaryGeneratorBottomSheet newInstance(int resumeId, String currentRole,
                                                            List<String> skills) {
        AiSummaryGeneratorBottomSheet f = new AiSummaryGeneratorBottomSheet();
        Bundle args = new Bundle();
        args.putInt(ARG_RESUME_ID, resumeId);
        args.putString(ARG_CURRENT_ROLE, currentRole != null ? currentRole : "");
        args.putStringArrayList(ARG_SKILLS, skills != null ? new ArrayList<>(skills) : new ArrayList<>());
        f.setArguments(args);
        return f;
    }

    public void setOnSummarySelectedListener(OnSummarySelectedListener listener) {
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
        return inflater.inflate(R.layout.dialog_ai_summary, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        EditText etTargetRole = view.findViewById(R.id.etSummaryTargetRole);
        EditText etStrengths = view.findViewById(R.id.etSummaryStrengths);
        AutoCompleteTextView dropdownYears = view.findViewById(R.id.dropdownYears);
        LinearProgressIndicator loadingBar = view.findViewById(R.id.summaryLoadingBar);
        LinearLayout containerResults = view.findViewById(R.id.containerSummaryResults);

        // Pre-fill
        if (getArguments() != null) {
            etTargetRole.setText(getArguments().getString(ARG_CURRENT_ROLE, ""));
            List<String> skills = getArguments().getStringArrayList(ARG_SKILLS);
            if (skills != null && !skills.isEmpty()) {
                etStrengths.setText(String.join(", ", skills.subList(0, Math.min(5, skills.size()))));
            }
        }

        // Years dropdown
        ArrayAdapter<String> yearsAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, YEARS_LABELS);
        dropdownYears.setAdapter(yearsAdapter);
        dropdownYears.setText(YEARS_LABELS[0], false);

        // Generate button — add dynamically
        MaterialButton btnGenerate = new MaterialButton(requireContext());
        btnGenerate.setText(R.string.resume_ai_summary_generate);
        btnGenerate.setTextColor(requireContext().getColor(R.color.colorTextPrimary));
        btnGenerate.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                requireContext().getColor(R.color.colorPrimary)));

        LinearLayout parent = (LinearLayout) view;
        int loadingIndex = parent.indexOfChild(loadingBar);
        parent.addView(btnGenerate, loadingIndex);

        btnGenerate.setOnClickListener(v -> {
            String targetRole = etTargetRole.getText().toString().trim();
            if (targetRole.isEmpty()) {
                Toast.makeText(requireContext(), R.string.error_field_required, Toast.LENGTH_SHORT).show();
                return;
            }

            String strengthsStr = etStrengths.getText().toString().trim();
            List<String> strengths = strengthsStr.isEmpty() ? new ArrayList<>() :
                    new ArrayList<>(Arrays.asList(strengthsStr.split("\\s*,\\s*")));

            int yearIdx = Arrays.asList(YEARS_LABELS).indexOf(dropdownYears.getText().toString());
            String yearsExp = yearIdx >= 0 ? YEARS_OPTIONS[yearIdx] : YEARS_OPTIONS[0];

            btnGenerate.setEnabled(false);
            loadingBar.setVisibility(View.VISIBLE);
            containerResults.setVisibility(View.GONE);
            containerResults.removeAllViews();

            String token = TokenManager.getBearerToken(requireContext());
            ApiClient.getService().generateSummary(token, resumeId,
                    new GenerateSummaryRequest(targetRole, strengths, yearsExp))
                    .enqueue(new Callback<GenerateSummaryResponse>() {
                        @Override
                        public void onResponse(@NonNull Call<GenerateSummaryResponse> call,
                                               @NonNull Response<GenerateSummaryResponse> response) {
                            btnGenerate.setEnabled(true);
                            loadingBar.setVisibility(View.GONE);
                            if (response.isSuccessful() && response.body() != null
                                    && response.body().getSummaries() != null) {
                                containerResults.setVisibility(View.VISIBLE);
                                for (SummaryOption opt : response.body().getSummaries()) {
                                    addSummaryResult(containerResults, opt);
                                }
                            } else {
                                Toast.makeText(requireContext(), R.string.resume_ats_error,
                                        Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<GenerateSummaryResponse> call,
                                              @NonNull Throwable t) {
                            btnGenerate.setEnabled(true);
                            loadingBar.setVisibility(View.GONE);
                            Toast.makeText(requireContext(), R.string.error_network,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        });
    }

    private void addSummaryResult(LinearLayout container, SummaryOption option) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        card.setPadding(pad, pad, pad, pad);
        card.setBackgroundResource(R.drawable.bg_template_selected);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
        card.setLayoutParams(cardParams);

        // Tone label
        TextView tvTone = new TextView(requireContext());
        tvTone.setText(option.getTone() != null ? option.getTone().toUpperCase(Locale.getDefault()) : "");
        tvTone.setTextSize(11);
        tvTone.setTextColor(requireContext().getColor(R.color.colorPrimary));
        tvTone.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(tvTone);

        // Summary text
        TextView tvText = new TextView(requireContext());
        tvText.setText(option.getText());
        tvText.setTextSize(13);
        tvText.setTextColor(requireContext().getColor(R.color.colorTextPrimary));
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        textParams.topMargin = (int) (4 * getResources().getDisplayMetrics().density);
        tvText.setLayoutParams(textParams);
        card.addView(tvText);

        // Use button
        MaterialButton btnUse = new MaterialButton(requireContext(),
                null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnUse.setText(R.string.resume_summary_use);
        btnUse.setTextSize(12);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.topMargin = (int) (6 * getResources().getDisplayMetrics().density);
        btnUse.setLayoutParams(btnParams);
        btnUse.setOnClickListener(v -> {
            if (listener != null) listener.onSummarySelected(option.getText());
            dismiss();
        });
        card.addView(btnUse);

        container.addView(card);
    }
}
