package com.example.codecompass.ui;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.codecompass.R;
import com.example.codecompass.model.CCSProgram;
import com.example.codecompass.model.University;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class UniversityDetailBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_UNIVERSITY    = "university_json";
    private static final String ARG_MATCH_SCORE   = "match_score";
    private static final String ARG_MATCH_REASONS = "match_reasons_json";

    private University university;
    private int matchScore;
    private List<String> matchReasons;

    public static UniversityDetailBottomSheet newInstance(
            University university, int matchScore, List<String> matchReasons) {
        UniversityDetailBottomSheet f = new UniversityDetailBottomSheet();
        Bundle args = new Bundle();
        Gson gson = new Gson();
        args.putString(ARG_UNIVERSITY, gson.toJson(university));
        args.putInt(ARG_MATCH_SCORE, matchScore);
        args.putString(ARG_MATCH_REASONS, gson.toJson(matchReasons));
        f.setArguments(args);
        return f;
    }

    public static UniversityDetailBottomSheet newInstance(University university) {
        return newInstance(university, -1, null);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            Gson gson = new Gson();
            university = gson.fromJson(getArguments().getString(ARG_UNIVERSITY), University.class);
            matchScore = getArguments().getInt(ARG_MATCH_SCORE, -1);
            String reasonsJson = getArguments().getString(ARG_MATCH_REASONS);
            if (reasonsJson != null && !reasonsJson.equals("null")) {
                Type listType = new TypeToken<List<String>>() {}.getType();
                matchReasons = gson.fromJson(reasonsJson, listType);
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_university_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (university == null) { dismiss(); return; }

        // Header
        TextView tvDetailTypeInitial = view.findViewById(R.id.tvDetailTypeInitial);
        TextView tvDetailName        = view.findViewById(R.id.tvDetailName);
        TextView tvDetailAbbreviation = view.findViewById(R.id.tvDetailAbbreviation);
        TextView tvDetailTypeBadge   = view.findViewById(R.id.tvDetailTypeBadge);
        TextView tvDetailChedCoe     = view.findViewById(R.id.tvDetailChedCoe);
        TextView tvDetailChedCod     = view.findViewById(R.id.tvDetailChedCod);

        // Match score
        LinearLayout layoutMatchScore    = view.findViewById(R.id.layoutMatchScore);
        ProgressBar  progressMatchScore  = view.findViewById(R.id.progressMatchScore);
        TextView     tvDetailMatchScore  = view.findViewById(R.id.tvDetailMatchScore);
        LinearLayout containerMatchReasons = view.findViewById(R.id.containerMatchReasons);
        View         dividerMatchSection = view.findViewById(R.id.dividerMatchSection);

        // Location, stats
        TextView tvDetailLocation      = view.findViewById(R.id.tvDetailLocation);
        TextView tvDetailAccreditation = view.findViewById(R.id.tvDetailAccreditation);
        TextView tvDetailTuition       = view.findViewById(R.id.tvDetailTuition);

        // Programs + website
        LinearLayout containerPrograms = view.findViewById(R.id.containerPrograms);
        MaterialButton btnVisitWebsite = view.findViewById(R.id.btnVisitWebsite);

        // --- Bind header ---
        String type = university.getUniversityType();
        String initial = "U";
        int circleColor = Color.parseColor("#6B7280");
        if ("state".equals(type)) {
            initial = "S";
            circleColor = Color.parseColor("#16A34A");
        } else if ("private".equals(type)) {
            initial = "P";
            circleColor = Color.parseColor("#7C3AED");
        } else if ("local".equals(type)) {
            initial = "L";
            circleColor = Color.parseColor("#2563EB");
        }
        tvDetailTypeInitial.setText(initial);
        tvDetailTypeInitial.getBackground().mutate().setTint(circleColor);

        tvDetailName.setText(university.getName());

        String abbr = university.getAbbreviation();
        if (abbr != null && !abbr.isEmpty()) {
            tvDetailAbbreviation.setText(abbr);
            tvDetailAbbreviation.setVisibility(View.VISIBLE);
        } else {
            tvDetailAbbreviation.setVisibility(View.GONE);
        }

        tvDetailTypeBadge.setText(getTypeLabel(type));
        if ("state".equals(type)) {
            tvDetailTypeBadge.setBackgroundResource(R.drawable.bg_badge_green);
        } else {
            tvDetailTypeBadge.setBackgroundResource(R.drawable.bg_badge_gray);
        }

        tvDetailChedCoe.setVisibility(university.isChedCoe() ? View.VISIBLE : View.GONE);
        tvDetailChedCod.setVisibility(university.isChedCod() ? View.VISIBLE : View.GONE);

        // --- Match score section ---
        if (matchScore >= 0) {
            layoutMatchScore.setVisibility(View.VISIBLE);
            dividerMatchSection.setVisibility(View.VISIBLE);
            progressMatchScore.setProgress(matchScore);
            tvDetailMatchScore.setText(getString(R.string.uni_match_score_format, matchScore));

            if (matchReasons != null && !matchReasons.isEmpty() && getContext() != null) {
                for (String reason : matchReasons) {
                    TextView tv = new TextView(getContext());
                    tv.setText(getString(R.string.uni_match_reason_format, reason));
                    tv.setTextColor(Color.parseColor("#6B7280"));
                    tv.setTextSize(12f);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    lp.topMargin = dpToPx(2);
                    containerMatchReasons.addView(tv, lp);
                }
            }
        } else {
            layoutMatchScore.setVisibility(View.GONE);
            dividerMatchSection.setVisibility(View.GONE);
        }

        // --- Location ---
        tvDetailLocation.setText(getString(R.string.uni_location_format, university.getCity(), university.getRegion()));

        // --- Stats ---
        int level = university.getAccreditationLevel();
        tvDetailAccreditation.setText(level > 0 ? getString(R.string.uni_accreditation_level, level) : getString(R.string.uni_accreditation_na));
        tvDetailTuition.setText(formatTuition(university));

        // --- Website button ---
        String website = university.getWebsiteUrl();
        if (website != null && !website.isEmpty()) {
            btnVisitWebsite.setVisibility(View.VISIBLE);
            btnVisitWebsite.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(website));
                startActivity(intent);
            });
        } else {
            btnVisitWebsite.setVisibility(View.GONE);
        }

        // --- Programs ---
        List<CCSProgram> programs = university.getPrograms();
        if (programs != null && !programs.isEmpty() && getContext() != null) {
            for (CCSProgram program : programs) {
                containerPrograms.addView(buildProgramRow(program));
            }
        }
    }

    private View buildProgramRow(CCSProgram program) {
        if (getContext() == null) return new View(getContext());

        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundResource(R.drawable.bg_stat_card);
        int pad = dpToPx(12);
        row.setPadding(pad, pad, pad, pad);

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowLp.bottomMargin = dpToPx(8);
        row.setLayoutParams(rowLp);

        // Row 1: name + board exam badge
        LinearLayout row1 = new LinearLayout(getContext());
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView tvProgramName = new TextView(getContext());
        tvProgramName.setText(program.getName());
        tvProgramName.setTextSize(13f);
        tvProgramName.setTypeface(null, Typeface.BOLD);
        tvProgramName.setTextColor(Color.parseColor("#111827"));
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        );
        tvProgramName.setLayoutParams(nameLp);
        row1.addView(tvProgramName);

        if (program.hasBoardExam()) {
            TextView tvBoard = new TextView(getContext());
            tvBoard.setText(R.string.uni_board_exam);
            tvBoard.setBackgroundResource(R.drawable.bg_badge_green);
            int p = dpToPx(4);
            tvBoard.setPadding(p * 2, p, p * 2, p);
            tvBoard.setTextSize(10f);
            tvBoard.setTextColor(Color.parseColor("#166534"));
            LinearLayout.LayoutParams boardLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            boardLp.setMarginStart(dpToPx(6));
            tvBoard.setLayoutParams(boardLp);
            row1.addView(tvBoard);
        }
        row.addView(row1);

        // Row 2: abbreviation + duration
        LinearLayout row2 = new LinearLayout(getContext());
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams row2Lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        row2Lp.topMargin = dpToPx(4);
        row2.setLayoutParams(row2Lp);

        TextView tvAbbr = new TextView(getContext());
        tvAbbr.setText(program.getAbbreviation());
        tvAbbr.setBackgroundResource(R.drawable.bg_badge_gray);
        int p2 = dpToPx(4);
        tvAbbr.setPadding(p2 * 2, p2, p2 * 2, p2);
        tvAbbr.setTextSize(10f);
        tvAbbr.setTextColor(Color.parseColor("#4B5563"));
        LinearLayout.LayoutParams abbrLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        abbrLp.setMarginEnd(dpToPx(6));
        tvAbbr.setLayoutParams(abbrLp);
        row2.addView(tvAbbr);

        TextView tvDuration = new TextView(getContext());
        tvDuration.setText(getString(R.string.uni_duration_years_format, program.getDurationYears()));
        tvDuration.setTextSize(11f);
        tvDuration.setTextColor(Color.parseColor("#6B7280"));
        row2.addView(tvDuration);
        row.addView(row2);

        // Row 3: specializations (if any)
        List<String> specs = program.getSpecializations();
        if (specs != null && !specs.isEmpty()) {
            TextView tvSpecs = new TextView(getContext());
            tvSpecs.setText(getString(R.string.uni_specializations_format, joinList(specs)));
            tvSpecs.setTextSize(11f);
            tvSpecs.setTextColor(Color.parseColor("#6B7280"));
            LinearLayout.LayoutParams specsLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            specsLp.topMargin = dpToPx(4);
            tvSpecs.setLayoutParams(specsLp);
            row.addView(tvSpecs);
        }

        // Row 4: curriculum link (if any)
        String curriculumUrl = program.getCurriculumUrl();
        if (curriculumUrl != null && !curriculumUrl.isEmpty()) {
            MaterialButton btnCurriculum = new MaterialButton(
                    new android.view.ContextThemeWrapper(getContext(),
                            com.google.android.material.R.style.Widget_MaterialComponents_Button),
                    null, 0);
            btnCurriculum.setText(R.string.uni_view_curriculum);
            btnCurriculum.setTextSize(12f);
            btnCurriculum.setTextColor(Color.WHITE);
            btnCurriculum.setBackgroundTintList(android.content.res.ColorStateList.valueOf(requireContext().getColor(R.color.colorPrimary)));
            btnCurriculum.setPadding(dpToPx(12), dpToPx(4), dpToPx(12), dpToPx(4));
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            btnLp.topMargin = dpToPx(4);
            btnCurriculum.setLayoutParams(btnLp);
            btnCurriculum.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(curriculumUrl));
                startActivity(intent);
            });
            row.addView(btnCurriculum);
        }

        return row;
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
        if (min == null && max == null) return "N/A";
        if (min != null && min == 0) return "Free / Minimal";
        NumberFormat fmt = NumberFormat.getNumberInstance(Locale.US);
        if (min != null && max != null) {
            return "₱" + fmt.format(min) + "–₱" + fmt.format(max) + "/yr";
        }
        if (max != null) return "Up to ₱" + fmt.format(max) + "/yr";
        return "₱" + fmt.format(min) + "/yr";
    }

    private String joinList(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    private int dpToPx(int dp) {
        if (getContext() == null) return dp;
        float density = getContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
