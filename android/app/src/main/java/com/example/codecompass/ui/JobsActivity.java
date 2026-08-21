package com.example.codecompass.ui;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.codecompass.R;
import com.example.codecompass.api.ApiClient;
import com.example.codecompass.api.TokenManager;
import com.example.codecompass.model.JobListing;
import com.example.codecompass.model.PagedResponse;
import com.example.codecompass.model.ResumeMatchFromIdRequest;
import com.example.codecompass.model.ResumeMatchRequest;
import com.example.codecompass.model.SavedJob;
import com.example.codecompass.ui.adapter.JobAdapter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class JobsActivity extends AppCompatActivity
        implements JobAdapter.OnJobActionListener,
                   JobDetailBottomSheet.OnSaveChangedListener {

    // ── Job type filter chips ─────────────────────────────────────────────────
    private static final String[][] JOB_TYPE_CHIPS = {
        {null,          "All"},
        {"full_time",   "Full-time"},
        {"part_time",   "Part-time"},
        {"internship",  "Internship"},
        {"freelance",   "Freelance"},
        {"remote",      "Remote"},
    };

    // ── Views ─────────────────────────────────────────────────────────────────
    private LinearProgressIndicator loadingBar;
    private LinearLayout layoutLoadingState;
    private TextInputEditText etSearch;
    private LinearLayout chipGroupJobType;
    private TextView tabAll;
    private TextView tabSaved;
    private View tabIndicator;
    private RecyclerView rvJobs;
    private TextView tvEmpty;
    private TextView tvResultCount;
    private MaterialButton btnLoadMore;
    private SwipeRefreshLayout swipeRefresh;

    // ── Resume banner views ───────────────────────────────────────────────────
    private LinearLayout layoutResumeIdle;
    private LinearLayout layoutResumeLoading;
    private LinearLayout layoutResumeResult;
    private TextView tvResumeFileName;
    private TextView tvResumeMatchCount;

    // ── State ─────────────────────────────────────────────────────────────────
    private final List<JobListing> jobs           = new ArrayList<>();
    private final List<JobListing> savedJobsList  = new ArrayList<>();
    private final List<JobListing> resumeJobs     = new ArrayList<>();
    private final Set<Integer> savedJobIds        = new HashSet<>();
    private int  currentPage   = 1;
    private boolean hasMore    = false;
    private boolean tabAllActive  = true;
    private boolean savedLoaded   = false;
    private boolean resumeActive  = false;
    private String  resumeFileName = null;
    private String  activeJobType  = null;
    private String  searchQuery    = "";
    private boolean isHandling401  = false;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final Handler mainHandler   = new Handler(Looper.getMainLooper());

    // ── Adapter + file picker ─────────────────────────────────────────────────
    private JobAdapter adapter;
    private ActivityResultLauncher<String> pdfPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_jobs);

        PDFBoxResourceLoader.init(getApplicationContext());

        loadingBar         = findViewById(R.id.loadingBar);
        layoutLoadingState = findViewById(R.id.layoutLoadingState);
        etSearch           = findViewById(R.id.etSearch);
        chipGroupJobType   = findViewById(R.id.chipGroupJobType);
        tabAll             = findViewById(R.id.tabAll);
        tabSaved           = findViewById(R.id.tabSaved);
        tabIndicator       = findViewById(R.id.tabIndicator);
        rvJobs             = findViewById(R.id.rvJobs);
        tvEmpty            = findViewById(R.id.tvEmpty);
        tvResultCount      = findViewById(R.id.tvResultCount);
        btnLoadMore        = findViewById(R.id.btnLoadMore);
        swipeRefresh       = findViewById(R.id.swipeRefresh);

        layoutResumeIdle    = findViewById(R.id.layoutResumeIdle);
        layoutResumeLoading = findViewById(R.id.layoutResumeLoading);
        layoutResumeResult  = findViewById(R.id.layoutResumeResult);
        tvResumeFileName    = findViewById(R.id.tvResumeFileName);
        tvResumeMatchCount  = findViewById(R.id.tvResumeMatchCount);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnUploadResume).setOnClickListener(v -> showResumeSourceSheet());
        findViewById(R.id.btnClearResume).setOnClickListener(v -> clearResume());

        pdfPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> { if (uri != null) handlePdfSelected(uri); });

        adapter = new JobAdapter(this);
        rvJobs.setLayoutManager(new LinearLayoutManager(this));
        rvJobs.setAdapter(adapter);
        rvJobs.setNestedScrollingEnabled(false);

        buildJobTypeChips();
        setActiveTab(true);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchHandler.removeCallbacksAndMessages(null);
                searchHandler.postDelayed(() -> {
                    searchQuery = s.toString().trim();
                    loadJobs(true);
                }, 500);
            }
        });

        tabAll.setOnClickListener(v -> {
            if (!tabAllActive) {
                setActiveTab(true);
                refreshDisplay();
            }
        });

        tabSaved.setOnClickListener(v -> {
            if (tabAllActive) {
                setActiveTab(false);
                if (!savedLoaded) {
                    loadSavedJobs();
                } else {
                    refreshDisplay();
                }
            }
        });

        btnLoadMore.setOnClickListener(v -> {
            currentPage++;
            loadJobs(false);
        });

        swipeRefresh.setOnRefreshListener(() -> {
            loadJobs(true);
            loadSavedJobIds();
        });

        loadJobs(true);
        loadSavedJobIds();
    }

    // ── PDF handling ──────────────────────────────────────────────────────────

    private void handlePdfSelected(Uri uri) {
        resumeFileName = getFileName(uri);
        setResumeBannerState(ResumeBannerState.LOADING);

        new Thread(() -> {
            try {
                ContentResolver cr = getContentResolver();
                InputStream stream = cr.openInputStream(uri);
                if (stream == null) throw new Exception("Cannot open file");

                PDDocument doc = PDDocument.load(stream);
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(doc);
                doc.close();
                stream.close();

                if (text.trim().length() < 100) {
                    mainHandler.post(() -> {
                        Toast.makeText(this, R.string.jobs_resume_too_short, Toast.LENGTH_LONG).show();
                        setResumeBannerState(ResumeBannerState.IDLE);
                    });
                } else {
                    mainHandler.post(() -> callRecommendFromResume(text.trim()));
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    Toast.makeText(this, R.string.jobs_resume_error, Toast.LENGTH_LONG).show();
                    setResumeBannerState(ResumeBannerState.IDLE);
                });
            }
        }).start();
    }

    private void callRecommendFromResume(String text) {
        String token = TokenManager.getBearerToken(this);
        ApiClient.getService()
                .recommendFromResume(token, new ResumeMatchRequest(text))
                .enqueue(new Callback<List<JobListing>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<JobListing>> call,
                                           @NonNull Response<List<JobListing>> response) {
                        if (isHandling401) return;
                        if (response.code() == 401) { handle401(); return; }
                        if (response.isSuccessful() && response.body() != null) {
                            resumeJobs.clear();
                            resumeJobs.addAll(response.body());
                            resumeActive = true;
                            setResumeBannerState(ResumeBannerState.RESULT);
                            if (tabAllActive) refreshDisplay();
                        } else {
                            Toast.makeText(JobsActivity.this,
                                    R.string.jobs_resume_error, Toast.LENGTH_LONG).show();
                            setResumeBannerState(ResumeBannerState.IDLE);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<JobListing>> call,
                                          @NonNull Throwable t) {
                        Toast.makeText(JobsActivity.this,
                                R.string.jobs_resume_error, Toast.LENGTH_LONG).show();
                        setResumeBannerState(ResumeBannerState.IDLE);
                    }
                });
    }

    private void clearResume() {
        resumeActive = false;
        resumeFileName = null;
        resumeJobs.clear();
        setResumeBannerState(ResumeBannerState.IDLE);
        refreshDisplay();
    }

    // ── Resume source picker ──────────────────────────────────────────────────

    private void showResumeSourceSheet() {
        ResumeSourceBottomSheet sheet = new ResumeSourceBottomSheet();
        sheet.setListener(new ResumeSourceBottomSheet.Listener() {
            @Override
            public void onSavedResumeChosen(int resumeId, String resumeTitle) {
                resumeFileName = resumeTitle;
                setResumeBannerState(ResumeBannerState.LOADING);
                callRecommendFromResumeId(resumeId);
            }

            @Override
            public void onUploadPdfChosen() {
                pdfPickerLauncher.launch("application/pdf");
            }

            @Override
            public void onAuthExpired() {
                handle401();
            }
        });
        sheet.show(getSupportFragmentManager(), "resume_source");
    }

    private void callRecommendFromResumeId(int resumeId) {
        String token = TokenManager.getBearerToken(this);
        ApiClient.getService()
                .recommendFromResumeId(token, new ResumeMatchFromIdRequest(resumeId))
                .enqueue(new Callback<List<JobListing>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<JobListing>> call,
                                           @NonNull Response<List<JobListing>> response) {
                        if (isHandling401) return;
                        if (response.code() == 401) { handle401(); return; }
                        if (response.isSuccessful() && response.body() != null) {
                            resumeJobs.clear();
                            resumeJobs.addAll(response.body());
                            resumeActive = true;
                            setResumeBannerState(ResumeBannerState.RESULT);
                            if (tabAllActive) refreshDisplay();
                        } else if (response.code() == 400) {
                            Toast.makeText(JobsActivity.this,
                                    R.string.jobs_resume_source_empty_content,
                                    Toast.LENGTH_LONG).show();
                            setResumeBannerState(ResumeBannerState.IDLE);
                        } else {
                            Toast.makeText(JobsActivity.this,
                                    R.string.jobs_resume_error, Toast.LENGTH_LONG).show();
                            setResumeBannerState(ResumeBannerState.IDLE);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<JobListing>> call,
                                          @NonNull Throwable t) {
                        if (isHandling401) return;
                        Toast.makeText(JobsActivity.this,
                                R.string.jobs_resume_error, Toast.LENGTH_LONG).show();
                        setResumeBannerState(ResumeBannerState.IDLE);
                    }
                });
    }

    private enum ResumeBannerState { IDLE, LOADING, RESULT }

    private void setResumeBannerState(ResumeBannerState state) {
        layoutResumeIdle.setVisibility(state == ResumeBannerState.IDLE ? View.VISIBLE : View.GONE);
        layoutResumeLoading.setVisibility(state == ResumeBannerState.LOADING ? View.VISIBLE : View.GONE);
        layoutResumeResult.setVisibility(state == ResumeBannerState.RESULT ? View.VISIBLE : View.GONE);

        if (state == ResumeBannerState.RESULT) {
            tvResumeFileName.setText(resumeFileName != null ? resumeFileName : "Resume");
            tvResumeMatchCount.setText(getString(R.string.jobs_resume_matches, resumeJobs.size()));
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) result = cursor.getString(idx);
                }
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result != null ? result : "resume.pdf";
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private void loadJobs(boolean reset) {
        if (reset) {
            currentPage = 1;
            jobs.clear();
        }
        showLoading(true);
        String token = TokenManager.getBearerToken(this);
        String search = searchQuery.isEmpty() ? null : searchQuery;

        ApiClient.getService()
                .getJobs(token, currentPage, search, activeJobType)
                .enqueue(new Callback<PagedResponse<JobListing>>() {
                    @Override
                    public void onResponse(@NonNull Call<PagedResponse<JobListing>> call,
                                           @NonNull Response<PagedResponse<JobListing>> response) {
                        if (isHandling401) return;
                        swipeRefresh.setRefreshing(false);
                        showLoading(false);
                        if (response.code() == 401) { handle401(); return; }
                        if (response.isSuccessful() && response.body() != null) {
                            PagedResponse<JobListing> page = response.body();
                            if (page.getResults() != null) {
                                jobs.addAll(page.getResults());
                            }
                            hasMore = page.getNext() != null;
                        }
                        if (tabAllActive) refreshDisplay();
                    }

                    @Override
                    public void onFailure(@NonNull Call<PagedResponse<JobListing>> call,
                                          @NonNull Throwable t) {
                        swipeRefresh.setRefreshing(false);
                        showLoading(false);
                        if (tabAllActive) refreshDisplay();
                    }
                });
    }

    private void loadSavedJobIds() {
        String token = TokenManager.getBearerToken(this);
        ApiClient.getService()
                .getSavedJobs(token)
                .enqueue(new Callback<PagedResponse<SavedJob>>() {
                    @Override
                    public void onResponse(@NonNull Call<PagedResponse<SavedJob>> call,
                                           @NonNull Response<PagedResponse<SavedJob>> response) {
                        if (isHandling401) return;
                        if (response.code() == 401) { handle401(); return; }
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getResults() != null) {
                            savedJobIds.clear();
                            savedJobsList.clear();
                            for (SavedJob sj : response.body().getResults()) {
                                if (sj.getJob() != null) {
                                    savedJobIds.add(sj.getJob().getId());
                                    savedJobsList.add(sj.getJob());
                                }
                            }
                            savedLoaded = true;
                            adapter.setSavedIds(savedJobIds);
                            if (!tabAllActive) refreshDisplay();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<PagedResponse<SavedJob>> call,
                                          @NonNull Throwable t) {}
                });
    }

    private void loadSavedJobs() {
        showLoading(true);
        String token = TokenManager.getBearerToken(this);
        ApiClient.getService()
                .getSavedJobs(token)
                .enqueue(new Callback<PagedResponse<SavedJob>>() {
                    @Override
                    public void onResponse(@NonNull Call<PagedResponse<SavedJob>> call,
                                           @NonNull Response<PagedResponse<SavedJob>> response) {
                        if (isHandling401) return;
                        showLoading(false);
                        if (response.code() == 401) { handle401(); return; }
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getResults() != null) {
                            savedJobIds.clear();
                            savedJobsList.clear();
                            for (SavedJob sj : response.body().getResults()) {
                                if (sj.getJob() != null) {
                                    savedJobIds.add(sj.getJob().getId());
                                    savedJobsList.add(sj.getJob());
                                }
                            }
                            savedLoaded = true;
                        }
                        refreshDisplay();
                    }

                    @Override
                    public void onFailure(@NonNull Call<PagedResponse<SavedJob>> call,
                                          @NonNull Throwable t) {
                        showLoading(false);
                        refreshDisplay();
                    }
                });
    }

    // ── Save / unsave ─────────────────────────────────────────────────────────

    private void toggleSave(JobListing job) {
        boolean wasSaved = savedJobIds.contains(job.getId());
        String token = TokenManager.getBearerToken(this);

        if (wasSaved) {
            savedJobIds.remove(job.getId());
            savedJobsList.removeIf(j -> j.getId() == job.getId());
            adapter.setSavedIds(savedJobIds);
            if (!tabAllActive) refreshDisplay();

            ApiClient.getService()
                    .unsaveJob(token, job.getId())
                    .enqueue(new Callback<Void>() {
                        @Override
                        public void onResponse(@NonNull Call<Void> call,
                                               @NonNull Response<Void> response) {
                            if (!response.isSuccessful()) revertSave(job, true);
                        }
                        @Override
                        public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                            revertSave(job, true);
                        }
                    });
        } else {
            savedJobIds.add(job.getId());
            savedJobsList.add(0, job);
            adapter.setSavedIds(savedJobIds);

            ApiClient.getService()
                    .saveJob(token, job.getId())
                    .enqueue(new Callback<Void>() {
                        @Override
                        public void onResponse(@NonNull Call<Void> call,
                                               @NonNull Response<Void> response) {
                            if (!response.isSuccessful() && response.code() != 201
                                    && response.code() != 200) {
                                revertSave(job, false);
                            }
                        }
                        @Override
                        public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                            revertSave(job, false);
                        }
                    });
        }
    }

    private void revertSave(JobListing job, boolean reAddToSaved) {
        if (reAddToSaved) {
            savedJobIds.add(job.getId());
            savedJobsList.add(0, job);
        } else {
            savedJobIds.remove(job.getId());
            savedJobsList.removeIf(j -> j.getId() == job.getId());
        }
        adapter.setSavedIds(savedJobIds);
        if (!tabAllActive) refreshDisplay();
    }

    // ── JobAdapter.OnJobActionListener ────────────────────────────────────────

    @Override
    public void onJobTap(JobListing job) {
        JobDetailBottomSheet sheet = JobDetailBottomSheet.newInstance(job, savedJobIds);
        sheet.setOnSaveChangedListener(this);
        sheet.show(getSupportFragmentManager(), "job_detail");
    }

    @Override
    public void onSaveToggle(JobListing job, boolean currentlySaved) {
        toggleSave(job);
    }

    // ── JobDetailBottomSheet.OnSaveChangedListener ────────────────────────────

    @Override
    public void onSaveToggled(JobListing job, boolean nowSaved) {
        if (nowSaved) {
            savedJobIds.add(job.getId());
            if (savedJobsList.stream().noneMatch(j -> j.getId() == job.getId())) {
                savedJobsList.add(0, job);
            }
        } else {
            savedJobIds.remove(job.getId());
            savedJobsList.removeIf(j -> j.getId() == job.getId());
        }
        adapter.setSavedIds(savedJobIds);
        if (!tabAllActive) refreshDisplay();
    }

    // ── Display ───────────────────────────────────────────────────────────────

    private void refreshDisplay() {
        List<JobListing> displayList;
        if (!tabAllActive) {
            displayList = savedJobsList;
        } else if (resumeActive) {
            displayList = resumeJobs;
        } else {
            displayList = jobs;
        }

        adapter.setResumeMode(resumeActive && tabAllActive);
        adapter.setData(displayList, savedJobIds);

        boolean empty = displayList.isEmpty();
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvJobs.setVisibility(empty ? View.GONE : View.VISIBLE);

        if (!empty) {
            tvResultCount.setText(getString(R.string.jobs_result_count,
                    displayList.size(), displayList.size() == 1 ? "" : "s"));
            tvResultCount.setVisibility(View.VISIBLE);
        } else {
            tvResultCount.setVisibility(View.GONE);
        }

        if (tabAllActive) {
            tvEmpty.setText(R.string.jobs_empty);
            // Hide load-more when showing resume results
            btnLoadMore.setVisibility((!resumeActive && hasMore) ? View.VISIBLE : View.GONE);
        } else {
            tvEmpty.setText(R.string.jobs_saved_empty);
            btnLoadMore.setVisibility(View.GONE);
        }
    }

    private void setActiveTab(boolean all) {
        tabAllActive = all;
        int primaryColor   = getResources().getColor(R.color.colorPrimary, getTheme());
        int secondaryColor = getResources().getColor(R.color.colorTextSecondary, getTheme());

        tabAll.setTextColor(all ? primaryColor : secondaryColor);
        tabSaved.setTextColor(all ? secondaryColor : primaryColor);

        tabIndicator.post(() -> {
            int totalWidth = tabAll.getWidth() + tabSaved.getWidth();
            if (totalWidth > 0) {
                tabIndicator.setBackgroundColor(primaryColor);
            }
        });
    }

    // ── Job type chips ────────────────────────────────────────────────────────

    private void buildJobTypeChips() {
        for (String[] entry : JOB_TYPE_CHIPS) {
            String value = entry[0];
            String label = entry[1];
            Chip chip = new Chip(this);
            chip.setText(label);
            chip.setCheckable(true);
            chip.setChecked(value == null);
            chip.setChipBackgroundColorResource(R.color.colorSurface);
            chip.setChipStrokeColorResource(R.color.colorTextPrimary);
            chip.setChipStrokeWidth(dpToPx(1));
            chip.setTextColor(getColor(R.color.colorTextPrimary));
            chip.setTextSize(12f);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dpToPx(6));
            chip.setLayoutParams(lp);

            chip.setOnCheckedChangeListener((btn, checked) -> {
                applyChipActiveStyle((Chip) btn, checked);
                if (checked) {
                    activeJobType = value;
                    for (int i = 0; i < chipGroupJobType.getChildCount(); i++) {
                        View child = chipGroupJobType.getChildAt(i);
                        if (child instanceof Chip && child != btn) {
                            ((Chip) child).setChecked(false);
                        }
                    }
                    loadJobs(true);
                } else {
                    if (activeJobType == null && value == null) btn.setChecked(true);
                    else if (value != null && value.equals(activeJobType)) btn.setChecked(true);
                }
            });

            applyChipActiveStyle(chip, value == null);
            chipGroupJobType.addView(chip);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void applyChipActiveStyle(Chip chip, boolean active) {
        int primary     = getColor(R.color.colorPrimary);
        int onPrimary   = getColor(R.color.colorOnPrimary);
        int surface     = getColor(R.color.colorSurface);
        int textPrimary = getColor(R.color.colorTextPrimary);
        chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(active ? primary : surface));
        chip.setTextColor(active ? onPrimary : textPrimary);
        chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(active ? primary : textPrimary));
    }

    private void showLoading(boolean show) {
        loadingBar.setVisibility(show ? View.VISIBLE : View.GONE);
        boolean initialLoad = show && jobs.isEmpty() && tabAllActive;
        layoutLoadingState.setVisibility(initialLoad ? View.VISIBLE : View.GONE);
        if (initialLoad) {
            swipeRefresh.setVisibility(View.GONE);
        } else if (!show) {
            swipeRefresh.setVisibility(View.VISIBLE);
            layoutLoadingState.setVisibility(View.GONE);
        }
    }

    private void handle401() {
        if (isHandling401) return;
        isHandling401 = true;
        TokenManager.clear(this);
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
