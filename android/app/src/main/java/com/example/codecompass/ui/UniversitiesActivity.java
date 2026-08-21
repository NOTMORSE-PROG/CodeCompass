package com.example.codecompass.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codecompass.R;
import com.example.codecompass.api.ApiClient;
import com.example.codecompass.api.TokenManager;
import com.example.codecompass.model.PagedResponse;
import com.example.codecompass.model.University;
import com.example.codecompass.model.UniversityRecommendation;
import com.example.codecompass.ui.adapter.UniversityAdapter;
import com.example.codecompass.ui.adapter.UniversityRecommendationAdapter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UniversitiesActivity extends AppCompatActivity {

    // ── Filter chips ───────────────────────────────────────────────────────────
    private static final String[][] TYPE_CHIPS = {
        {"all",     "All"},
        {"state",   "State"},
        {"private", "Private"},
        {"local",   "Local"},
    };
    private static final String[][] PROGRAM_CHIPS = {
        {"all",   "All Programs"},
        {"BSCS",  "BSCS"},
        {"BSIT",  "BSIT"},
        {"BSIS",  "BSIS"},
        {"BSCE",  "BSCE"},
    };

    // ── Views ──────────────────────────────────────────────────────────────────
    private LinearProgressIndicator loadingBar;
    private LinearLayout layoutLoadingState;
    private TabLayout tabLayout;
    private LinearLayout layoutFilters;
    private LinearLayout layoutRecommendations;
    private LinearLayout layoutBrowse;
    private TextInputEditText etSearch;
    private LinearLayout chipGroupType;
    private LinearLayout chipGroupProgram;
    private RecyclerView rvRecommendations;
    private TextView tvRecsEmpty;
    private RecyclerView rvUniversities;
    private TextView tvEmpty;
    private TextView tvResultCount;
    private MaterialButton btnLoadMore;

    // ── State ──────────────────────────────────────────────────────────────────
    private final List<University> allUniversities = new ArrayList<>();
    private final List<UniversityRecommendation> recommendations = new ArrayList<>();
    private String activeType = "all";
    private String activeProgram = "all";
    private String searchQuery = "";
    private boolean hasBrowseMore = false;
    private int browseNextPage = 2;
    private int pendingRequests = 0;
    private boolean isHandling401 = false;
    private boolean browseTabActive = false;  // false = recommendations tab

    // ── Adapters ───────────────────────────────────────────────────────────────
    private UniversityAdapter browseAdapter;
    private UniversityRecommendationAdapter recAdapter;

    // Debounce search input
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_universities);

        loadingBar          = findViewById(R.id.loadingBar);
        layoutLoadingState  = findViewById(R.id.layoutLoadingState);
        tabLayout           = findViewById(R.id.tabLayout);
        layoutFilters       = findViewById(R.id.layoutFilters);
        layoutRecommendations = findViewById(R.id.layoutRecommendations);
        layoutBrowse        = findViewById(R.id.layoutBrowse);
        etSearch            = findViewById(R.id.etSearch);
        chipGroupType       = findViewById(R.id.chipGroupType);
        chipGroupProgram    = findViewById(R.id.chipGroupProgram);
        rvRecommendations   = findViewById(R.id.rvRecommendations);
        tvRecsEmpty         = findViewById(R.id.tvRecsEmpty);
        rvUniversities      = findViewById(R.id.rvUniversities);
        tvEmpty             = findViewById(R.id.tvEmpty);
        tvResultCount       = findViewById(R.id.tvResultCount);
        btnLoadMore         = findViewById(R.id.btnLoadMore);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnLoadMore.setOnClickListener(v -> loadMoreUniversities());

        // Browse adapter
        browseAdapter = new UniversityAdapter(univ -> openDetail(univ, -1, null));
        rvUniversities.setLayoutManager(new LinearLayoutManager(this));
        rvUniversities.setAdapter(browseAdapter);

        // Recommendations adapter
        recAdapter = new UniversityRecommendationAdapter(rec -> {
            Gson gson = new Gson();
            University asUniv = gson.fromJson(gson.toJson(rec), University.class);
            openDetail(asUniv, rec.getMatchScore(), rec.getMatchReasons());
        });
        rvRecommendations.setLayoutManager(new LinearLayoutManager(this));
        rvRecommendations.setAdapter(recAdapter);

        // Tabs
        tabLayout.addTab(tabLayout.newTab().setText("Recommendations"));
        tabLayout.addTab(tabLayout.newTab().setText("Browse All"));
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 1) {
                    browseTabActive = true;
                    layoutRecommendations.setVisibility(View.GONE);
                    layoutBrowse.setVisibility(View.VISIBLE);
                    layoutFilters.setVisibility(View.VISIBLE);
                    btnLoadMore.setVisibility(hasBrowseMore ? View.VISIBLE : View.GONE);
                } else {
                    browseTabActive = false;
                    layoutBrowse.setVisibility(View.GONE);
                    layoutFilters.setVisibility(View.GONE);
                    btnLoadMore.setVisibility(View.GONE);
                    layoutRecommendations.setVisibility(View.VISIBLE);
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        buildTypeChips();
        buildProgramChips();

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (searchHandler != null && searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }
                searchRunnable = () -> {
                    String query = s.toString().trim();
                    if (!query.equals(searchQuery)) {
                        searchQuery = query;
                        reloadBrowse();
                    }
                };
                searchHandler.postDelayed(searchRunnable, 400);
            }
        });

        loadData();
    }

    // ── Data loading ───────────────────────────────────────────────────────────

    private void loadData() {
        pendingRequests = 2;
        showLoading(true);
        String token = TokenManager.getBearerToken(this);
        loadRecommendations(token);
        loadBrowsePage(token, 1);
    }

    private void loadRecommendations(String token) {
        ApiClient.getService()
                .getUniversityRecommendations(token)
                .enqueue(new Callback<List<UniversityRecommendation>>() {
                    @Override
                    public void onResponse(
                            @NonNull Call<List<UniversityRecommendation>> call,
                            @NonNull Response<List<UniversityRecommendation>> response) {
                        if (isHandling401) return;
                        if (response.code() == 401) { handle401(); return; }
                        if (response.isSuccessful() && response.body() != null) {
                            recommendations.clear();
                            recommendations.addAll(response.body());
                        }
                        decrementAndRefresh();
                    }
                    @Override
                    public void onFailure(@NonNull Call<List<UniversityRecommendation>> call,
                                          @NonNull Throwable t) {
                        decrementAndRefresh();
                    }
                });
    }

    private void loadBrowsePage(String token, int page) {
        String searchParam = searchQuery.isEmpty() ? null : searchQuery;
        String typeParam   = "all".equals(activeType) ? null : activeType;

        ApiClient.getService()
                .getUniversities(token, searchParam, typeParam, page)
                .enqueue(new Callback<PagedResponse<University>>() {
                    @Override
                    public void onResponse(
                            @NonNull Call<PagedResponse<University>> call,
                            @NonNull Response<PagedResponse<University>> response) {
                        if (isHandling401) return;
                        if (response.code() == 401) { handle401(); return; }
                        if (response.isSuccessful() && response.body() != null) {
                            PagedResponse<University> pageData = response.body();
                            if (pageData.getResults() != null) {
                                allUniversities.addAll(pageData.getResults());
                            }
                            hasBrowseMore = pageData.getNext() != null;
                            browseNextPage = hasBrowseMore ? page + 1 : page;
                        }
                        decrementAndRefresh();
                    }
                    @Override
                    public void onFailure(@NonNull Call<PagedResponse<University>> call,
                                          @NonNull Throwable t) {
                        decrementAndRefresh();
                    }
                });
    }

    private void loadMoreUniversities() {
        String token = TokenManager.getBearerToken(this);
        btnLoadMore.setVisibility(View.GONE);
        loadingBar.setVisibility(View.VISIBLE);
        String searchParam = searchQuery.isEmpty() ? null : searchQuery;
        String typeParam   = "all".equals(activeType) ? null : activeType;

        ApiClient.getService()
                .getUniversities(token, searchParam, typeParam, browseNextPage)
                .enqueue(new Callback<PagedResponse<University>>() {
                    @Override
                    public void onResponse(
                            @NonNull Call<PagedResponse<University>> call,
                            @NonNull Response<PagedResponse<University>> response) {
                        loadingBar.setVisibility(View.GONE);
                        if (isHandling401) return;
                        if (response.code() == 401) { handle401(); return; }
                        if (response.isSuccessful() && response.body() != null) {
                            PagedResponse<University> pageData = response.body();
                            if (pageData.getResults() != null) {
                                allUniversities.addAll(pageData.getResults());
                            }
                            hasBrowseMore = pageData.getNext() != null;
                            if (hasBrowseMore) browseNextPage++;
                        } else {
                            hasBrowseMore = false;
                        }
                        applyBrowseFilter();
                    }
                    @Override
                    public void onFailure(@NonNull Call<PagedResponse<University>> call,
                                          @NonNull Throwable t) {
                        loadingBar.setVisibility(View.GONE);
                        hasBrowseMore = false;
                        applyBrowseFilter();
                    }
                });
    }

    /** Reload browse from page 1 (called when server-side filters change). */
    private void reloadBrowse() {
        allUniversities.clear();
        browseNextPage = 2;
        hasBrowseMore = false;
        pendingRequests = 1;
        showLoading(false); // don't show full-screen spinner for filter changes
        loadingBar.setVisibility(View.VISIBLE);
        loadBrowsePage(TokenManager.getBearerToken(this), 1);
    }

    private void decrementAndRefresh() {
        pendingRequests--;
        if (pendingRequests <= 0) {
            runOnUiThread(() -> {
                showLoading(false);
                applyBrowseFilter();
                updateRecommendationsView();
            });
        }
    }

    // ── Filtering ──────────────────────────────────────────────────────────────

    private void applyBrowseFilter() {
        List<University> filtered = new ArrayList<>();
        for (University u : allUniversities) {
            if (!matchesProgram(u)) continue;
            filtered.add(u);
        }
        browseAdapter.setData(filtered);

        boolean empty = filtered.isEmpty();
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvUniversities.setVisibility(empty ? View.GONE : View.VISIBLE);
        btnLoadMore.setVisibility(hasBrowseMore && browseTabActive ? View.VISIBLE : View.GONE);

        if (!allUniversities.isEmpty()) {
            tvResultCount.setText(getString(R.string.universities_result_count,
                    filtered.size(), filtered.size() == 1 ? "y" : "ies"));
            tvResultCount.setVisibility(View.VISIBLE);
        } else {
            tvResultCount.setVisibility(View.GONE);
        }
    }

    private boolean matchesProgram(University u) {
        if ("all".equals(activeProgram)) return true;
        if (u.getPrograms() == null) return false;
        for (com.example.codecompass.model.CCSProgram p : u.getPrograms()) {
            if (activeProgram.equalsIgnoreCase(p.getAbbreviation())) return true;
        }
        return false;
    }

    private void updateRecommendationsView() {
        if (recommendations.isEmpty()) {
            tvRecsEmpty.setVisibility(View.VISIBLE);
            rvRecommendations.setVisibility(View.GONE);
        } else {
            tvRecsEmpty.setVisibility(View.GONE);
            rvRecommendations.setVisibility(View.VISIBLE);
            recAdapter.setData(recommendations);
        }
    }

    // ── Chip builders ──────────────────────────────────────────────────────────

    private void buildTypeChips() {
        for (String[] entry : TYPE_CHIPS) {
            String value = entry[0];
            String label = entry[1];
            Chip chip = new Chip(this);
            chip.setText(label);
            chip.setCheckable(true);
            chip.setChecked(value.equals("all"));
            styleChip(chip);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dpToPx(6));
            chip.setLayoutParams(lp);

            chip.setOnCheckedChangeListener((btn, checked) -> {
                if (checked) {
                    activeType = value;
                    for (int i = 0; i < chipGroupType.getChildCount(); i++) {
                        View child = chipGroupType.getChildAt(i);
                        if (child instanceof Chip && child != btn) ((Chip) child).setChecked(false);
                    }
                    reloadBrowse();
                } else {
                    if (activeType.equals(value)) btn.setChecked(true);
                }
            });
            chipGroupType.addView(chip);
        }
    }

    private void buildProgramChips() {
        for (String[] entry : PROGRAM_CHIPS) {
            String value = entry[0];
            String label = entry[1];
            Chip chip = new Chip(this);
            chip.setText(label);
            chip.setCheckable(true);
            chip.setChecked(value.equals("all"));
            styleChip(chip);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dpToPx(6));
            chip.setLayoutParams(lp);

            chip.setOnCheckedChangeListener((btn, checked) -> {
                if (checked) {
                    activeProgram = value;
                    for (int i = 0; i < chipGroupProgram.getChildCount(); i++) {
                        View child = chipGroupProgram.getChildAt(i);
                        if (child instanceof Chip && child != btn) ((Chip) child).setChecked(false);
                    }
                    applyBrowseFilter();  // client-side only
                } else {
                    if (activeProgram.equals(value)) btn.setChecked(true);
                }
            });
            chipGroupProgram.addView(chip);
        }
    }

    private void styleChip(Chip chip) {
        int[][] states = {{android.R.attr.state_checked}, {}};
        int yellow = getColor(R.color.colorPrimary);
        int black  = getColor(R.color.colorTextPrimary);
        chip.setChipBackgroundColor(new ColorStateList(states, new int[]{yellow, Color.WHITE}));
        chip.setTextColor(new ColorStateList(states, new int[]{black, black}));
        chip.setChipStrokeColor(new ColorStateList(states, new int[]{black, black}));
        chip.setCheckedIconTint(ColorStateList.valueOf(black));
        chip.setChipStrokeWidth(dpToPx(1.5f));
        chip.setTextSize(12f);
    }

    // ── Open detail ────────────────────────────────────────────────────────────

    private void openDetail(University univ, int matchScore, List<String> matchReasons) {
        UniversityDetailBottomSheet sheet =
                UniversityDetailBottomSheet.newInstance(univ, matchScore, matchReasons);
        sheet.show(getSupportFragmentManager(), "uni_detail");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void showLoading(boolean show) {
        loadingBar.setVisibility(show ? View.VISIBLE : View.GONE);
        boolean initialLoad = show && allUniversities.isEmpty() && recommendations.isEmpty();
        layoutLoadingState.setVisibility(initialLoad ? View.VISIBLE : View.GONE);
        if (initialLoad) {
            layoutRecommendations.setVisibility(View.GONE);
            layoutBrowse.setVisibility(View.GONE);
            layoutFilters.setVisibility(View.GONE);
        } else if (!show) {
            layoutLoadingState.setVisibility(View.GONE);
            if (browseTabActive) {
                layoutBrowse.setVisibility(View.VISIBLE);
                layoutRecommendations.setVisibility(View.GONE);
                layoutFilters.setVisibility(View.VISIBLE);
            } else {
                layoutRecommendations.setVisibility(View.VISIBLE);
                layoutBrowse.setVisibility(View.GONE);
                layoutFilters.setVisibility(View.GONE);
            }
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

    private int dpToPx(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
