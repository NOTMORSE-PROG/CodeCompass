package com.example.codecompass.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
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
import com.example.codecompass.model.Certification;
import com.example.codecompass.model.PagedResponse;
import com.example.codecompass.model.Roadmap;
import com.example.codecompass.model.RoadmapNode;
import com.example.codecompass.model.TrackCertRequest;
import com.example.codecompass.model.UserCertification;
import com.example.codecompass.ui.adapter.CertificationAdapter;
import com.example.codecompass.ui.adapter.MyCertsAdapter;
import com.example.codecompass.ui.adapter.RecommendationAdapter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CertificationsActivity extends AppCompatActivity
        implements CertificationAdapter.OnCertActionListener,
                   CertDetailBottomSheet.OnTrackChangeListener {

    // ── Track filter chips ─────────────────────────────────────────────────────
    private static final String[][] TRACK_CHIPS = {
        {"all",           "All"},
        {"web",           "Web Dev"},
        {"backend",       "Backend"},
        {"data_science",  "Data Science"},
        {"cybersecurity", "Cybersecurity"},
        {"cloud",         "Cloud"},
        {"mobile",        "Mobile Dev"},
        {"networking",    "Networking"},
        {"algorithms",    "Algorithms"},
        {"marketing",     "Marketing"},
        {"agile",         "Agile & PM"},
        {"general",       "General IT"},
    };

    // ── Views ──────────────────────────────────────────────────────────────────
    private LinearProgressIndicator loadingBar;
    private LinearLayout layoutLoadingState;
    private TabLayout tabLayout;
    private LinearLayout layoutBrowse;
    private LinearLayout layoutMyCerts;
    // Browse
    private TextInputEditText etSearch;
    private LinearLayout chipGroupProvider;
    private Chip chipFreeOnly;
    private RecyclerView rvCertifications;
    private RecyclerView rvRecommendations;
    private LinearLayout sectionRecommendations;
    private TextView tvEmpty;
    private TextView tvResultCount;
    private MaterialButton btnLoadMore;
    // My Certs
    private RecyclerView rvMyCerts;
    private TextView tvMyCertsEmpty;

    // ── State ──────────────────────────────────────────────────────────────────
    private final List<Certification> allCerts = new ArrayList<>();
    private final Map<Integer, UserCertification> myCerts = new HashMap<>();
    private String activeTrack = "all";
    private boolean showFreeOnly = false;
    private String searchQuery = "";
    private int pendingRequests = 0;
    private boolean isHandling401 = false;
    private Roadmap primaryRoadmap = null;
    private boolean roadmapLoaded = false;
    private boolean myCertsTabActive = false;
    private boolean hasCertMore = false;
    private int certNextPage = 2;

    // ── Adapters ────────────────────────────────────────────────────────────────
    private CertificationAdapter adapter;
    private RecommendationAdapter recommendationAdapter;
    private MyCertsAdapter myCertsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_certifications);

        loadingBar             = findViewById(R.id.loadingBar);
        layoutLoadingState     = findViewById(R.id.layoutLoadingState);
        tabLayout              = findViewById(R.id.tabLayout);
        layoutBrowse           = findViewById(R.id.layoutBrowse);
        layoutMyCerts          = findViewById(R.id.layoutMyCerts);
        etSearch               = findViewById(R.id.etSearch);
        chipGroupProvider      = findViewById(R.id.chipGroupProvider);
        chipFreeOnly           = findViewById(R.id.chipFreeOnly);
        rvCertifications       = findViewById(R.id.rvCertifications);
        rvRecommendations      = findViewById(R.id.rvRecommendations);
        sectionRecommendations = findViewById(R.id.sectionRecommendations);
        tvEmpty                = findViewById(R.id.tvEmpty);
        tvResultCount          = findViewById(R.id.tvResultCount);
        btnLoadMore            = findViewById(R.id.btnLoadMore);
        rvMyCerts              = findViewById(R.id.rvMyCerts);
        tvMyCertsEmpty         = findViewById(R.id.tvMyCertsEmpty);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnLoadMore.setOnClickListener(v -> loadMoreCerts());

        // Browse adapter
        adapter = new CertificationAdapter(this);
        rvCertifications.setLayoutManager(new LinearLayoutManager(this));
        rvCertifications.setAdapter(adapter);

        // Recommendations adapter
        recommendationAdapter = new RecommendationAdapter(cert -> onCardTap(cert));
        rvRecommendations.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvRecommendations.setAdapter(recommendationAdapter);

        // My Certs adapter
        myCertsAdapter = new MyCertsAdapter((cert, uc) -> openDetail(cert, uc));
        rvMyCerts.setLayoutManager(new LinearLayoutManager(this));
        rvMyCerts.setAdapter(myCertsAdapter);

        // Tabs
        tabLayout.addTab(tabLayout.newTab().setText("Browse"));
        tabLayout.addTab(tabLayout.newTab().setText("My Certs"));
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 1) {
                    myCertsTabActive = true;
                    layoutBrowse.setVisibility(View.GONE);
                    layoutMyCerts.setVisibility(View.VISIBLE);
                    btnLoadMore.setVisibility(View.GONE);
                    updateMyCertsView();
                } else {
                    myCertsTabActive = false;
                    layoutMyCerts.setVisibility(View.GONE);
                    layoutBrowse.setVisibility(View.VISIBLE);
                    btnLoadMore.setVisibility(hasCertMore ? View.VISIBLE : View.GONE);
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        buildTrackChips();

        chipFreeOnly.setOnCheckedChangeListener((btn, checked) -> {
            showFreeOnly = checked;
            applyFilter();
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString().toLowerCase(Locale.getDefault()).trim();
                applyFilter();
            }
        });

        loadData();
    }

    // ── Data loading ───────────────────────────────────────────────────────────

    private void loadData() {
        pendingRequests = 2;
        showLoading(true);
        String token = TokenManager.getBearerToken(this);
        loadCertPage(token, 1);
        loadMyCerts(token);
        loadPrimaryRoadmapForRecs(token);
    }

    private void loadCertPage(String token, int page) {
        ApiClient.getService()
                .getCertifications(token, null, null, null, null, page)
                .enqueue(new Callback<PagedResponse<Certification>>() {
                    @Override
                    public void onResponse(@NonNull Call<PagedResponse<Certification>> call,
                                           @NonNull Response<PagedResponse<Certification>> response) {
                        if (isHandling401) return;
                        if (response.code() == 401) { handle401(); return; }
                        if (response.isSuccessful() && response.body() != null) {
                            PagedResponse<Certification> pageData = response.body();
                            if (pageData.getResults() != null) {
                                allCerts.addAll(pageData.getResults());
                            }
                            hasCertMore = pageData.getNext() != null;
                            certNextPage = hasCertMore ? page + 1 : page;
                        }
                        decrementAndRefresh();
                    }
                    @Override
                    public void onFailure(@NonNull Call<PagedResponse<Certification>> call,
                                          @NonNull Throwable t) {
                        decrementAndRefresh();
                    }
                });
    }

    private void loadMoreCerts() {
        String token = TokenManager.getBearerToken(this);
        btnLoadMore.setVisibility(View.GONE);
        loadingBar.setVisibility(View.VISIBLE);
        ApiClient.getService()
                .getCertifications(token, null, null, null, null, certNextPage)
                .enqueue(new Callback<PagedResponse<Certification>>() {
                    @Override
                    public void onResponse(@NonNull Call<PagedResponse<Certification>> call,
                                           @NonNull Response<PagedResponse<Certification>> response) {
                        loadingBar.setVisibility(View.GONE);
                        if (isHandling401) return;
                        if (response.code() == 401) { handle401(); return; }
                        if (response.isSuccessful() && response.body() != null) {
                            PagedResponse<Certification> pageData = response.body();
                            if (pageData.getResults() != null) {
                                allCerts.addAll(pageData.getResults());
                            }
                            hasCertMore = pageData.getNext() != null;
                            if (hasCertMore) certNextPage++;
                        } else {
                            hasCertMore = false;
                        }
                        applyFilter();
                    }
                    @Override
                    public void onFailure(@NonNull Call<PagedResponse<Certification>> call,
                                          @NonNull Throwable t) {
                        loadingBar.setVisibility(View.GONE);
                        hasCertMore = false;
                        applyFilter();
                    }
                });
    }

    private void loadMyCerts(String token) {
        ApiClient.getService()
                .getMyCertifications(token)
                .enqueue(new Callback<PagedResponse<UserCertification>>() {
                    @Override
                    public void onResponse(@NonNull Call<PagedResponse<UserCertification>> call,
                                           @NonNull Response<PagedResponse<UserCertification>> response) {
                        if (isHandling401) return;
                        if (response.code() == 401) { handle401(); return; }
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getResults() != null) {
                            myCerts.clear();
                            for (UserCertification uc : response.body().getResults()) {
                                if (uc.getCertification() != null) {
                                    myCerts.put(uc.getCertification().getId(), uc);
                                }
                            }
                        }
                        decrementAndRefresh();
                    }
                    @Override
                    public void onFailure(@NonNull Call<PagedResponse<UserCertification>> call,
                                          @NonNull Throwable t) {
                        decrementAndRefresh();
                    }
                });
    }

    private void loadPrimaryRoadmapForRecs(String token) {
        ApiClient.getService().getRoadmaps(token).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(@NonNull Call<JsonElement> call,
                                   @NonNull Response<JsonElement> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    roadmapLoaded = true; updateRecommendations(); return;
                }
                int id = extractPrimaryRoadmapId(response.body());
                if (id == -1) {
                    roadmapLoaded = true; updateRecommendations(); return;
                }
                ApiClient.getService().getRoadmap(token, id).enqueue(new Callback<Roadmap>() {
                    @Override
                    public void onResponse(@NonNull Call<Roadmap> call2,
                                           @NonNull Response<Roadmap> r2) {
                        if (r2.isSuccessful() && r2.body() != null) primaryRoadmap = r2.body();
                        roadmapLoaded = true;
                        updateRecommendations();
                    }
                    @Override
                    public void onFailure(@NonNull Call<Roadmap> call2, @NonNull Throwable t) {
                        roadmapLoaded = true; updateRecommendations();
                    }
                });
            }
            @Override
            public void onFailure(@NonNull Call<JsonElement> call, @NonNull Throwable t) {
                roadmapLoaded = true; updateRecommendations();
            }
        });
    }

    private int extractPrimaryRoadmapId(JsonElement body) {
        try {
            JsonArray arr = body.isJsonArray() ? body.getAsJsonArray()
                    : body.getAsJsonObject().getAsJsonArray("results");
            if (arr == null) return -1;
            int firstId = -1;
            for (int i = 0; i < arr.size(); i++) {
                JsonObject r = arr.get(i).getAsJsonObject();
                int rid = r.get("id").getAsInt();
                if (firstId == -1) firstId = rid;
                if (r.has("isPrimary") && r.get("isPrimary").getAsBoolean()) return rid;
            }
            return firstId;
        } catch (Exception e) { return -1; }
    }

    private void decrementAndRefresh() {
        pendingRequests--;
        if (pendingRequests <= 0) {
            showLoading(false);
            applyFilter();
            updateMyCertsView();
            updateRecommendations();
        }
    }

    // ── My Certs tab ───────────────────────────────────────────────────────────

    private void updateMyCertsView() {
        Map<Integer, Certification> byId = new HashMap<>();
        for (Certification c : allCerts) byId.put(c.getId(), c);

        // Apply same track / free / search filters to My Certs
        Map<Integer, UserCertification> filtered = new HashMap<>();
        for (Map.Entry<Integer, UserCertification> entry : myCerts.entrySet()) {
            Certification cert = byId.get(entry.getKey());
            if (cert == null) continue;
            if (!activeTrack.equals("all") && !activeTrack.equals(cert.getTrack())) continue;
            if (showFreeOnly && !cert.isFree()) continue;
            if (!searchQuery.isEmpty()) {
                String name = cert.getName().toLowerCase(Locale.getDefault());
                String abbr = cert.getAbbreviation() != null
                        ? cert.getAbbreviation().toLowerCase(Locale.getDefault()) : "";
                if (!name.contains(searchQuery) && !abbr.contains(searchQuery)) continue;
            }
            filtered.put(entry.getKey(), entry.getValue());
        }

        myCertsAdapter.setData(filtered, byId);
        boolean empty = filtered.isEmpty();
        tvMyCertsEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvMyCerts.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    // ── Recommendations ────────────────────────────────────────────────────────

    private void updateRecommendations() {
        runOnUiThread(() -> {
            if (allCerts.isEmpty() || !roadmapLoaded
                    || primaryRoadmap == null
                    || primaryRoadmap.getNodes() == null
                    || primaryRoadmap.getNodes().isEmpty()) {
                sectionRecommendations.setVisibility(View.GONE);
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (RoadmapNode node : primaryRoadmap.getNodes()) {
                if (node.getTitle() != null)
                    sb.append(node.getTitle().toLowerCase(Locale.getDefault())).append(" ");
            }
            String keywordBlob = sb.toString();

            List<ScoredCert> scored = new ArrayList<>();
            for (Certification cert : allCerts) {
                if (myCerts.containsKey(cert.getId())) continue;
                if (cert.getRelevantSkills() == null || cert.getRelevantSkills().isEmpty()) continue;
                int score = 0;
                for (String skill : cert.getRelevantSkills()) {
                    if (skill != null && keywordBlob.contains(skill.toLowerCase(Locale.getDefault())))
                        score++;
                }
                if (score > 0) scored.add(new ScoredCert(cert, score));
            }
            scored.sort((a, b) -> b.score - a.score);

            List<Certification> recs = new ArrayList<>();
            for (int i = 0; i < Math.min(4, scored.size()); i++) recs.add(scored.get(i).cert);

            if (recs.isEmpty()) {
                sectionRecommendations.setVisibility(View.GONE);
            } else {
                sectionRecommendations.setVisibility(View.VISIBLE);
                recommendationAdapter.setData(recs);
            }
        });
    }

    private static class ScoredCert {
        final Certification cert;
        final int score;
        ScoredCert(Certification c, int s) { cert = c; score = s; }
    }

    // ── Filtering ──────────────────────────────────────────────────────────────

    private void applyFilter() {
        List<Certification> filtered = allCerts.stream()
                .filter(c -> activeTrack.equals("all") || activeTrack.equals(c.getTrack()))
                .filter(c -> !showFreeOnly || c.isFree())
                .filter(c -> searchQuery.isEmpty()
                        || c.getName().toLowerCase(Locale.getDefault()).contains(searchQuery)
                        || (c.getAbbreviation() != null
                            && c.getAbbreviation().toLowerCase(Locale.getDefault()).contains(searchQuery)))
                .collect(Collectors.toList());

        adapter.setData(filtered, myCerts);

        boolean empty = filtered.isEmpty();
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvCertifications.setVisibility(empty ? View.GONE : View.VISIBLE);
        btnLoadMore.setVisibility(hasCertMore && !myCertsTabActive ? View.VISIBLE : View.GONE);

        if (!allCerts.isEmpty()) {
            tvResultCount.setText(getString(R.string.certifications_result_count,
                    filtered.size(), filtered.size() == 1 ? "" : "s"));
            tvResultCount.setVisibility(View.VISIBLE);
        }

        // Keep My Certs view in sync with the same filters
        updateMyCertsView();
    }

    // ── Track chips ────────────────────────────────────────────────────────────

    private void buildTrackChips() {
        for (String[] entry : TRACK_CHIPS) {
            String value = entry[0];
            String label = entry[1];
            Chip chip = new Chip(this);
            chip.setText(label);
            chip.setCheckable(true);
            chip.setChecked(value.equals("all"));

            int[][] chipStates = {{android.R.attr.state_checked}, {}};
            int yellow = getColor(R.color.colorPrimary);
            int black  = getColor(R.color.colorTextPrimary);
            chip.setChipBackgroundColor(new ColorStateList(chipStates,
                    new int[]{yellow, Color.WHITE}));
            chip.setTextColor(new ColorStateList(chipStates,
                    new int[]{black, black}));
            chip.setChipStrokeColor(new ColorStateList(chipStates,
                    new int[]{black, black}));
            chip.setCheckedIconTint(ColorStateList.valueOf(black));
            chip.setChipStrokeWidth(dpToPx(1.5f));
            chip.setTextSize(12f);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(dpToPx(6));
            chip.setLayoutParams(params);

            chip.setOnCheckedChangeListener((btn, checked) -> {
                if (checked) {
                    activeTrack = value;
                    for (int i = 0; i < chipGroupProvider.getChildCount(); i++) {
                        View child = chipGroupProvider.getChildAt(i);
                        if (child instanceof Chip && child != btn) ((Chip) child).setChecked(false);
                    }
                    applyFilter();
                } else {
                    if (activeTrack.equals(value)) btn.setChecked(true);
                }
            });

            chipGroupProvider.addView(chip);
        }
    }

    // ── CertificationAdapter.OnCertActionListener ─────────────────────────────

    @Override
    public void onTrack(Certification cert) {
        if (!myCerts.containsKey(cert.getId())) {
            String token = TokenManager.getBearerToken(this);
            ApiClient.getService()
                    .trackCertification(token, new TrackCertRequest(cert.getId(), "interested"))
                    .enqueue(new Callback<UserCertification>() {
                        @Override
                        public void onResponse(@NonNull Call<UserCertification> call,
                                               @NonNull Response<UserCertification> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                onTracked(response.body());
                            }
                        }
                        @Override public void onFailure(@NonNull Call<UserCertification> call,
                                                        @NonNull Throwable t) {}
                    });
        } else {
            onCardTap(cert);
        }
    }

    @Override
    public void onCardTap(Certification cert) {
        openDetail(cert, myCerts.get(cert.getId()));
    }

    private void openDetail(Certification cert, UserCertification uc) {
        CertDetailBottomSheet sheet = CertDetailBottomSheet.newInstance(cert, uc);
        sheet.setOnTrackChangeListener(this);
        sheet.show(getSupportFragmentManager(), "cert_detail");
    }

    // ── CertDetailBottomSheet.OnTrackChangeListener ───────────────────────────

    @Override
    public void onTracked(UserCertification uc) {
        if (uc.getCertification() != null) myCerts.put(uc.getCertification().getId(), uc);
        applyFilter();
        updateMyCertsView();
        updateRecommendations();
    }

    @Override
    public void onStatusUpdated(UserCertification uc) {
        if (uc.getCertification() != null) myCerts.put(uc.getCertification().getId(), uc);
        applyFilter();
        updateMyCertsView();
    }

    @Override
    public void onUntracked(int certId) {
        myCerts.remove(certId);
        applyFilter();
        updateMyCertsView();
        updateRecommendations();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void showLoading(boolean show) {
        loadingBar.setVisibility(show ? View.VISIBLE : View.GONE);
        // Centered spinner only during the very first load (list still empty)
        boolean initialLoad = show && allCerts.isEmpty();
        layoutLoadingState.setVisibility(initialLoad ? View.VISIBLE : View.GONE);
        if (initialLoad) {
            layoutBrowse.setVisibility(View.GONE);
            layoutMyCerts.setVisibility(View.GONE);
        } else if (!show) {
            layoutLoadingState.setVisibility(View.GONE);
            // Restore correct content visibility based on active tab
            if (myCertsTabActive) {
                layoutMyCerts.setVisibility(View.VISIBLE);
                layoutBrowse.setVisibility(View.GONE);
            } else {
                layoutBrowse.setVisibility(View.VISIBLE);
                layoutMyCerts.setVisibility(View.GONE);
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
