package com.example.codecompass.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;

import com.example.codecompass.R;
import com.example.codecompass.model.roadmap.AssessmentQuestion;
import com.example.codecompass.model.roadmap.AssessmentResponse;
import com.example.codecompass.model.roadmap.QuizResult;
import com.example.codecompass.model.roadmap.QuizResultItem;
import com.example.codecompass.viewmodel.RoadmapViewModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class QuizActivity extends AppCompatActivity {

    // ── Intent extras / result keys ───────────────────────────────────────────
    public static final String EXTRA_ROADMAP_ID   = "roadmapId";
    public static final String EXTRA_NODE_ID      = "nodeId";
    public static final String EXTRA_RESOURCE_ID  = "resourceId";
    public static final String EXTRA_VIDEO_TITLE  = "videoTitle";
    public static final String EXTRA_MODE         = "mode";
    public static final String MODE_VIDEO         = "video";
    public static final String MODE_FINAL         = "final";
    public static final String RESULT_RESOURCE_ID  = "resourceId";
    public static final String RESULT_FINAL_PASSED = "finalAssessmentPassed";
    public static final String EXTRA_ROADMAP_TITLE = "roadmapTitle";
    public static final String EXTRA_CAREER_PATH   = "careerPath";

    // ── Option key order ──────────────────────────────────────────────────────
    private static final String[] KEYS = {"a", "b", "c", "d"};

    // ── Views — loading ───────────────────────────────────────────────────────
    private LinearLayout layoutLoading;
    private TextView     tvLoadingLabel;

    // ── Views — active quiz ───────────────────────────────────────────────────
    private LinearLayout layoutActiveQuiz;
    private TextView     tvQuestionNumber;
    private TextView     tvLeaveQuiz;
    private ProgressBar  progressQuestion;
    private ProgressBar  progressTimer;
    private TextView     tvTimer;
    private TextView     tvQuestion;

    private final LinearLayout[] optionLayouts   = new LinearLayout[4];
    private final TextView[]     optionKeyViews  = new TextView[4];
    private final TextView[]     optionTextViews = new TextView[4];

    private Button btnNextOrSubmit;

    // ── Views — overview ─────────────────────────────────────────────────────
    private LinearLayout   layoutOverview;
    private TextView       tvOverviewSubtitle;
    private MaterialButton btnBeginAssessment;
    private TextView       tvOverviewBack;

    // ── Views — results ───────────────────────────────────────────────────────
    private ScrollView   scrollResults;
    private LinearLayout layoutResultBanner;
    private TextView     tvResultIcon;
    private TextView     tvResultTitle;
    private TextView     tvResultScore;
    private LinearLayout containerBreakdown;
    private Button       btnBackToLesson;
    private Button       btnTryAgain;
    private Button       btnBackToLessonFail;

    // ── State ─────────────────────────────────────────────────────────────────
    private RoadmapViewModel        viewModel;
    private int                     roadmapId;
    private int                     nodeId;
    private int                     resourceId;
    private String                  mode         = MODE_VIDEO;
    private String                  roadmapTitle = "";
    private String                  careerPath   = "";
    private List<AssessmentQuestion> questions    = new ArrayList<>();
    private int                     sessionId;
    private int                     currentIndex;
    private String                  selectedAnswer;
    private final Map<String, String> answers     = new HashMap<>();
    private CountDownTimer          countDownTimer;
    private boolean                 isSubmitting = false;

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz);

        roadmapId  = getIntent().getIntExtra(EXTRA_ROADMAP_ID, -1);
        nodeId     = getIntent().getIntExtra(EXTRA_NODE_ID, -1);
        resourceId = getIntent().getIntExtra(EXTRA_RESOURCE_ID, -1);
        String modeExtra = getIntent().getStringExtra(EXTRA_MODE);
        if (MODE_FINAL.equals(modeExtra)) mode = MODE_FINAL;

        String titleExtra = getIntent().getStringExtra(EXTRA_ROADMAP_TITLE);
        if (titleExtra != null) roadmapTitle = titleExtra;
        String pathExtra = getIntent().getStringExtra(EXTRA_CAREER_PATH);
        if (pathExtra != null) careerPath = pathExtra;

        bindViews();

        btnBeginAssessment.setOnClickListener(v -> {
            showLoading(getString(R.string.quiz_generating_full));
            viewModel.generateFinalAssessment();
        });
        tvOverviewBack.setOnClickListener(v -> finish());

        viewModel = new ViewModelProvider(this).get(RoadmapViewModel.class);
        viewModel.setRoadmapId(roadmapId);
        viewModel.clearQuizEvents();

        observeEvents();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishWithCancel();
            }
        });

        showLoading(getString(R.string.quiz_generating_full));
        startQuizGeneration();
    }

    private void startQuizGeneration() {
        if (MODE_FINAL.equals(mode)) {
            showOverview();
        } else {
            viewModel.generateQuiz(nodeId, resourceId);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelTimer();
    }

    // ── View binding ──────────────────────────────────────────────────────────

    private void bindViews() {
        layoutLoading    = findViewById(R.id.layoutLoading);
        tvLoadingLabel   = findViewById(R.id.tvLoadingLabel);
        layoutActiveQuiz = findViewById(R.id.layoutActiveQuiz);
        tvQuestionNumber = findViewById(R.id.tvQuestionNumber);
        tvLeaveQuiz      = findViewById(R.id.tvLeaveQuiz);
        progressQuestion = findViewById(R.id.progressQuestion);
        progressTimer    = findViewById(R.id.progressTimer);
        tvTimer          = findViewById(R.id.tvTimer);
        tvQuestion       = findViewById(R.id.tvQuestion);

        optionLayouts[0]   = findViewById(R.id.layoutOptionA);
        optionLayouts[1]   = findViewById(R.id.layoutOptionB);
        optionLayouts[2]   = findViewById(R.id.layoutOptionC);
        optionLayouts[3]   = findViewById(R.id.layoutOptionD);
        optionKeyViews[0]  = findViewById(R.id.tvOptionAKey);
        optionKeyViews[1]  = findViewById(R.id.tvOptionBKey);
        optionKeyViews[2]  = findViewById(R.id.tvOptionCKey);
        optionKeyViews[3]  = findViewById(R.id.tvOptionDKey);
        optionTextViews[0] = findViewById(R.id.tvOptionAText);
        optionTextViews[1] = findViewById(R.id.tvOptionBText);
        optionTextViews[2] = findViewById(R.id.tvOptionCText);
        optionTextViews[3] = findViewById(R.id.tvOptionDText);

        btnNextOrSubmit     = findViewById(R.id.btnNextOrSubmit);

        layoutOverview     = findViewById(R.id.layoutOverview);
        tvOverviewSubtitle = findViewById(R.id.tvOverviewSubtitle);
        btnBeginAssessment = findViewById(R.id.btnBeginAssessment);
        tvOverviewBack     = findViewById(R.id.tvOverviewBack);

        scrollResults       = findViewById(R.id.scrollResults);
        layoutResultBanner  = findViewById(R.id.layoutResultBanner);
        tvResultIcon        = findViewById(R.id.tvResultIcon);
        tvResultTitle       = findViewById(R.id.tvResultTitle);
        tvResultScore       = findViewById(R.id.tvResultScore);
        containerBreakdown  = findViewById(R.id.containerBreakdown);
        btnBackToLesson     = findViewById(R.id.btnBackToLesson);
        btnTryAgain         = findViewById(R.id.btnTryAgain);
        btnBackToLessonFail = findViewById(R.id.btnBackToLessonFail);
    }

    // ── LiveData observation ──────────────────────────────────────────────────

    private void observeEvents() {
        viewModel.getQuizReadyEvent().observe(this, response -> {
            if (response == null) return;
            onQuestionsReady(response);
        });

        viewModel.getQuizResultEvent().observe(this, result -> {
            if (result == null) return;
            isSubmitting = false;
            onResultReceived(result);
        });

        viewModel.getError().observe(this, error -> {
            if (error == null) return;
            isSubmitting = false;
            tvLoadingLabel.setText(error);
            showLoading(error);
        });
    }

    // ── State transitions ─────────────────────────────────────────────────────

    private void showOverview() {
        String subtitle = roadmapTitle.isEmpty()
                ? careerPath
                : careerPath.isEmpty() ? roadmapTitle : roadmapTitle + " · " + careerPath;
        tvOverviewSubtitle.setText(subtitle);
        layoutOverview.setVisibility(View.VISIBLE);
        layoutLoading.setVisibility(View.GONE);
        layoutActiveQuiz.setVisibility(View.GONE);
        scrollResults.setVisibility(View.GONE);
    }

    private void showLoading(String message) {
        tvLoadingLabel.setText(message);
        layoutLoading.setVisibility(View.VISIBLE);
        layoutOverview.setVisibility(View.GONE);
        layoutActiveQuiz.setVisibility(View.GONE);
        scrollResults.setVisibility(View.GONE);
    }

    private void showQuiz() {
        layoutLoading.setVisibility(View.GONE);
        layoutOverview.setVisibility(View.GONE);
        layoutActiveQuiz.setVisibility(View.VISIBLE);
        scrollResults.setVisibility(View.GONE);
    }

    private void showResults() {
        layoutLoading.setVisibility(View.GONE);
        layoutOverview.setVisibility(View.GONE);
        layoutActiveQuiz.setVisibility(View.GONE);
        scrollResults.setVisibility(View.VISIBLE);
    }

    // ── Quiz setup ────────────────────────────────────────────────────────────

    private void onQuestionsReady(AssessmentResponse response) {
        sessionId    = response.getSessionId();
        questions    = response.getQuestions() != null ? response.getQuestions() : new ArrayList<>();
        currentIndex = 0;
        selectedAnswer = null;
        answers.clear();

        // Attach click listeners once questions are ready
        for (int i = 0; i < 4; i++) {
            final String key = KEYS[i];
            optionLayouts[i].setOnClickListener(v -> handleOptionSelected(key));
        }
        btnNextOrSubmit.setOnClickListener(v -> handleNext());
        tvLeaveQuiz.setOnClickListener(v -> finishWithCancel());

        showQuiz();
        renderQuestion(0);
        startTimer();
    }

    // ── Question rendering ────────────────────────────────────────────────────

    private void renderQuestion(int index) {
        if (questions == null || index >= questions.size()) return;

        AssessmentQuestion q = questions.get(index);
        if (q == null) return;
        int total = questions.size();

        tvQuestionNumber.setText(getString(R.string.quiz_question_of, index + 1, total));
        progressQuestion.setProgress((index * 100) / total);
        tvQuestion.setText(q.getQuestion() != null ? q.getQuestion() : "");

        Map<String, String> opts = q.getOptions();
        for (int i = 0; i < 4; i++) {
            String text = opts != null ? opts.get(KEYS[i]) : null;
            optionTextViews[i].setText(text != null ? text : "");
            resetOptionStyle(i);
        }

        selectedAnswer = null;
        btnNextOrSubmit.setEnabled(false);
        btnNextOrSubmit.setAlpha(0.4f);
        btnNextOrSubmit.setText(index == total - 1
                ? getString(R.string.quiz_submit_final)
                : getString(R.string.quiz_next));
    }

    private void resetOptionStyle(int i) {
        optionLayouts[i].setBackgroundResource(R.drawable.bg_quiz_option_normal);
        optionKeyViews[i].setBackgroundResource(R.drawable.bg_circle_gray);
        optionKeyViews[i].setBackgroundTintList(null);
        optionKeyViews[i].setTextColor(getColor(R.color.colorTextSecondary));
    }

    // ── Timer ─────────────────────────────────────────────────────────────────

    private void startTimer() {
        cancelTimer();
        progressTimer.setMax(100);
        progressTimer.setProgress(100);
        tvTimer.setText(getString(R.string.quiz_timer_seconds_format, 15));
        updateTimerUi(15);

        countDownTimer = new CountDownTimer(15_000, 1_000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secs = (int) (millisUntilFinished / 1000);
                tvTimer.setText(getString(R.string.quiz_timer_seconds_format, secs));
                progressTimer.setProgress((secs * 100) / 15);
                updateTimerUi(secs);
            }

            @Override
            public void onFinish() {
                tvTimer.setText(getString(R.string.quiz_timer_seconds_format, 0));
                progressTimer.setProgress(0);
                updateTimerUi(0);
                autoAdvance();
            }
        }.start();
    }

    private void cancelTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }

    private void updateTimerUi(int secs) {
        int color;
        if (secs >= 8) {
            color = getColor(R.color.colorOnlineGreen);
        } else if (secs >= 4) {
            color = getColor(R.color.colorPrimary);
        } else {
            color = getColor(R.color.colorDanger);
        }
        tvTimer.setTextColor(color);
        progressTimer.setProgressTintList(ColorStateList.valueOf(color));
    }

    // ── Answer selection ──────────────────────────────────────────────────────

    private void handleOptionSelected(String key) {
        selectedAnswer = key;

        // Reset all to normal first
        for (int i = 0; i < 4; i++) {
            resetOptionStyle(i);
        }

        // Apply selected style to chosen option
        int idx = keyToIndex(key);
        if (idx >= 0) {
            optionLayouts[idx].setBackgroundResource(R.drawable.bg_quiz_option_selected);
            optionKeyViews[idx].setBackgroundTintList(
                    ColorStateList.valueOf(getColor(R.color.colorPrimary)));
            optionKeyViews[idx].setTextColor(getColor(R.color.colorOnPrimary));
        }

        btnNextOrSubmit.setEnabled(true);
        btnNextOrSubmit.setAlpha(1.0f);
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private void handleNext() {
        if (isSubmitting) return;
        cancelTimer();
        // Only record if user actually picked an answer
        if (selectedAnswer != null) {
            answers.put(String.valueOf(currentIndex), selectedAnswer);
        }
        if (currentIndex == questions.size() - 1) {
            submitAll();
        } else {
            currentIndex++;
            selectedAnswer = null;
            renderQuestion(currentIndex);
            startTimer();
        }
    }

    private void autoAdvance() {
        // Timed out — no answer recorded; backend treats missing key as wrong
        if (isSubmitting) return;
        if (currentIndex == questions.size() - 1) {
            submitAll();
        } else {
            currentIndex++;
            selectedAnswer = null;
            renderQuestion(currentIndex);
            startTimer();
        }
    }

    private void submitAll() {
        isSubmitting = true;
        showLoading(getString(R.string.quiz_checking));
        if (MODE_FINAL.equals(mode)) {
            viewModel.submitFinalAssessment(sessionId, answers);
        } else {
            viewModel.submitQuiz(nodeId, resourceId, sessionId, answers);
        }
    }

    // ── Results ───────────────────────────────────────────────────────────────

    private void onResultReceived(QuizResult result) {
        showResults();
        populateResultBanner(result);
        buildBreakdown(result);
        populateActionButtons(result);
    }

    private void populateResultBanner(QuizResult result) {
        if (result == null) return;
        if (result.isPassed()) {
            layoutResultBanner.setBackgroundColor(getColor(R.color.quizPassBg));
            tvResultIcon.setText("✓");
            tvResultIcon.setTextColor(getColor(R.color.quizPassText));
            tvResultTitle.setText(getString(R.string.quiz_passed_label));
            tvResultTitle.setTextColor(getColor(R.color.quizPassText));
            tvResultScore.setText(getString(R.string.quiz_passed_score,
                    result.getCorrectCount(), result.getTotalQuestions(), result.getScore()));
        } else {
            layoutResultBanner.setBackgroundColor(getColor(R.color.quizFailBg));
            tvResultIcon.setText("✗");
            tvResultIcon.setTextColor(getColor(R.color.quizFailText));
            tvResultTitle.setText(getString(R.string.quiz_failed_label));
            tvResultTitle.setTextColor(getColor(R.color.quizFailText));
            tvResultScore.setText(getString(R.string.quiz_failed_score,
                    result.getCorrectCount(), result.getTotalQuestions(), result.getPassThreshold()));
        }
    }

    private void buildBreakdown(QuizResult result) {
        if (result == null) return;
        containerBreakdown.removeAllViews();
        List<QuizResultItem> items = result.getResults();
        if (items == null || questions == null) return;

        for (int i = 0; i < items.size() && i < questions.size(); i++) {
            containerBreakdown.addView(
                    buildBreakdownCard(i + 1, questions.get(i), items.get(i)));
        }
    }

    private View buildBreakdownCard(int number, AssessmentQuestion q, QuizResultItem item) {
        int cardPad  = dp(12);
        int rowGap   = dp(4);
        int cardGap  = dp(12);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(getColor(R.color.colorSurface));
        card.setPadding(cardPad, cardPad, cardPad, cardPad);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, cardGap);
        card.setLayoutParams(cardLp);

        // Header row: icon + question text
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        headerLp.setMargins(0, 0, 0, dp(8));
        header.setLayoutParams(headerLp);

        TextView tvIcon = new TextView(this);
        tvIcon.setText(item.isCorrect() ? "✓" : "✗");
        tvIcon.setTextSize(14);
        tvIcon.setTypeface(null, android.graphics.Typeface.BOLD);
        tvIcon.setTextColor(getColor(item.isCorrect() ? R.color.quizPassText : R.color.quizFailText));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        iconLp.setMarginEnd(dp(6));
        tvIcon.setLayoutParams(iconLp);
        header.addView(tvIcon);

        TextView tvQ = new TextView(this);
        tvQ.setText(getString(R.string.quiz_result_number_format, number, q.getQuestion() != null ? q.getQuestion() : ""));
        tvQ.setTextSize(13);
        tvQ.setTypeface(null, android.graphics.Typeface.BOLD);
        tvQ.setTextColor(getColor(R.color.colorTextPrimary));
        tvQ.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        header.addView(tvQ);
        card.addView(header);

        // Option rows
        Map<String, String> opts = q.getOptions();
        if (opts != null) {
            for (String key : KEYS) {
                String text = opts.get(key);
                if (text == null) continue;

                boolean isCorrect  = key.equals(item.getCorrectAnswer());
                boolean isYours    = key.equals(item.getYourAnswer());
                boolean isWrong    = isYours && !isCorrect;

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(10), dp(7), dp(10), dp(7));
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                rowLp.setMargins(0, 0, 0, rowGap);
                row.setLayoutParams(rowLp);

                if (isCorrect) {
                    row.setBackgroundResource(R.drawable.bg_quiz_option_correct);
                } else if (isWrong) {
                    row.setBackgroundResource(R.drawable.bg_quiz_option_wrong);
                } else {
                    row.setBackgroundResource(R.drawable.bg_quiz_option_normal);
                }

                TextView tvKey = new TextView(this);
                tvKey.setText(key.toUpperCase(java.util.Locale.getDefault()));
                tvKey.setTextSize(11);
                tvKey.setTypeface(null, android.graphics.Typeface.BOLD);
                tvKey.setTextColor(getColor(R.color.colorTextSecondary));
                tvKey.setBackgroundResource(R.drawable.bg_circle_gray);
                tvKey.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams keyLp = new LinearLayout.LayoutParams(dp(24), dp(24));
                keyLp.setMarginEnd(dp(8));
                tvKey.setLayoutParams(keyLp);
                row.addView(tvKey);

                TextView tvOpt = new TextView(this);
                tvOpt.setText(text);
                tvOpt.setTextSize(13);
                tvOpt.setTextColor(getColor(R.color.colorTextPrimary));
                tvOpt.setLayoutParams(new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                row.addView(tvOpt);
                card.addView(row);
            }
        }

        // Explanation box
        String explanation = item.getExplanation();
        if (explanation != null && !explanation.isEmpty()) {
            TextView tvExp = new TextView(this);
            tvExp.setText(getString(R.string.quiz_explanation_format, explanation));
            tvExp.setTextSize(12);
            tvExp.setTextColor(getColor(R.color.colorTextSecondary));
            tvExp.setLineSpacing(0, 1.4f);
            tvExp.setBackgroundResource(R.drawable.bg_quiz_explanation);
            tvExp.setPadding(dp(10), dp(8), dp(10), dp(8));
            LinearLayout.LayoutParams expLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            expLp.setMargins(0, dp(8), 0, 0);
            tvExp.setLayoutParams(expLp);
            card.addView(tvExp);
        }

        return card;
    }

    private void populateActionButtons(QuizResult result) {
        if (result.isPassed()) {
            btnBackToLesson.setVisibility(View.VISIBLE);
            btnTryAgain.setVisibility(View.GONE);
            btnBackToLessonFail.setVisibility(View.GONE);
            btnBackToLesson.setOnClickListener(v -> finishWithPass());
        } else {
            btnBackToLesson.setVisibility(View.GONE);
            btnTryAgain.setVisibility(View.VISIBLE);
            btnBackToLessonFail.setVisibility(View.VISIBLE);
            btnTryAgain.setOnClickListener(v -> retryQuiz());
            btnBackToLessonFail.setOnClickListener(v -> finishWithCancel());
        }
    }

    // ── Exit paths ────────────────────────────────────────────────────────────

    private void finishWithPass() {
        Intent out = new Intent();
        if (MODE_FINAL.equals(mode)) {
            out.putExtra(RESULT_FINAL_PASSED, true);
        } else {
            out.putExtra(RESULT_RESOURCE_ID, resourceId);
        }
        setResult(RESULT_OK, out);
        finish();
    }

    private void finishWithCancel() {
        setResult(RESULT_CANCELED);
        finish();
    }

    private void retryQuiz() {
        isSubmitting   = false;
        currentIndex   = 0;
        selectedAnswer = null;
        answers.clear();
        questions.clear();
        viewModel.clearQuizEvents();
        showLoading(getString(R.string.quiz_generating_full));
        startQuizGeneration();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int keyToIndex(String key) {
        for (int i = 0; i < KEYS.length; i++) {
            if (KEYS[i].equals(key)) return i;
        }
        return -1;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
