package com.example.codecompass.ui;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.codecompass.R;
import com.example.codecompass.model.roadmap.AssessmentQuestion;
import com.example.codecompass.model.roadmap.AssessmentResponse;
import com.example.codecompass.model.roadmap.QuizResult;
import com.example.codecompass.model.roadmap.QuizResultItem;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class QuizBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "QuizBottomSheet";
    private static final String ARG_ASSESSMENT  = "assessment";
    private static final String ARG_NODE_ID     = "node_id";
    private static final String ARG_RESOURCE_ID = "resource_id";

    // ── Callbacks ─────────────────────────────────────────────────────────────

    public interface OnQuizSubmitListener {
        void onSubmit(int nodeId, int resourceId, int sessionId, Map<String, String> answers);
    }

    public interface OnQuizPassedListener {
        void onQuizPassed(int resourceId);
    }

    private OnQuizSubmitListener submitListener;
    private OnQuizPassedListener passedListener;

    public void setOnQuizSubmitListener(OnQuizSubmitListener l)  { submitListener = l; }
    public void setOnQuizPassedListener(OnQuizPassedListener l)  { passedListener = l; }

    // ── Factory ───────────────────────────────────────────────────────────────

    public static QuizBottomSheet newInstance(AssessmentResponse assessment,
                                              int nodeId, int resourceId) {
        QuizBottomSheet sheet = new QuizBottomSheet();
        Bundle args = new Bundle();
        args.putSerializable(ARG_ASSESSMENT, assessment);
        args.putInt(ARG_NODE_ID, nodeId);
        args.putInt(ARG_RESOURCE_ID, resourceId);
        sheet.setArguments(args);
        return sheet;
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private AssessmentResponse assessment;
    private int nodeId;
    private int resourceId;

    /**
     * Maps question index (0-based) → selected option letter ("a", "b", "c", "d").
     * Matches the backend submit format: {"0": "a", "1": "c", ...}
     */
    private final Map<Integer, String> selectedAnswers = new HashMap<>();

    /**
     * RadioGroups per question — used to read selections and apply result styles.
     * Each RadioButton's tag is the letter string ("a", "b", "c", "d").
     */
    private RadioGroup[] radioGroups;

    // ── Views ─────────────────────────────────────────────────────────────────

    private LinearLayout layoutGenerating;
    private LinearLayout layoutQuestions;
    private Button btnSubmit;
    private LinearLayout layoutResult;
    private TextView tvResultIcon;
    private TextView tvResultTitle;
    private TextView tvResultScore;
    private TextView tvPassThreshold;
    private Button btnTryAgain;
    private Button btnToggleBreakdown;
    private LinearLayout layoutBreakdown;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_quiz, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() == null) { dismiss(); return; }
        assessment = (AssessmentResponse) getArguments().getSerializable(ARG_ASSESSMENT);
        nodeId     = getArguments().getInt(ARG_NODE_ID);
        resourceId = getArguments().getInt(ARG_RESOURCE_ID);
        if (assessment == null) { dismiss(); return; }

        bindViews(view);
        buildQuestions();
    }

    private void bindViews(View root) {
        layoutGenerating  = root.findViewById(R.id.layoutQuizGenerating);
        layoutQuestions   = root.findViewById(R.id.layoutQuizQuestions);
        btnSubmit         = root.findViewById(R.id.btnSubmitQuiz);
        layoutResult      = root.findViewById(R.id.layoutQuizResult);
        tvResultIcon      = root.findViewById(R.id.tvResultIcon);
        tvResultTitle     = root.findViewById(R.id.tvResultTitle);
        tvResultScore     = root.findViewById(R.id.tvResultScore);
        tvPassThreshold   = root.findViewById(R.id.tvPassThreshold);
        btnTryAgain       = root.findViewById(R.id.btnTryAgain);
        btnToggleBreakdown= root.findViewById(R.id.btnToggleBreakdown);
        layoutBreakdown   = root.findViewById(R.id.layoutBreakdown);
    }

    // ── Build question UI ─────────────────────────────────────────────────────

    private void buildQuestions() {
        layoutGenerating.setVisibility(View.GONE);

        List<AssessmentQuestion> questions = assessment.getQuestions();
        if (questions == null || questions.isEmpty()) {
            dismiss();
            return;
        }

        selectedAnswers.clear();
        radioGroups = new RadioGroup[questions.size()];
        layoutQuestions.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());

        for (int qi = 0; qi < questions.size(); qi++) {
            AssessmentQuestion q = questions.get(qi);
            final int questionIndex = qi;

            // Question container
            LinearLayout qBlock = new LinearLayout(requireContext());
            qBlock.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams blockParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            blockParams.setMargins(0, 0, 0, dpToPx(20));
            qBlock.setLayoutParams(blockParams);

            // Question text
            TextView tvQuestion = new TextView(requireContext());
            tvQuestion.setText(getString(R.string.quiz_question_numbered, qi + 1, q.getQuestion()));
            tvQuestion.setTextSize(14f);
            tvQuestion.setTextColor(requireContext().getColor(R.color.colorTextPrimary));
            tvQuestion.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams tvParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            tvParams.setMargins(0, 0, 0, dpToPx(10));
            tvQuestion.setLayoutParams(tvParams);
            qBlock.addView(tvQuestion);

            // RadioGroup for options
            RadioGroup rg = new RadioGroup(requireContext());
            rg.setOrientation(RadioGroup.VERTICAL);
            radioGroups[qi] = rg;

            if (q.getOptions() != null && !q.getOptions().isEmpty()) {
                // Sort by key to guarantee a → b → c → d order
                List<Map.Entry<String, String>> entries = new ArrayList<>(q.getOptions().entrySet());
                Collections.sort(entries, Map.Entry.comparingByKey());

                for (Map.Entry<String, String> entry : entries) {
                    final String letter = entry.getKey(); // "a", "b", "c", "d"
                    RadioButton rb = new RadioButton(requireContext());
                    rb.setText(getString(R.string.quiz_option_format,
                            letter.toUpperCase(java.util.Locale.getDefault()), entry.getValue()));
                    rb.setTextSize(13f);
                    rb.setTextColor(requireContext().getColor(R.color.colorTextPrimary));
                    rb.setButtonTintList(ColorStateList.valueOf(
                            requireContext().getColor(R.color.colorPrimary)));
                    LinearLayout.LayoutParams rbParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    rbParams.setMargins(0, 0, 0, dpToPx(6));
                    rb.setLayoutParams(rbParams);
                    rb.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
                    rb.setBackground(androidx.appcompat.content.res.AppCompatResources.getDrawable(requireContext(), R.drawable.bg_quiz_option_normal));
                    rb.setTag(letter); // tag is the letter key, not an index
                    rg.addView(rb);
                }
            }

            rg.setOnCheckedChangeListener((group, checkedId) -> {
                RadioButton checked = group.findViewById(checkedId);
                if (checked != null) {
                    String letter = (String) checked.getTag();
                    selectedAnswers.put(questionIndex, letter);
                    applySelectedStyle(group, checkedId);
                    checkAllAnswered();
                }
            });

            qBlock.addView(rg);
            layoutQuestions.addView(qBlock);
        }

        layoutQuestions.setVisibility(View.VISIBLE);
        btnSubmit.setVisibility(View.VISIBLE);
        btnSubmit.setOnClickListener(v -> submitQuiz());
    }

    private void applySelectedStyle(RadioGroup group, int selectedId) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (!(child instanceof RadioButton)) continue;
            RadioButton rb = (RadioButton) child;
            if (rb.getId() == selectedId) {
                rb.setBackground(androidx.appcompat.content.res.AppCompatResources.getDrawable(requireContext(), R.drawable.bg_quiz_option_selected));
            } else {
                rb.setBackground(androidx.appcompat.content.res.AppCompatResources.getDrawable(requireContext(), R.drawable.bg_quiz_option_normal));
            }
        }
    }

    private void checkAllAnswered() {
        List<AssessmentQuestion> questions = assessment.getQuestions();
        if (questions == null) return;
        boolean allAnswered = selectedAnswers.size() == questions.size();
        btnSubmit.setEnabled(allAnswered);
        btnSubmit.setAlpha(allAnswered ? 1f : 0.5f);
    }

    // ── Submit ────────────────────────────────────────────────────────────────

    private void submitQuiz() {
        btnSubmit.setEnabled(false);
        btnSubmit.setAlpha(0.5f);

        // Build Map<String, String>: question index string → letter answer
        Map<String, String> answersMap = new HashMap<>();
        for (Map.Entry<Integer, String> entry : selectedAnswers.entrySet()) {
            answersMap.put(String.valueOf(entry.getKey()), entry.getValue());
        }

        if (submitListener != null) {
            submitListener.onSubmit(nodeId, resourceId, assessment.getSessionId(), answersMap);
        }
    }

    /**
     * Called externally (from RoadmapActivity) after the quiz result LiveData fires.
     */
    public void showResult(QuizResult result) {
        if (result == null || !isAdded()) return;

        // Lock all radio buttons and apply result styling
        if (radioGroups != null) {
            for (int qi = 0; qi < radioGroups.length; qi++) {
                RadioGroup rg = radioGroups[qi];
                if (rg == null) continue;
                applyResultStyle(rg, result, qi);
            }
        }

        btnSubmit.setVisibility(View.GONE);
        layoutResult.setVisibility(View.VISIBLE);
        layoutResult.setBackground(null);

        boolean passed = result.isPassed();

        if (passed) {
            tvResultIcon.setText("✓");
            tvResultIcon.setTextColor(requireContext().getColor(R.color.quizPassText));
            tvResultTitle.setText(getString(R.string.quiz_passed));
            tvResultTitle.setTextColor(requireContext().getColor(R.color.quizPassText));
            btnTryAgain.setVisibility(View.GONE);

            // Notify parent that this resource quiz was passed
            if (passedListener != null) {
                passedListener.onQuizPassed(resourceId);
            }
        } else {
            tvResultIcon.setText("✗");
            tvResultIcon.setTextColor(requireContext().getColor(R.color.quizFailText));
            tvResultTitle.setText(getString(R.string.quiz_failed));
            tvResultTitle.setTextColor(requireContext().getColor(R.color.quizFailText));
            btnTryAgain.setVisibility(View.VISIBLE);
            btnTryAgain.setOnClickListener(v -> {
                // Reset for retry
                selectedAnswers.clear();
                layoutResult.setVisibility(View.GONE);
                btnSubmit.setEnabled(false);
                btnSubmit.setAlpha(0.5f);
                buildQuestions();
            });
        }

        tvResultScore.setText(String.format(Locale.getDefault(),
                "%d/%d correct (%d%%)",
                result.getCorrectCount(), result.getTotalQuestions(), result.getScore()));

        if (result.getPassThreshold() > 0) {
            tvPassThreshold.setText(String.format(Locale.getDefault(),
                    "Pass threshold: %d%%", result.getPassThreshold()));
            tvPassThreshold.setVisibility(View.VISIBLE);
        }

        // Breakdown toggle
        btnToggleBreakdown.setOnClickListener(v -> {
            if (layoutBreakdown.getVisibility() == View.VISIBLE) {
                layoutBreakdown.setVisibility(View.GONE);
                btnToggleBreakdown.setText(getString(R.string.quiz_breakdown));
            } else {
                populateBreakdown(result);
                layoutBreakdown.setVisibility(View.VISIBLE);
                btnToggleBreakdown.setText(R.string.quiz_hide_breakdown);
            }
        });
    }

    // ── Result option styling ─────────────────────────────────────────────────

    private void applyResultStyle(RadioGroup group, QuizResult result, int qi) {
        QuizResultItem item = (result.getResults() != null && qi < result.getResults().size())
                ? result.getResults().get(qi) : null;

        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (!(child instanceof RadioButton)) continue;
            RadioButton rb = (RadioButton) child;
            rb.setEnabled(false); // lock after submission

            if (item == null) continue;
            String tagLetter = (String) rb.getTag();
            if (tagLetter == null) continue;

            if (tagLetter.equals(item.getCorrectAnswer())) {
                rb.setBackground(androidx.appcompat.content.res.AppCompatResources.getDrawable(requireContext(), R.drawable.bg_quiz_option_correct));
            } else if (tagLetter.equals(item.getYourAnswer()) && !item.isCorrect()) {
                rb.setBackground(androidx.appcompat.content.res.AppCompatResources.getDrawable(requireContext(), R.drawable.bg_quiz_option_wrong));
            } else {
                rb.setBackground(androidx.appcompat.content.res.AppCompatResources.getDrawable(requireContext(), R.drawable.bg_quiz_option_normal));
            }
        }
    }

    // ── Breakdown ─────────────────────────────────────────────────────────────

    private void populateBreakdown(QuizResult result) {
        layoutBreakdown.removeAllViews();
        if (result.getResults() == null) return;

        List<AssessmentQuestion> questions = assessment.getQuestions();

        for (int i = 0; i < result.getResults().size(); i++) {
            QuizResultItem item = result.getResults().get(i);
            AssessmentQuestion q = (questions != null && i < questions.size()) ? questions.get(i) : null;

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.VERTICAL);
            row.setBackgroundResource(item.isCorrect()
                    ? R.drawable.bg_quiz_option_correct
                    : R.drawable.bg_quiz_option_wrong);
            row.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.setMargins(0, 0, 0, dpToPx(8));
            row.setLayoutParams(rowParams);

            // Q label + icon
            String icon = item.isCorrect() ? "✓" : "✗";
            String qLabel = icon + " Q" + (i + 1);
            if (q != null) qLabel += ": " + q.getQuestion();
            TextView tvQLabel = new TextView(requireContext());
            tvQLabel.setText(qLabel);
            tvQLabel.setTextSize(13f);
            tvQLabel.setTextColor(requireContext().getColor(
                    item.isCorrect() ? R.color.quizPassText : R.color.quizFailText));
            tvQLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            row.addView(tvQLabel);

            // Your answer / correct answer (if wrong)
            if (!item.isCorrect() && item.getCorrectAnswer() != null) {
                String answerLine = "Your answer: " + item.getYourAnswer().toUpperCase(java.util.Locale.getDefault())
                        + " · Correct: " + item.getCorrectAnswer().toUpperCase(java.util.Locale.getDefault());
                TextView tvAnswer = new TextView(requireContext());
                tvAnswer.setText(answerLine);
                tvAnswer.setTextSize(12f);
                tvAnswer.setTextColor(requireContext().getColor(R.color.colorTextSecondary));
                LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                ap.setMargins(0, dpToPx(4), 0, 0);
                tvAnswer.setLayoutParams(ap);
                row.addView(tvAnswer);
            }

            // Explanation
            if (item.getExplanation() != null && !item.getExplanation().isEmpty()) {
                TextView tvExp = new TextView(requireContext());
                tvExp.setText(getString(R.string.quiz_explanation_bullet_format, item.getExplanation()));
                tvExp.setTextSize(12f);
                tvExp.setTextColor(requireContext().getColor(R.color.colorTextSecondary));
                LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                ep.setMargins(0, dpToPx(4), 0, 0);
                tvExp.setLayoutParams(ep);
                row.addView(tvExp);
            }

            layoutBreakdown.addView(row);
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private int dpToPx(int dp) {
        return (int) (dp * requireContext().getResources().getDisplayMetrics().density);
    }
}
