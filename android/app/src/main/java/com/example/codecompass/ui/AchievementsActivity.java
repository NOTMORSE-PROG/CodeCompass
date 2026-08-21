package com.example.codecompass.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codecompass.R;
import com.example.codecompass.api.ApiClient;
import com.example.codecompass.api.TokenManager;
import com.example.codecompass.model.Badge;
import com.example.codecompass.model.GamificationProfile;
import com.example.codecompass.model.LeaderboardEntry;
import com.example.codecompass.model.PagedResponse;
import com.example.codecompass.model.UserBadge;
import com.example.codecompass.model.XPEvent;
import com.example.codecompass.ui.adapter.BadgeAdapter;
import com.example.codecompass.ui.adapter.LeaderboardAdapter;
import com.example.codecompass.ui.adapter.XPHistoryAdapter;
import com.example.codecompass.util.JwtUtils;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AchievementsActivity extends AppCompatActivity {

    private static final int XP_PER_LEVEL = 500;

    // ── Views — XP ────────────────────────────────────────────────────────────
    private TextView tvTotalXp;
    private TextView tvLevelLabel;
    private LinearProgressIndicator progressXp;
    private TextView tvLevelCurrent;
    private TextView tvLevelXpRange;

    // ── Views — Streak ────────────────────────────────────────────────────────
    private TextView tvStreakCount;
    private TextView tvStreakMessage;

    // ── Views — Badges ────────────────────────────────────────────────────────
    private RecyclerView rvBadges;
    private TextView tvBadgeCount;
    private TextView tvBadgesEmpty;

    // ── Views — XP History ────────────────────────────────────────────────────
    private RecyclerView rvXpHistory;
    private TextView tvXpHistoryEmpty;

    // ── Views — Leaderboard ───────────────────────────────────────────────────
    private RecyclerView rvLeaderboard;
    private TextView tvLeaderboardEmpty;
    private Button btnPeriodWeekly;
    private Button btnPeriodMonthly;
    private Button btnPeriodAllTime;

    // ── Loading ───────────────────────────────────────────────────────────────
    private View loadingBar;
    private int pendingRequests = 0;
    private boolean isHandling401 = false;

    // ── Adapters ──────────────────────────────────────────────────────────────
    private final BadgeAdapter badgeAdapter = new BadgeAdapter();
    private final XPHistoryAdapter xpHistoryAdapter = new XPHistoryAdapter();
    private final LeaderboardAdapter leaderboardAdapter = new LeaderboardAdapter();

    // ── State ─────────────────────────────────────────────────────────────────
    private List<Badge> allBadges = null;
    private Set<String> earnedSlugs = new HashSet<>();
    private String currentPeriod = "weekly";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_achievements);

        bindViews();
        setupRecyclerViews();
        setupPeriodButtons();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        startLoading();
        loadAll();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    // ── Bind ──────────────────────────────────────────────────────────────────

    private void bindViews() {
        loadingBar        = findViewById(R.id.loadingBarAchievements);
        tvTotalXp         = findViewById(R.id.tvTotalXp);
        tvLevelLabel      = findViewById(R.id.tvLevelLabel);
        progressXp        = findViewById(R.id.progressXp);
        tvLevelCurrent    = findViewById(R.id.tvLevelCurrent);
        tvLevelXpRange    = findViewById(R.id.tvLevelXpRange);
        tvStreakCount     = findViewById(R.id.tvStreakCount);
        tvStreakMessage   = findViewById(R.id.tvStreakMessage);
        rvBadges          = findViewById(R.id.rvBadges);
        tvBadgeCount      = findViewById(R.id.tvBadgeCount);
        tvBadgesEmpty     = findViewById(R.id.tvBadgesEmpty);
        rvXpHistory       = findViewById(R.id.rvXpHistory);
        tvXpHistoryEmpty  = findViewById(R.id.tvXpHistoryEmpty);
        rvLeaderboard     = findViewById(R.id.rvLeaderboard);
        tvLeaderboardEmpty = findViewById(R.id.tvLeaderboardEmpty);
        btnPeriodWeekly   = findViewById(R.id.btnPeriodWeekly);
        btnPeriodMonthly  = findViewById(R.id.btnPeriodMonthly);
        btnPeriodAllTime  = findViewById(R.id.btnPeriodAllTime);
    }

    private void setupRecyclerViews() {
        rvBadges.setLayoutManager(new GridLayoutManager(this, 3));
        rvBadges.setAdapter(badgeAdapter);
        rvBadges.setNestedScrollingEnabled(false);

        rvXpHistory.setLayoutManager(new LinearLayoutManager(this));
        rvXpHistory.setAdapter(xpHistoryAdapter);
        rvXpHistory.setNestedScrollingEnabled(false);

        rvLeaderboard.setLayoutManager(new LinearLayoutManager(this));
        rvLeaderboard.setAdapter(leaderboardAdapter);
        rvLeaderboard.setNestedScrollingEnabled(false);
    }

    private void setupPeriodButtons() {
        btnPeriodWeekly.setOnClickListener(v -> loadLeaderboard("weekly"));
        btnPeriodMonthly.setOnClickListener(v -> loadLeaderboard("monthly"));
        btnPeriodAllTime.setOnClickListener(v -> loadLeaderboard("all_time"));
        updatePeriodButtons("weekly");
    }

    private void updatePeriodButtons(String period) {
        currentPeriod = period;
        int activeColor  = getColor(R.color.colorPrimary);
        int inactiveColor = getColor(R.color.colorDivider);
        int activeText   = getColor(R.color.colorOnPrimary);
        int inactiveText = getColor(R.color.colorTextSecondary);

        styleButton(btnPeriodWeekly,  "weekly".equals(period),  activeColor, inactiveColor, activeText, inactiveText);
        styleButton(btnPeriodMonthly, "monthly".equals(period), activeColor, inactiveColor, activeText, inactiveText);
        styleButton(btnPeriodAllTime, "all_time".equals(period),activeColor, inactiveColor, activeText, inactiveText);
    }

    private void styleButton(Button btn, boolean active,
                              int activeColor, int inactiveColor,
                              int activeText, int inactiveText) {
        btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                active ? activeColor : inactiveColor));
        btn.setTextColor(active ? activeText : inactiveText);
    }

    // ── Loading ───────────────────────────────────────────────────────────────

    private void startLoading() {
        pendingRequests = 5;
        loadingBar.setVisibility(View.VISIBLE);
    }

    private void onRequestDone() {
        pendingRequests = Math.max(0, pendingRequests - 1);
        if (pendingRequests == 0) {
            loadingBar.setVisibility(View.GONE);
        }
    }

    // ── API calls ─────────────────────────────────────────────────────────────

    private void loadAll() {
        String bearer = TokenManager.getBearerToken(this);
        loadProfile(bearer);
        loadAllBadges(bearer);
        loadEarnedBadges(bearer);
        loadXPHistory(bearer);
        loadLeaderboard("weekly");
    }

    private void loadProfile(String bearer) {
        ApiClient.getService()
                .getGamificationProfile(bearer)
                .enqueue(new Callback<GamificationProfile>() {
                    @Override
                    public void onResponse(Call<GamificationProfile> call,
                                           Response<GamificationProfile> response) {
                        if (response.code() == 401) { handle401(); return; }
                        if (response.isSuccessful() && response.body() != null) {
                            populateProfileCards(response.body());
                        }
                        onRequestDone();
                    }

                    @Override
                    public void onFailure(Call<GamificationProfile> call, Throwable t) {
                        onRequestDone();
                    }
                });
    }

    private void loadAllBadges(String bearer) {
        ApiClient.getService()
                .getAllBadges(bearer)
                .enqueue(new Callback<PagedResponse<Badge>>() {
                    @Override
                    public void onResponse(Call<PagedResponse<Badge>> call,
                                           Response<PagedResponse<Badge>> response) {
                        if (response.code() == 401) { handle401(); return; }
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getResults() != null) {
                            allBadges = response.body().getResults();
                            refreshBadgeGrid();
                        } else {
                            tvBadgesEmpty.setVisibility(View.VISIBLE);
                        }
                        onRequestDone();
                    }

                    @Override
                    public void onFailure(Call<PagedResponse<Badge>> call, Throwable t) {
                        tvBadgesEmpty.setVisibility(View.VISIBLE);
                        onRequestDone();
                    }
                });
    }

    private void loadEarnedBadges(String bearer) {
        ApiClient.getService()
                .getEarnedBadges(bearer)
                .enqueue(new Callback<PagedResponse<UserBadge>>() {
                    @Override
                    public void onResponse(Call<PagedResponse<UserBadge>> call,
                                           Response<PagedResponse<UserBadge>> response) {
                        if (response.code() == 401) { handle401(); return; }
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getResults() != null) {
                            earnedSlugs.clear();
                            for (UserBadge ub : response.body().getResults()) {
                                if (ub.getBadge() != null) {
                                    earnedSlugs.add(ub.getBadge().getSlug());
                                }
                            }
                            refreshBadgeGrid();
                        }
                        onRequestDone();
                    }

                    @Override
                    public void onFailure(Call<PagedResponse<UserBadge>> call, Throwable t) {
                        onRequestDone();
                    }
                });
    }

    private void loadXPHistory(String bearer) {
        ApiClient.getService()
                .getXPHistory(bearer)
                .enqueue(new Callback<List<XPEvent>>() {
                    @Override
                    public void onResponse(Call<List<XPEvent>> call,
                                           Response<List<XPEvent>> response) {
                        if (response.code() == 401) { handle401(); return; }
                        if (response.isSuccessful() && response.body() != null
                                && !response.body().isEmpty()) {
                            xpHistoryAdapter.setEvents(response.body());
                            tvXpHistoryEmpty.setVisibility(View.GONE);
                        } else {
                            tvXpHistoryEmpty.setVisibility(View.VISIBLE);
                        }
                        onRequestDone();
                    }

                    @Override
                    public void onFailure(Call<List<XPEvent>> call, Throwable t) {
                        tvXpHistoryEmpty.setVisibility(View.VISIBLE);
                        onRequestDone();
                    }
                });
    }

    private void loadLeaderboard(String period) {
        updatePeriodButtons(period);
        tvLeaderboardEmpty.setVisibility(View.GONE);

        ApiClient.getService()
                .getLeaderboard(TokenManager.getBearerToken(this), period)
                .enqueue(new Callback<List<LeaderboardEntry>>() {
                    @Override
                    public void onResponse(Call<List<LeaderboardEntry>> call,
                                           Response<List<LeaderboardEntry>> response) {
                        if (response.code() == 401) { handle401(); return; }
                        if (response.isSuccessful() && response.body() != null
                                && !response.body().isEmpty()) {
                            String myName = JwtUtils.getFullName(
                                    TokenManager.getAccessToken(AchievementsActivity.this));
                            leaderboardAdapter.setEntries(response.body(), myName);
                            tvLeaderboardEmpty.setVisibility(View.GONE);
                        } else {
                            leaderboardAdapter.setEntries(java.util.Collections.emptyList(), "");
                            tvLeaderboardEmpty.setVisibility(View.VISIBLE);
                        }
                        // Only count initial weekly load towards pendingRequests
                        if ("weekly".equals(period) && pendingRequests > 0) {
                            onRequestDone();
                        }
                    }

                    @Override
                    public void onFailure(Call<List<LeaderboardEntry>> call, Throwable t) {
                        tvLeaderboardEmpty.setVisibility(View.VISIBLE);
                        if ("weekly".equals(period) && pendingRequests > 0) {
                            onRequestDone();
                        }
                    }
                });
    }

    // ── UI population ─────────────────────────────────────────────────────────

    private void populateProfileCards(GamificationProfile profile) {
        int xpTotal = profile.getXpTotal();
        int streak  = profile.getStreakCount();

        // XP card
        int level     = xpTotal / XP_PER_LEVEL + 1;
        int xpInLevel = xpTotal % XP_PER_LEVEL;
        int pct       = (xpInLevel * 100) / XP_PER_LEVEL;
        int nextLevelXp = level * XP_PER_LEVEL;

        tvTotalXp.setText(String.format(Locale.getDefault(), "%,d", xpTotal));
        tvLevelLabel.setText(getString(R.string.achievements_level_label, level,
                String.format(Locale.getDefault(), "%,d", nextLevelXp)));
        progressXp.setProgressCompat(pct, true);
        tvLevelCurrent.setText(getString(R.string.achievements_level_current, level));
        tvLevelXpRange.setText(getString(R.string.achievements_xp_range,
                String.format(Locale.getDefault(), "%,d", xpInLevel),
                String.format(Locale.getDefault(), "%,d", XP_PER_LEVEL)));

        // Streak card
        tvStreakCount.setText(String.valueOf(streak));
        tvStreakMessage.setText(streak > 0
                ? getString(R.string.achievements_streak_active)
                : getString(R.string.achievements_streak_inactive));
    }

    private void refreshBadgeGrid() {
        if (allBadges == null) return;
        badgeAdapter.setData(allBadges, earnedSlugs);
        tvBadgesEmpty.setVisibility(allBadges.isEmpty() ? View.VISIBLE : View.GONE);
        tvBadgeCount.setText(getString(R.string.achievements_badge_count,
                earnedSlugs.size(), allBadges.size()));
    }

    // ── Auth helpers ──────────────────────────────────────────────────────────

    private void handle401() {
        if (isHandling401) return;
        isHandling401 = true;
        TokenManager.clear(this);
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
