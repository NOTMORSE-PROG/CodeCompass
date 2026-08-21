package com.example.codecompass.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codecompass.R;
import com.example.codecompass.model.RoadmapNode;
import com.example.codecompass.model.roadmap.NodeResource;
import com.example.codecompass.repository.RoadmapRepository;
import com.example.codecompass.ui.adapter.NodeResourceAdapter;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class NodeDetailBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "NodeDetailBottomSheet";
    private static final String ARG_NODE    = "node";
    private static final String ARG_ROADMAP = "roadmap_id";
    private static final String PREFS_NAME  = "cc_roadmap_progress";

    // ── Callbacks ─────────────────────────────────────────────────────────────

    public interface OnNodeCompletedListener {
        void onNodeStatusChange(int nodeId, String newStatus);
    }

    public interface OnOpenResourceListener {
        void onOpenResource(String url);
    }

    /** Called when user taps a YouTube resource — opens VideoLearningActivity. */
    public interface OnOpenVideoResourceListener {
        void onOpenVideoResource(NodeResource resource, int nodeId, boolean isCompleted);
    }

    private OnNodeCompletedListener    completedListener;
    private OnOpenResourceListener     openResourceListener;
    private OnOpenVideoResourceListener openVideoListener;

    public void setOnNodeCompletedListener(OnNodeCompletedListener l)       { completedListener    = l; }
    public void setOnOpenResourceListener(OnOpenResourceListener l)         { openResourceListener = l; }
    public void setOnOpenVideoResourceListener(OnOpenVideoResourceListener l){ openVideoListener    = l; }

    // ── Factory ───────────────────────────────────────────────────────────────

    public static NodeDetailBottomSheet newInstance(RoadmapNode node, int roadmapId) {
        NodeDetailBottomSheet sheet = new NodeDetailBottomSheet();
        Bundle args = new Bundle();
        args.putSerializable(ARG_NODE, node);
        args.putInt(ARG_ROADMAP, roadmapId);
        sheet.setArguments(args);
        return sheet;
    }

    // ── Views ─────────────────────────────────────────────────────────────────

    private FrameLayout frameIcon;
    private ImageView ivLock;
    private TextView tvOrder;
    private TextView tvCheck;
    private TextView tvTypeBadge;
    private TextView tvStatusBadge;
    private TextView tvTitle;
    private TextView tvHours;
    private View bsDot1, bsDot2, bsDot3, bsDot4, bsDot5;
    private TextView tvDiffLabel;
    private TextView tvXpReward;
    private TextView tvDescription;
    private View dividerCurriculum;
    private LinearLayout layoutCurriculumHeader;
    private TextView tvCurriculumProgress;
    private ProgressBar progressCurriculum;
    private LinearLayout layoutFetchingResources;
    private LinearLayout layoutQuizGenerating;
    private RecyclerView recyclerResources;
    private Button btnMarkComplete;
    private TextView tvQuizGateInfo;
    private TextView tvCompletedDate;

    private NodeResourceAdapter resourceAdapter;
    private RoadmapNode node;
    private int roadmapId;
    /** Resource currently being opened — for associating quiz with its resource. */
    private int activeResourceId = -1;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_node_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() == null) { dismiss(); return; }
        node      = (RoadmapNode) getArguments().getSerializable(ARG_NODE);
        roadmapId = getArguments().getInt(ARG_ROADMAP, -1);
        if (node == null) { dismiss(); return; }

        bindViews(view);
        setupResourceRecycler();
        bindNodeData();
        fetchResourcesIfNeeded();
    }

    // ── Public API (called by RoadmapActivity) ────────────────────────────────

    /**
     * Called when a quiz for a resource is passed.
     * Marks the resource as completed, updates progress, and enables Mark Complete if all done.
     */
    public void onResourceQuizPassed(int resourceId) {
        if (!isAdded()) return;
        resourceAdapter.markCompleted(resourceId);
        saveCompletedIdsPref(node.getId(), resourceAdapter.getCompletedIds());
        updateCurriculumProgress();
        showMarkCompleteButton(resourceAdapter.allCompleted());
    }

    // ── Binding ───────────────────────────────────────────────────────────────

    private void bindViews(View root) {
        frameIcon              = root.findViewById(R.id.frameNodeIconBs);
        ivLock                 = root.findViewById(R.id.ivNodeLockBs);
        tvOrder                = root.findViewById(R.id.tvNodeOrderBs);
        tvCheck                = root.findViewById(R.id.tvNodeCheckBs);
        tvTypeBadge            = root.findViewById(R.id.tvNodeTypeBadgeBs);
        tvStatusBadge          = root.findViewById(R.id.tvNodeStatusBadgeBs);
        tvTitle                = root.findViewById(R.id.tvNodeTitleBs);
        tvHours                = root.findViewById(R.id.tvNodeHoursBs);
        bsDot1                 = root.findViewById(R.id.bsDot1);
        bsDot2                 = root.findViewById(R.id.bsDot2);
        bsDot3                 = root.findViewById(R.id.bsDot3);
        bsDot4                 = root.findViewById(R.id.bsDot4);
        bsDot5                 = root.findViewById(R.id.bsDot5);
        tvDiffLabel            = root.findViewById(R.id.tvDifficultyLabelBs);
        tvXpReward             = root.findViewById(R.id.tvXpRewardBs);
        tvDescription          = root.findViewById(R.id.tvNodeDescriptionBs);
        dividerCurriculum      = root.findViewById(R.id.dividerCurriculum);
        layoutCurriculumHeader = root.findViewById(R.id.layoutCurriculumHeader);
        tvCurriculumProgress   = root.findViewById(R.id.tvCurriculumProgress);
        progressCurriculum     = root.findViewById(R.id.progressCurriculum);
        layoutFetchingResources= root.findViewById(R.id.layoutFetchingResources);
        layoutQuizGenerating   = root.findViewById(R.id.layoutQuizGenerating);
        recyclerResources      = root.findViewById(R.id.recyclerResources);
        btnMarkComplete        = root.findViewById(R.id.btnMarkComplete);
        tvQuizGateInfo         = root.findViewById(R.id.tvQuizGateInfo);
        tvCompletedDate        = root.findViewById(R.id.tvCompletedDate);
    }

    private void setupResourceRecycler() {
        resourceAdapter = new NodeResourceAdapter();
        resourceAdapter.setListener((resource, position) -> handleResourceTap(resource, position));
        recyclerResources.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerResources.setAdapter(resourceAdapter);
    }

    private void bindNodeData() {
        applyIconState();
        applyTypeBadge();
        applyStatusBadge();

        tvTitle.setText(node.getTitle());
        tvHours.setText(getString(R.string.node_duration_format, node.getEstimatedHours()));

        int diff = Math.max(1, Math.min(5, node.getDifficulty()));
        View[] dots = {bsDot1, bsDot2, bsDot3, bsDot4, bsDot5};
        for (int i = 0; i < 5; i++) {
            dots[i].setBackgroundResource(i < diff
                    ? R.drawable.bg_circle_yellow
                    : R.drawable.bg_circle_gray);
        }
        tvDiffLabel.setText(node.getDifficultyLabel());

        tvXpReward.setText(String.format(Locale.getDefault(), "+%d XP", node.getXpReward()));

        if (node.getDescription() != null && !node.getDescription().isEmpty()) {
            tvDescription.setText(node.getDescription());
            tvDescription.setVisibility(View.VISIBLE);
        } else {
            tvDescription.setVisibility(View.GONE);
        }

        if (node.isCompleted() && node.getCompletedAt() != null) {
            tvCompletedDate.setText(getString(R.string.node_completed_on_format, formatDate(node.getCompletedAt())));
            tvCompletedDate.setVisibility(View.VISIBLE);
            btnMarkComplete.setVisibility(View.GONE);
            tvQuizGateInfo.setVisibility(View.GONE);
        } else if (node.isAvailable() || node.isInProgress()) {
            tvCompletedDate.setVisibility(View.GONE);
            // Mark complete visibility determined after resources are fetched
        } else {
            tvCompletedDate.setVisibility(View.GONE);
            btnMarkComplete.setVisibility(View.GONE);
            tvQuizGateInfo.setVisibility(View.GONE);
        }

        btnMarkComplete.setOnClickListener(v -> {
            btnMarkComplete.setEnabled(false);
            btnMarkComplete.setText(R.string.roadmap_marking);
            if (completedListener != null) {
                completedListener.onNodeStatusChange(node.getId(), RoadmapNode.STATUS_COMPLETED);
            }
            dismiss();
        });
    }

    // ── Resource fetching ─────────────────────────────────────────────────────

    private void fetchResourcesIfNeeded() {
        if (!node.isAvailable() && !node.isInProgress() && !node.isCompleted()) return;
        if (roadmapId < 0) return;

        if (node.areResourcesFetched() && node.getResources() != null) {
            showResources(node.getResources());
            return;
        }

        layoutFetchingResources.setVisibility(View.VISIBLE);

        RoadmapRepository repo = new RoadmapRepository(requireActivity().getApplication());
        repo.fetchNodeResources(roadmapId, node.getId(),
                new RoadmapRepository.Callback1<List<NodeResource>>() {
                    @Override
                    public void onSuccess(List<NodeResource> data) {
                        if (!isAdded()) return;
                        layoutFetchingResources.setVisibility(View.GONE);
                        showResources(data);
                    }

                    @Override
                    public void onError(String message) {
                        if (!isAdded()) return;
                        layoutFetchingResources.setVisibility(View.GONE);
                        showMarkCompleteButton(false);
                    }
                });
    }

    private void showResources(List<NodeResource> resources) {
        if (resources == null || resources.isEmpty()) {
            showMarkCompleteButton(false);
            return;
        }

        dividerCurriculum.setVisibility(View.VISIBLE);
        layoutCurriculumHeader.setVisibility(View.VISIBLE);

        resourceAdapter.setResources(resources);

        // Restore SharedPrefs: completed IDs + active index
        resourceAdapter.restoreCompletedIds(restoreCompletedIdsPref(node.getId()));
        int savedActive = restoreActiveIndexPref(node.getId());
        if (savedActive < resources.size()) {
            resourceAdapter.setActiveIndex(savedActive);
        }

        // Auto-mark unavailable resources done — there's nothing to watch.
        // Mirrors the web useEffect and handleResourceTap's isUnavailable branch.
        boolean anyAutoMarked = false;
        Set<Integer> completed = resourceAdapter.getCompletedIds();
        for (NodeResource r : resources) {
            if (r.isUnavailable() && !completed.contains(r.getId())) {
                resourceAdapter.markCompleted(r.getId());
                anyAutoMarked = true;
            }
        }
        if (anyAutoMarked) {
            saveCompletedIdsPref(node.getId(), resourceAdapter.getCompletedIds());
        }

        updateCurriculumProgress();

        boolean hasYoutube = node.hasYouTubeResources();
        showMarkCompleteButton(!hasYoutube || resourceAdapter.allCompleted());
        if (hasYoutube && !resourceAdapter.allCompleted() && !node.isCompleted()) {
            tvQuizGateInfo.setVisibility(View.VISIBLE);
        }
    }

    private void updateCurriculumProgress() {
        int total = resourceAdapter.getItemCount();
        int done  = resourceAdapter.getCompletedCount();
        tvCurriculumProgress.setText(String.format(Locale.getDefault(), "%d/%d done", done, total));
        int progress = total > 0 ? (done * 100 / total) : 0;
        progressCurriculum.setProgress(progress);
    }

    private void showMarkCompleteButton(boolean canComplete) {
        if (node.isCompleted()) return;
        if (!node.isAvailable() && !node.isInProgress()) return;

        if (canComplete) {
            btnMarkComplete.setVisibility(View.VISIBLE);
            tvQuizGateInfo.setVisibility(View.GONE);
        } else {
            btnMarkComplete.setVisibility(View.GONE);
            tvQuizGateInfo.setVisibility(View.VISIBLE);
        }
    }

    // ── Resource tap ──────────────────────────────────────────────────────────

    private void handleResourceTap(NodeResource resource, int position) {
        if (resource.isPlaceholder()) return;

        // Unavailable: mark visited immediately
        if (resource.isUnavailable()) {
            resourceAdapter.markCompleted(resource.getId());
            saveCompletedIdsPref(node.getId(), resourceAdapter.getCompletedIds());
            updateCurriculumProgress();
            showMarkCompleteButton(resourceAdapter.allCompleted());
            return;
        }

        resourceAdapter.setActiveIndex(position);
        saveActiveIndexPref(node.getId(), position);
        activeResourceId = resource.getId();

        if (resource.isYouTube()) {
            // Open VideoLearningActivity — quiz handled in-app at 60 % watch.
            // Re-watch mode: if the resource (or node) is already completed, the activity
            // hides all quiz/progress UI so the user can just review the video.
            if (openVideoListener != null) {
                boolean isResourceCompleted =
                        resourceAdapter.getCompletedIds().contains(resource.getId())
                                || node.isCompleted();
                openVideoListener.onOpenVideoResource(resource, node.getId(), isResourceCompleted);
            }
        } else {
            // Non-YouTube: open URL in browser/custom tabs + mark completed on tap
            String url = resource.getUrl();
            if (url != null && !url.isEmpty() && openResourceListener != null) {
                openResourceListener.onOpenResource(url);
            }
            resourceAdapter.markCompleted(resource.getId());
            saveCompletedIdsPref(node.getId(), resourceAdapter.getCompletedIds());
            updateCurriculumProgress();
            showMarkCompleteButton(resourceAdapter.allCompleted());
        }
    }

    // ── SharedPreferences ─────────────────────────────────────────────────────

    private SharedPreferences getPrefs() {
        return requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private void saveActiveIndexPref(int nodeId, int index) {
        getPrefs().edit().putInt("cc_node_" + nodeId + "_active", index).apply();
    }

    private int restoreActiveIndexPref(int nodeId) {
        return getPrefs().getInt("cc_node_" + nodeId + "_active", 0);
    }

    private void saveCompletedIdsPref(int nodeId, Set<Integer> ids) {
        Set<String> strIds = new HashSet<>();
        for (int id : ids) strIds.add(String.valueOf(id));
        getPrefs().edit().putStringSet("cc_node_" + nodeId + "_done", strIds).apply();
    }

    private Set<Integer> restoreCompletedIdsPref(int nodeId) {
        Set<String> strIds = getPrefs().getStringSet("cc_node_" + nodeId + "_done", new HashSet<>());
        Set<Integer> ids = new HashSet<>();
        for (String s : strIds) {
            try { ids.add(Integer.parseInt(s)); } catch (NumberFormatException ignored) {}
        }
        return ids;
    }

    // ── Style helpers ─────────────────────────────────────────────────────────

    private void applyIconState() {
        if (node.isCompleted()) {
            frameIcon.setBackgroundTintList(ColorStateList.valueOf(
                    requireContext().getColor(R.color.colorTextPrimary)));
            ivLock.setVisibility(View.GONE);
            tvOrder.setVisibility(View.GONE);
            tvCheck.setVisibility(View.VISIBLE);
        } else if (node.isInProgress()) {
            frameIcon.setBackgroundTintList(ColorStateList.valueOf(
                    requireContext().getColor(R.color.colorPrimary)));
            ivLock.setVisibility(View.GONE);
            tvOrder.setVisibility(View.VISIBLE);
            tvCheck.setVisibility(View.GONE);
            tvOrder.setTextColor(requireContext().getColor(R.color.colorOnPrimary));
        } else if (node.isAvailable()) {
            frameIcon.setBackgroundTintList(ColorStateList.valueOf(
                    requireContext().getColor(R.color.nodeIconAvailableBg)));
            ivLock.setVisibility(View.GONE);
            tvOrder.setVisibility(View.VISIBLE);
            tvCheck.setVisibility(View.GONE);
            tvOrder.setTextColor(requireContext().getColor(R.color.nodeIconAvailableFg));
        } else {
            frameIcon.setBackgroundTintList(ColorStateList.valueOf(
                    requireContext().getColor(R.color.nodeIconLocked)));
            ivLock.setVisibility(View.VISIBLE);
            tvOrder.setVisibility(View.GONE);
            tvCheck.setVisibility(View.GONE);
        }
    }

    private void applyTypeBadge() {
        String type = node.getNodeType();
        if (type == null) type = RoadmapNode.TYPE_SKILL;
        int bg, fg;
        String label;
        switch (type) {
            case RoadmapNode.TYPE_PROJECT:
                label = "PROJECT"; bg = R.color.nodeBadgeProjectBg; fg = R.color.nodeBadgeProjectText;
                break;
            case RoadmapNode.TYPE_CERTIFICATION:
                label = "CERT"; bg = R.color.nodeBadgeCertBg; fg = R.color.nodeBadgeCertText;
                break;
            case RoadmapNode.TYPE_ASSESSMENT:
                label = "QUIZ"; bg = R.color.nodeBadgeQuizBg; fg = R.color.nodeBadgeQuizText;
                break;
            default:
                label = "SKILL"; bg = R.color.nodeBadgeSkillBg; fg = R.color.nodeBadgeSkillText;
                break;
        }
        tvTypeBadge.setText(label);
        tvTypeBadge.setBackgroundTintList(ColorStateList.valueOf(requireContext().getColor(bg)));
        tvTypeBadge.setTextColor(requireContext().getColor(fg));
    }

    private void applyStatusBadge() {
        String status = node.getStatus();
        if (status == null) status = RoadmapNode.STATUS_LOCKED;
        int bg, fg;
        String label;
        switch (status) {
            case RoadmapNode.STATUS_IN_PROGRESS:
                label = getString(R.string.roadmap_node_status_in_progress);
                bg = R.color.colorPrimary; fg = R.color.colorOnPrimary;
                break;
            case RoadmapNode.STATUS_COMPLETED:
                label = getString(R.string.roadmap_node_status_completed);
                bg = R.color.statusCompletedBg; fg = R.color.statusCompletedText;
                break;
            case RoadmapNode.STATUS_AVAILABLE:
                label = getString(R.string.roadmap_node_status_available);
                bg = R.color.colorPrimary; fg = R.color.colorOnPrimary;
                break;
            default:
                label = getString(R.string.roadmap_node_status_locked);
                bg = R.color.statusLockedBg; fg = R.color.statusLockedText;
                break;
        }
        tvStatusBadge.setText(label);
        tvStatusBadge.setBackgroundTintList(ColorStateList.valueOf(requireContext().getColor(bg)));
        tvStatusBadge.setTextColor(requireContext().getColor(fg));
    }

    private String formatDate(String isoDate) {
        if (isoDate == null || isoDate.length() < 10) return isoDate;
        try {
            String[] parts = isoDate.substring(0, 10).split("-");
            String[] months = {"Jan","Feb","Mar","Apr","May","Jun",
                               "Jul","Aug","Sep","Oct","Nov","Dec"};
            int m = Integer.parseInt(parts[1]) - 1;
            return months[m] + " " + parts[2] + ", " + parts[0];
        } catch (Exception e) {
            return isoDate.substring(0, 10);
        }
    }
}
