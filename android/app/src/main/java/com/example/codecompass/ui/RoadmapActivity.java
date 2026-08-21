package com.example.codecompass.ui;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.codecompass.R;
import com.example.codecompass.api.ApiClient;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.example.codecompass.ui.AIChatHubActivity;
import com.example.codecompass.api.TokenManager;
import com.example.codecompass.model.GamificationProfile;
import com.example.codecompass.model.Roadmap;
import com.example.codecompass.model.RoadmapNode;
import com.example.codecompass.ui.adapter.RoadmapAdapter;
import com.example.codecompass.viewmodel.RoadmapDisplayItem;
import com.example.codecompass.viewmodel.RoadmapViewModel;

import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RoadmapActivity extends AppCompatActivity {

    public static final String EXTRA_ROADMAP_ID = "roadmap_id";
    private static final long NEXT_UP_DISMISS_DELAY_MS = 6000L;

    // ── Views ─────────────────────────────────────────────────────────────────
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerRoadmap;
    private LinearLayout layoutLoading;
    private LinearLayout layoutEmpty;
    private LinearLayout layoutError;
    private LinearLayout layoutRepairBanner;
    private LinearLayout layoutXpGain;
    private LinearLayout layoutNextUp;
    private TextView tvError;
    private TextView tvXpAmount;
    private TextView tvNextUpTitle;

    // Header views (from included view_roadmap_header)
    private TextView tvRoadmapTitle;
    private TextView tvRoadmapStatus;
    private TextView tvCareerPath;
    private ProgressBar progressRoadmap;
    private TextView tvCompletionPct;
    private TextView tvNodesDone;
    private TextView tvHoursLeft;
    private TextView tvStreakSeparator;
    private TextView tvStreakStat;

    // ── ViewModel + Adapter ───────────────────────────────────────────────────
    private RoadmapViewModel viewModel;
    private RoadmapAdapter adapter;

    // ── State ─────────────────────────────────────────────────────────────────
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean hasAutoScrolled = false;
    private boolean pendingNextUpBanner = false;

    // Receives RESULT_OK from VideoLearningActivity when quiz is passed
    private final ActivityResultLauncher<Intent> videoResultLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    int resId = result.getData().getIntExtra(
                            VideoLearningActivity.RESULT_RESOURCE_ID, -1);
                    if (resId >= 0) {
                        NodeDetailBottomSheet sheet = (NodeDetailBottomSheet)
                                getSupportFragmentManager()
                                        .findFragmentByTag(NodeDetailBottomSheet.TAG);
                        if (sheet != null && sheet.isAdded()) {
                            sheet.onResourceQuizPassed(resId);
                        }
                    }
                }
            });

    // Receives RESULT_OK from QuizActivity (MODE_FINAL) when the final assessment passes.
    // ViewModel already reloads the roadmap on pass (so certs appear unlocked), but we
    // trigger an explicit load here too in case the result arrives first.
    private final ActivityResultLauncher<Intent> finalAssessmentResultLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null
                        && result.getData().getBooleanExtra(QuizActivity.RESULT_FINAL_PASSED, false)) {
                    viewModel.load();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_roadmap);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        bindViews();
        setupRecycler();
        setupSwipeRefresh();
        setupBottomNav();

        viewModel = new ViewModelProvider(this).get(RoadmapViewModel.class);
        int roadmapId = getIntent().getIntExtra(EXTRA_ROADMAP_ID, -1);
        viewModel.setRoadmapId(roadmapId);

        observeViewModel();
        viewModel.loadInitial(); // fix-structure + load on first open
        loadStreakAsync();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
    }

    // ── View binding ──────────────────────────────────────────────────────────

    private void bindViews() {
        swipeRefresh       = findViewById(R.id.swipeRefresh);
        recyclerRoadmap    = findViewById(R.id.recyclerRoadmap);
        layoutLoading      = findViewById(R.id.layoutLoading);
        layoutEmpty        = findViewById(R.id.layoutEmpty);
        layoutError        = findViewById(R.id.layoutError);
        layoutRepairBanner = findViewById(R.id.layoutRepairBanner);
        layoutXpGain       = findViewById(R.id.layoutXpGain);
        layoutNextUp       = findViewById(R.id.layoutNextUp);
        tvError            = findViewById(R.id.tvError);
        tvXpAmount         = findViewById(R.id.tvXpAmount);
        tvNextUpTitle      = findViewById(R.id.tvNextUpTitle);

        // Header
        tvRoadmapTitle    = findViewById(R.id.tvRoadmapTitle);
        tvRoadmapStatus   = findViewById(R.id.tvRoadmapStatus);
        tvCareerPath      = findViewById(R.id.tvCareerPath);
        progressRoadmap   = findViewById(R.id.progressRoadmap);
        tvCompletionPct   = findViewById(R.id.tvCompletionPct);
        tvNodesDone       = findViewById(R.id.tvNodesDone);
        tvHoursLeft       = findViewById(R.id.tvHoursLeft);
        tvStreakSeparator = findViewById(R.id.tvStreakSeparator);
        tvStreakStat      = findViewById(R.id.tvStreakStat);

        // Repair banner
        findViewById(R.id.btnRepair).setOnClickListener(v -> {
            layoutRepairBanner.setVisibility(View.GONE);
            viewModel.repairRoadmap();
        });

        // Empty state
        findViewById(R.id.btnGenerate).setOnClickListener(v -> openOnboarding());

        // Error state retry
        findViewById(R.id.btnRetry).setOnClickListener(v -> {
            layoutError.setVisibility(View.GONE);
            viewModel.refresh();
        });

        // Next Up dismiss
        findViewById(R.id.btnDismissNextUp).setOnClickListener(v -> hideNextUpBanner());
    }

    private void setupRecycler() {
        adapter = new RoadmapAdapter();
        adapter.setListener(this::openNodeDetail);
        recyclerRoadmap.setLayoutManager(new LinearLayoutManager(this));
        recyclerRoadmap.setAdapter(adapter);
        recyclerRoadmap.setHasFixedSize(false);
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setColorSchemeColors(getColor(R.color.colorPrimary));
        swipeRefresh.setOnRefreshListener(() -> viewModel.refresh());
    }

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavRoadmap);
        bottomNav.setSelectedItemId(R.id.nav_roadmap);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_roadmap) return true;
            if (id == R.id.nav_home) {
                Intent intent = new Intent(this, DashboardActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
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
    }

    // ── ViewModel observers ───────────────────────────────────────────────────

    private void observeViewModel() {
        viewModel.isLoading().observe(this, loading -> {
            if (loading == null) return;
            if (loading) {
                layoutLoading.setVisibility(View.VISIBLE);
                layoutEmpty.setVisibility(View.GONE);
                layoutError.setVisibility(View.GONE);
            } else {
                layoutLoading.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
            }
        });

        viewModel.getDisplayItems().observe(this, items -> {
            if (items == null || items.isEmpty()) {
                recyclerRoadmap.setVisibility(View.GONE);
                layoutEmpty.setVisibility(View.VISIBLE);
                return;
            }
            recyclerRoadmap.setVisibility(View.VISIBLE);
            layoutEmpty.setVisibility(View.GONE);
            adapter.submitList(items);
            checkForRepairNeeded(items);

            // Auto-scroll to first in_progress node on first load only
            if (!hasAutoScrolled) {
                hasAutoScrolled = true;
                autoScrollToInProgress(items);
            }

            // Show "Next Up" banner if a node was just completed
            if (pendingNextUpBanner) {
                pendingNextUpBanner = false;
                showNextUpBanner(items);
            }
        });

        viewModel.getRoadmap().observe(this, this::bindHeader);

        viewModel.getError().observe(this, error -> {
            if (error == null || error.isEmpty()) return;
            layoutLoading.setVisibility(View.GONE);
            if (adapter.getItemCount() == 0) {
                tvError.setText(error);
                layoutError.setVisibility(View.VISIBLE);
                layoutEmpty.setVisibility(View.GONE);
            }
            swipeRefresh.setRefreshing(false);
        });

        viewModel.getXpGainEvent().observe(this, xp -> {
            if (xp == null || xp <= 0) return;
            showXpOverlay(xp);
            pendingNextUpBanner = true; // next displayItems update → show Next Up
        });
    }

    // ── Header binding ────────────────────────────────────────────────────────

    private void bindHeader(Roadmap roadmap) {
        if (roadmap == null) return;

        if (roadmap.getTitle() != null && !roadmap.getTitle().isEmpty()) {
            tvRoadmapTitle.setText(roadmap.getTitle());
        }

        if (roadmap.getCareerPath() != null && !roadmap.getCareerPath().isEmpty()) {
            tvCareerPath.setText(roadmap.getCareerPath());
            tvCareerPath.setVisibility(View.VISIBLE);
        } else {
            tvCareerPath.setVisibility(View.GONE);
        }

        String status = roadmap.getStatus();
        if ("active".equalsIgnoreCase(status)) {
            tvRoadmapStatus.setText(getString(R.string.roadmap_status_active));
            tvRoadmapStatus.setVisibility(View.VISIBLE);
        } else if ("completed".equalsIgnoreCase(status)) {
            tvRoadmapStatus.setText(getString(R.string.roadmap_status_completed));
            tvRoadmapStatus.setVisibility(View.VISIBLE);
        } else if ("generating".equalsIgnoreCase(status)) {
            tvRoadmapStatus.setText(getString(R.string.roadmap_status_generating));
            tvRoadmapStatus.setVisibility(View.VISIBLE);
        } else {
            tvRoadmapStatus.setVisibility(View.GONE);
        }

        int pct = roadmap.getCompletionPercentage();
        ObjectAnimator.ofInt(progressRoadmap, "progress", progressRoadmap.getProgress(), pct)
                .setDuration(600)
                .start();
        tvCompletionPct.setText(String.format(Locale.getDefault(), "%d%%", pct));

        if (roadmap.getNodes() != null) {
            int done = 0, nonMilestoneTotal = 0, hoursLeft = 0;
            for (RoadmapNode n : roadmap.getNodes()) {
                if (!n.isMilestone()) {
                    nonMilestoneTotal++;
                    if (n.isCompleted()) done++;
                    else hoursLeft += n.getEstimatedHours();
                }
            }
            tvNodesDone.setText(String.format(Locale.getDefault(), "%d / %d nodes", done, nonMilestoneTotal));
            tvHoursLeft.setText(String.format(Locale.getDefault(), "~%dh left", hoursLeft));
        }
    }

    // ── Streak (loaded separately from gamification API) ──────────────────────

    private void loadStreakAsync() {
        ApiClient.getService()
                .getGamificationProfile(TokenManager.getBearerToken(this))
                .enqueue(new Callback<GamificationProfile>() {
                    @Override
                    public void onResponse(Call<GamificationProfile> call,
                                           Response<GamificationProfile> response) {
                        if (!response.isSuccessful() || response.body() == null) return;
                        int streak = response.body().getStreakCount();
                        if (streak > 0) {
                            tvStreakSeparator.setVisibility(View.VISIBLE);
                            tvStreakStat.setText(getString(R.string.roadmap_streak_format, streak));
                            tvStreakStat.setVisibility(View.VISIBLE);
                        }
                    }

                    @Override
                    public void onFailure(Call<GamificationProfile> call, Throwable t) {
                        // Streak is non-critical — fail silently
                    }
                });
    }

    // ── Repair banner ─────────────────────────────────────────────────────────

    private void checkForRepairNeeded(List<RoadmapDisplayItem> items) {
        int nodeCards = 0, lockedCards = 0;
        for (RoadmapDisplayItem item : items) {
            if (item.getType() == RoadmapDisplayItem.TYPE_NODE_CARD) {
                nodeCards++;
                if (item.getNode().isLocked()) lockedCards++;
            }
        }
        layoutRepairBanner.setVisibility(
                (nodeCards > 0 && nodeCards == lockedCards) ? View.VISIBLE : View.GONE);
    }

    // ── Auto-scroll ───────────────────────────────────────────────────────────

    private void autoScrollToInProgress(List<RoadmapDisplayItem> items) {
        int scrollPos = -1;
        for (int i = 0; i < items.size(); i++) {
            RoadmapDisplayItem item = items.get(i);
            if (item.getType() == RoadmapDisplayItem.TYPE_NODE_CARD
                    && item.getNode().isInProgress()) {
                scrollPos = i;
                break;
            }
        }
        if (scrollPos < 0) return;
        final int pos = scrollPos;
        recyclerRoadmap.post(() -> {
            LinearLayoutManager lm = (LinearLayoutManager) recyclerRoadmap.getLayoutManager();
            if (lm != null) lm.scrollToPositionWithOffset(pos, dpToPx(80));
        });
    }

    // ── Next Up banner ────────────────────────────────────────────────────────

    private void showNextUpBanner(List<RoadmapDisplayItem> items) {
        // Find the first AVAILABLE node to highlight as "Next Up"
        for (RoadmapDisplayItem item : items) {
            if (item.getType() == RoadmapDisplayItem.TYPE_NODE_CARD
                    && item.getNode().isAvailable()) {
                tvNextUpTitle.setText(item.getNode().getTitle());
                layoutNextUp.setVisibility(View.VISIBLE);
                layoutNextUp.setAlpha(0f);
                layoutNextUp.animate().alpha(1f).setDuration(250).start();
                mainHandler.postDelayed(this::hideNextUpBanner, NEXT_UP_DISMISS_DELAY_MS);
                return;
            }
        }
    }

    private void hideNextUpBanner() {
        mainHandler.removeCallbacksAndMessages(null); // cancel any pending dismiss
        layoutNextUp.animate().alpha(0f).setDuration(200).withEndAction(
                () -> layoutNextUp.setVisibility(View.GONE)).start();
    }

    // ── Node tap ──────────────────────────────────────────────────────────────

    private void openNodeDetail(RoadmapNode node) {
        // Final Assessment bypasses the bottom sheet — it launches QuizActivity directly
        // in MODE_FINAL with the roadmap ID. The backend generates 10 questions based on
        // the roadmap's skill + project content across all phases.
        if (RoadmapNode.TYPE_FINAL_ASSESSMENT.equals(node.getNodeType())) {
            if (node.isLocked()) return;  // locked → no action
            Intent intent = new Intent(this, QuizActivity.class);
            intent.putExtra(QuizActivity.EXTRA_ROADMAP_ID, viewModel.getRoadmapId());
            intent.putExtra(QuizActivity.EXTRA_MODE, QuizActivity.MODE_FINAL);
            com.example.codecompass.model.Roadmap rm = viewModel.getRoadmap().getValue();
            if (rm != null) {
                intent.putExtra(QuizActivity.EXTRA_ROADMAP_TITLE, rm.getTitle() != null ? rm.getTitle() : "");
                intent.putExtra(QuizActivity.EXTRA_CAREER_PATH, rm.getCareerPath() != null ? rm.getCareerPath() : "");
            }
            finalAssessmentResultLauncher.launch(intent);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            return;
        }

        // Immediately transition AVAILABLE → IN_PROGRESS (mirrors web behavior)
        if (node.isAvailable()) {
            viewModel.updateNodeStatus(node.getId(), RoadmapNode.STATUS_IN_PROGRESS);
        }

        NodeDetailBottomSheet sheet = NodeDetailBottomSheet.newInstance(node, viewModel.getRoadmapId());
        sheet.setOnNodeCompletedListener((nodeId, newStatus) ->
                viewModel.updateNodeStatus(nodeId, newStatus));

        // Non-YouTube resources: open URL in browser / Chrome Custom Tabs
        sheet.setOnOpenResourceListener(this::openUrl);

        // YouTube resources: open VideoLearningActivity (quiz in-app at 60 %)
        sheet.setOnOpenVideoResourceListener((resource, nodeId, isCompleted) -> {
            String videoId = resource.getYoutubeVideoId() != null
                    ? resource.getYoutubeVideoId()
                    : extractVideoId(resource.getUrl());
            Intent intent = new Intent(this, VideoLearningActivity.class);
            intent.putExtra(VideoLearningActivity.EXTRA_NODE_ID, nodeId);
            intent.putExtra(VideoLearningActivity.EXTRA_RESOURCE_ID, resource.getId());
            intent.putExtra(VideoLearningActivity.EXTRA_ROADMAP_ID, viewModel.getRoadmapId());
            intent.putExtra(VideoLearningActivity.EXTRA_VIDEO_ID, videoId);
            intent.putExtra(VideoLearningActivity.EXTRA_VIDEO_TITLE, resource.getTitle());
            intent.putExtra(VideoLearningActivity.EXTRA_VIDEO_CHANNEL, resource.getYoutubeChannel());
            intent.putExtra(VideoLearningActivity.EXTRA_WATCH_UNLOCKED, resource.isWatchUnlocked());
            intent.putExtra(VideoLearningActivity.EXTRA_RESOURCE_COMPLETED, isCompleted);
            videoResultLauncher.launch(intent);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        sheet.show(getSupportFragmentManager(), NodeDetailBottomSheet.TAG);
    }

    private String extractVideoId(String url) {
        if (url == null) return "";
        if (url.contains("youtu.be/")) {
            int start = url.indexOf("youtu.be/") + 9;
            int end = url.indexOf("?", start);
            return end < 0 ? url.substring(start) : url.substring(start, end);
        }
        if (url.contains("v=")) {
            int start = url.indexOf("v=") + 2;
            int end = url.indexOf("&", start);
            return end < 0 ? url.substring(start) : url.substring(start, end);
        }
        return "";
    }

    // ── URL opening ───────────────────────────────────────────────────────────

    private void openUrl(String url) {
        if (url == null || url.startsWith("yt:")) return;
        if (url.contains("youtube.com") || url.contains("youtu.be")) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } else {
            new CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .setUrlBarHidingEnabled(true)
                    .build()
                    .launchUrl(this, Uri.parse(url));
        }
    }

    // ── XP overlay animation ──────────────────────────────────────────────────

    private void showXpOverlay(int xp) {
        tvXpAmount.setText(String.format(Locale.getDefault(), "+%d XP", xp));
        layoutXpGain.setVisibility(View.VISIBLE);
        layoutXpGain.setAlpha(0f);
        layoutXpGain.setScaleX(0.6f);
        layoutXpGain.setScaleY(0.6f);

        AnimatorSet showSet = new AnimatorSet();
        showSet.playTogether(
                ObjectAnimator.ofFloat(layoutXpGain, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(layoutXpGain, "scaleX", 0.6f, 1f),
                ObjectAnimator.ofFloat(layoutXpGain, "scaleY", 0.6f, 1f));
        showSet.setDuration(300);
        showSet.start();

        mainHandler.postDelayed(() -> {
            ObjectAnimator fadeOut = ObjectAnimator.ofFloat(layoutXpGain, "alpha", 1f, 0f);
            fadeOut.setDuration(400);
            fadeOut.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    layoutXpGain.setVisibility(View.GONE);
                }
            });
            fadeOut.start();
        }, 2000);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void openOnboarding() {
        startActivity(new Intent(this, OnboardingActivity.class));
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
