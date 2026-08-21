package com.example.codecompass.ui;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.codecompass.R;
import com.example.codecompass.api.ApiClient;
import com.example.codecompass.api.TokenManager;
import com.example.codecompass.ui.CertificationsActivity;
import com.example.codecompass.ui.UniversitiesActivity;
import com.example.codecompass.ui.JobsActivity;
import com.example.codecompass.model.GamificationProfile;
import com.example.codecompass.model.Roadmap;
import com.example.codecompass.model.RoadmapNode;
import com.example.codecompass.util.JwtUtils;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DashboardActivity extends AppCompatActivity {

    // ── Views ─────────────────────────────────────────────────────────────────
    private TextView tvWelcome;
    private TextView tvHeaderInitial;
    private TextView tvXp;
    private TextView tvStreak;
    private TextView tvRoadmapTitle;
    private TextView tvRoadmapPct;
    private CircularProgressIndicator progressRoadmap;
    private LinearLayout layoutNextSteps;
    private LinearLayout containerNextSteps;
    private TextView tvViewFullRoadmap;
    private LinearLayout layoutXpBadge;
    private LinearLayout layoutStreakBadge;
    private LinearLayout layoutWarmupBanner;
    private TextView tvWarmupRetry;
    private TextView tvStatStreak;
    private TextView tvStatXp;
    private View loadingBar;
    private SwipeRefreshLayout swipeRefresh;
    private CardView cardRoadmap;
    private CardView cardAiChat;
    private CardView btnActionRoadmap;
    private CardView btnActionAiChat;
    private CardView btnActionJobs;
    private CardView btnActionAchievements;
    private CardView cardCertifications;
    private CardView cardUniversities;
    private CardView cardResumeBuilder;

    // ── State ─────────────────────────────────────────────────────────────────
    private boolean isHandling401 = false;
    private int pendingRequests = 0;
    private int currentRoadmapId = -1;
    private final Handler warmupHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        tvWelcome          = findViewById(R.id.tvWelcome);
        tvHeaderInitial    = findViewById(R.id.tvHeaderInitial);
        tvXp               = findViewById(R.id.tvXp);
        tvStreak           = findViewById(R.id.tvStreak);
        tvRoadmapTitle     = findViewById(R.id.tvRoadmapTitle);
        tvRoadmapPct       = findViewById(R.id.tvRoadmapPct);
        progressRoadmap    = findViewById(R.id.progressRoadmap);
        layoutNextSteps    = findViewById(R.id.layoutNextSteps);
        containerNextSteps = findViewById(R.id.containerNextSteps);
        tvViewFullRoadmap  = findViewById(R.id.tvViewFullRoadmap);
        layoutXpBadge      = findViewById(R.id.layoutXpBadge);
        layoutStreakBadge  = findViewById(R.id.layoutStreakBadge);
        layoutWarmupBanner = findViewById(R.id.layoutWarmupBanner);
        tvWarmupRetry      = findViewById(R.id.tvWarmupRetry);
        tvStatStreak       = findViewById(R.id.tvStatStreak);
        tvStatXp           = findViewById(R.id.tvStatXp);
        loadingBar         = findViewById(R.id.loadingBar);
        swipeRefresh       = findViewById(R.id.swipeRefresh);
        cardRoadmap        = findViewById(R.id.cardRoadmap);
        cardAiChat         = findViewById(R.id.cardAiChat);
        btnActionRoadmap   = findViewById(R.id.btnActionRoadmap);
        btnActionAiChat    = findViewById(R.id.btnActionAiChat);
        btnActionJobs      = findViewById(R.id.btnActionJobs);
        btnActionAchievements = findViewById(R.id.btnActionAchievements);
        cardCertifications = findViewById(R.id.cardCertifications);
        cardUniversities   = findViewById(R.id.cardUniversities);
        cardResumeBuilder  = findViewById(R.id.cardResumeBuilder);

        tvWarmupRetry.setOnClickListener(v -> refresh());

        swipeRefresh.setColorSchemeColors(getColor(R.color.colorPrimary));
        swipeRefresh.setOnRefreshListener(this::refresh);

        // Bottom navigation — Home is selected; Profile navigates to ProfileActivity
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.nav_home);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) return true;
            if (id == R.id.nav_roadmap) {
                openRoadmap();
                return false;
            }
            if (id == R.id.nav_chat) {
                Intent intent = new Intent(this, AIChatHubActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                return false;
            }
            if (id == R.id.nav_profile) {
                Intent intent = new Intent(this, ProfileActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                return false;
            }
            Toast.makeText(this, R.string.coming_soon, Toast.LENGTH_SHORT).show();
            return false;
        });

        setupWelcomeHeader();
        setupClickListeners();

        startLoading();
        warmupHandler.postDelayed(this::showWarmupBanner, 5_000L);

        loadGamificationProfile();
        loadRoadmap();
    }

    // ── Loading state helpers ─────────────────────────────────────────────────

    private void startLoading() {
        pendingRequests = 2;
        loadingBar.setVisibility(View.VISIBLE);
    }

    private void onRequestDone() {
        pendingRequests = Math.max(0, pendingRequests - 1);
        if (pendingRequests == 0) {
            loadingBar.setVisibility(View.GONE);
            swipeRefresh.setRefreshing(false);
            warmupHandler.removeCallbacksAndMessages(null);
            dismissWarmupBanner();
        }
    }

    private void showWarmupBanner() {
        if (isFinishing() || pendingRequests == 0) return;
        layoutWarmupBanner.setVisibility(View.VISIBLE);
        layoutWarmupBanner.setAlpha(0f);
        layoutWarmupBanner.setTranslationY(-16f);
        layoutWarmupBanner.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .start();
    }

    private void dismissWarmupBanner() {
        layoutWarmupBanner.setVisibility(View.GONE);
    }

    private void refresh() {
        warmupHandler.removeCallbacksAndMessages(null);
        dismissWarmupBanner();
        containerNextSteps.removeAllViews();
        layoutNextSteps.setVisibility(View.GONE);
        tvStatStreak.setText("0");
        tvStatXp.setText("0");
        tvRoadmapPct.setText("0%");
        progressRoadmap.setProgress(0);
        startLoading();
        warmupHandler.postDelayed(this::showWarmupBanner, 5_000L);
        loadGamificationProfile();
        loadRoadmap();
    }

    // ── Welcome header ────────────────────────────────────────────────────────

    private void setupWelcomeHeader() {
        String token = TokenManager.getAccessToken(this);
        String fullName = JwtUtils.getFullName(token);
        if (fullName == null || fullName.trim().isEmpty()) return;
        String firstName = fullName.contains(" ")
                ? fullName.substring(0, fullName.indexOf(' '))
                : fullName;
        if (!firstName.isEmpty()) {
            tvWelcome.setText(firstName);
            tvHeaderInitial.setText(firstName.substring(0, 1).toUpperCase());
        }
    }

    // ── Click listeners ───────────────────────────────────────────────────────

    private void setupClickListeners() {
        cardRoadmap.setOnClickListener(v -> openRoadmap());
        tvViewFullRoadmap.setOnClickListener(v -> openRoadmap());
        btnActionRoadmap.setOnClickListener(v -> openRoadmap());
        cardAiChat.setOnClickListener(v -> openAiChat());
        btnActionAiChat.setOnClickListener(v -> openAiChat());
        btnActionJobs.setOnClickListener(v -> {
            Intent intent = new Intent(this, JobsActivity.class);
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });
        cardCertifications.setOnClickListener(v -> {
            Intent intent = new Intent(this, CertificationsActivity.class);
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });
        cardUniversities.setOnClickListener(v -> {
            Intent intent = new Intent(this, UniversitiesActivity.class);
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });
        cardResumeBuilder.setOnClickListener(v -> {
            Intent intent = new Intent(this, ResumeListActivity.class);
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });
        btnActionAchievements.setOnClickListener(v -> {
                Intent intent = new Intent(this, AchievementsActivity.class);
                startActivity(intent);
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });
    }

    private void openAiChat() {
        Intent intent = new Intent(this, AIChatActivity.class);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    private void openRoadmap() {
        Intent intent = new Intent(this, RoadmapActivity.class);
        intent.putExtra(RoadmapActivity.EXTRA_ROADMAP_ID, currentRoadmapId);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    // ── Gamification profile ──────────────────────────────────────────────────

    private void loadGamificationProfile() {
        ApiClient.getService()
                .getGamificationProfile(TokenManager.getBearerToken(this))
                .enqueue(new Callback<GamificationProfile>() {
                    @Override
                    public void onResponse(Call<GamificationProfile> call,
                                           Response<GamificationProfile> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            GamificationProfile profile = response.body();
                            int xp = profile.getXpTotal();
                            int streak = profile.getStreakCount();

                            // Hero badge pills
                            tvXp.setText(getString(R.string.xp_points, xp));
                            if (streak > 0) {
                                tvStreak.setText(getString(R.string.day_streak, streak));
                                layoutStreakBadge.setVisibility(View.VISIBLE);
                            }

                            // Stat cards
                            tvStatXp.setText(String.format(Locale.getDefault(), "%,d", xp));
                            tvStatStreak.setText(String.valueOf(streak));
                        } else if (response.code() == 401) {
                            handle401();
                            return;
                        }
                        onRequestDone();
                    }

                    @Override
                    public void onFailure(Call<GamificationProfile> call, Throwable t) {
                        onRequestDone();
                        if (t instanceof SocketTimeoutException) {
                            showWarmupBanner();
                        }
                    }
                });
    }

    // ── Roadmap ───────────────────────────────────────────────────────────────

    private void loadRoadmap() {
        ApiClient.getService()
                .getRoadmaps(TokenManager.getBearerToken(this))
                .enqueue(new Callback<JsonElement>() {
                    @Override
                    public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            onRequestDone();
                            return;
                        }
                        int roadmapId = extractFirstRoadmapId(response.body());
                        if (roadmapId != -1) {
                            currentRoadmapId = roadmapId;
                            fetchRoadmapDetail(TokenManager.getBearerToken(DashboardActivity.this), roadmapId);
                        } else {
                            onRequestDone();
                        }
                    }

                    @Override
                    public void onFailure(Call<JsonElement> call, Throwable t) {
                        onRequestDone();
                        if (t instanceof SocketTimeoutException) {
                            showWarmupBanner();
                        }
                    }
                });
    }

    private int extractFirstRoadmapId(JsonElement body) {
        try {
            JsonArray arr;
            if (body.isJsonArray()) {
                arr = body.getAsJsonArray();
            } else {
                JsonObject obj = body.getAsJsonObject();
                if (!obj.has("results")) return -1;
                arr = obj.getAsJsonArray("results");
            }
            if (arr.size() == 0) return -1;
            return arr.get(0).getAsJsonObject().get("id").getAsInt();
        } catch (Exception e) {
            return -1;
        }
    }

    private void fetchRoadmapDetail(String bearer, int roadmapId) {
        ApiClient.getService()
                .getRoadmap(bearer, roadmapId)
                .enqueue(new Callback<Roadmap>() {
                    @Override
                    public void onResponse(Call<Roadmap> call, Response<Roadmap> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            populateRoadmapCard(response.body());
                        } else if (response.code() == 401) {
                            handle401();
                            return;
                        }
                        onRequestDone();
                    }

                    @Override
                    public void onFailure(Call<Roadmap> call, Throwable t) {
                        onRequestDone();
                        if (t instanceof SocketTimeoutException) {
                            showWarmupBanner();
                        }
                    }
                });
    }

    private void handle401() {
        if (isHandling401) return;
        isHandling401 = true;
        TokenManager.clear(this);
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void populateRoadmapCard(Roadmap roadmap) {
        String title = roadmap.getTitle();
        if (title != null && !title.isEmpty()) {
            tvRoadmapTitle.setText(title);
        }
        int pct = roadmap.getCompletionPercentage();

        // Animate circular ring 0 → actual completion
        ObjectAnimator ringAnim = ObjectAnimator.ofInt(progressRoadmap, "progress", 0, pct);
        ringAnim.setDuration(900);
        ringAnim.setInterpolator(new DecelerateInterpolator());
        ringAnim.start();

        tvRoadmapPct.setText(String.format(Locale.getDefault(), "%d%%", pct));

        List<RoadmapNode> nodes = roadmap.getNodes();
        if (nodes != null) {
            int shown = 0;
            for (RoadmapNode node : nodes) {
                if (shown >= 3) break;
                if (node.isAvailableOrInProgress()) {
                    addNextStepRow(node);
                    shown++;
                }
            }
            if (shown > 0) {
                layoutNextSteps.setVisibility(View.VISIBLE);
            }
        }
    }

    private void addNextStepRow(RoadmapNode node) {
        View row = getLayoutInflater().inflate(R.layout.item_next_step, containerNextSteps, false);
        TextView tvTitle = row.findViewById(R.id.tvNodeTitle);
        TextView tvMeta  = row.findViewById(R.id.tvNodeMeta);

        tvTitle.setText(node.getTitle());
        String status = "in_progress".equals(node.getStatus()) ? "In Progress" : "Available";
        tvMeta.setText(String.format(Locale.getDefault(), "%s · %dh · +%d XP",
                status, node.getEstimatedHours(), node.getXpReward()));

        row.setOnClickListener(v -> openRoadmap());
        containerNextSteps.addView(row);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh roadmap progress card when returning from RoadmapActivity
        if (currentRoadmapId != -1) {
            containerNextSteps.removeAllViews();
            layoutNextSteps.setVisibility(View.GONE);
            fetchRoadmapDetail(TokenManager.getBearerToken(this), currentRoadmapId);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        warmupHandler.removeCallbacksAndMessages(null);
    }
}
