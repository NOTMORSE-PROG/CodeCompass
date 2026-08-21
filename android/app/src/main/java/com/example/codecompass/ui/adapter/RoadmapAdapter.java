package com.example.codecompass.ui.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codecompass.R;
import com.example.codecompass.model.RoadmapNode;
import com.example.codecompass.viewmodel.RoadmapDisplayItem;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RoadmapAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnNodeClickListener {
        void onNodeClick(RoadmapNode node);
    }

    private final List<RoadmapDisplayItem> items = new ArrayList<>();
    private OnNodeClickListener listener;

    public void setListener(OnNodeClickListener listener) {
        this.listener = listener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void submitList(List<RoadmapDisplayItem> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).getType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case RoadmapDisplayItem.TYPE_MILESTONE:
                return new MilestoneViewHolder(
                        inflater.inflate(R.layout.item_roadmap_milestone, parent, false));
            case RoadmapDisplayItem.TYPE_DIVIDER:
                return new DividerViewHolder(
                        inflater.inflate(R.layout.item_roadmap_subsection_divider, parent, false));
            case RoadmapDisplayItem.TYPE_SECTION_HEADER:
                return new SectionHeaderViewHolder(
                        inflater.inflate(R.layout.item_roadmap_milestone, parent, false));
            default: // TYPE_NODE_CARD
                return new NodeCardViewHolder(
                        inflater.inflate(R.layout.item_roadmap_node, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        RoadmapDisplayItem item = items.get(position);
        if (holder instanceof SectionHeaderViewHolder) {
            ((SectionHeaderViewHolder) holder).bind(item);
        } else if (holder instanceof MilestoneViewHolder) {
            ((MilestoneViewHolder) holder).bind(item);
        } else if (holder instanceof DividerViewHolder) {
            ((DividerViewHolder) holder).bind(item.getDividerLabel());
        } else if (holder instanceof NodeCardViewHolder) {
            NodeCardViewHolder ncvh = (NodeCardViewHolder) holder;
            ncvh.bind(item.getNode(), position);
            if (!item.getNode().isLocked()) {
                ncvh.itemView.setOnClickListener(v -> {
                    if (listener != null) listener.onNodeClick(item.getNode());
                });
                ncvh.itemView.setClickable(true);
            } else {
                ncvh.itemView.setOnClickListener(null);
                ncvh.itemView.setClickable(false);
            }
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    // ── Milestone ViewHolder ──────────────────────────────────────────────────

    static class MilestoneViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTitle;
        final TextView tvStats;
        final View lineLeft;
        final View lineRight;

        MilestoneViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle   = itemView.findViewById(R.id.tvMilestoneTitle);
            tvStats   = itemView.findViewById(R.id.tvMilestoneStats);
            lineLeft  = ((LinearLayout) tvTitle.getParent()).getChildAt(0);
            lineRight = ((LinearLayout) tvTitle.getParent()).getChildAt(2);
        }

        void bind(RoadmapDisplayItem item) {
            RoadmapNode milestone = item.getMilestone();
            Context ctx = itemView.getContext();

            // Title with optional ✓ prefix for completed phases
            String milestoneTitle = milestone.isCompleted()
                    ? ctx.getString(R.string.roadmap_milestone_completed_format, milestone.getTitle())
                    : milestone.getTitle();
            tvTitle.setText(milestoneTitle);

            // Badge color based on milestone status
            int bg, fg;
            if (milestone.isCompleted()) {
                bg = ctx.getColor(R.color.milestoneDoneBg);
                fg = ctx.getColor(R.color.milestoneDoneText);
            } else if (milestone.isAvailable() || milestone.isInProgress()) {
                bg = ctx.getColor(R.color.milestoneActiveBg);
                fg = ctx.getColor(R.color.milestoneActiveText);
            } else {
                bg = ctx.getColor(R.color.milestoneLockedBg);
                fg = ctx.getColor(R.color.milestoneLockedText);
            }
            tvTitle.setBackgroundTintList(ColorStateList.valueOf(bg));
            tvTitle.setTextColor(fg);

            // Stats line
            int total = item.getPhaseTotal();
            int done  = item.getPhaseDone();
            int xp    = item.getPhaseXpRemaining();
            if (total > 0) {
                String stats = xp > 0
                        ? String.format(Locale.getDefault(), "%d/%d done · +%d XP", done, total, xp)
                        : String.format(Locale.getDefault(), "%d/%d done", done, total);
                tvStats.setText(stats);
                tvStats.setVisibility(View.VISIBLE);
            } else {
                tvStats.setVisibility(View.GONE);
            }
        }
    }

    // ── Section Header ViewHolder (synthetic phase header — no DB milestone) ─────

    static class SectionHeaderViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTitle;
        final TextView tvStats;

        SectionHeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvMilestoneTitle);
            tvStats = itemView.findViewById(R.id.tvMilestoneStats);
        }

        void bind(RoadmapDisplayItem item) {
            Context ctx = itemView.getContext();
            String label = item.isSectionCompleted()
                    ? ctx.getString(R.string.roadmap_milestone_completed_format, item.getSectionLabel())
                    : item.getSectionLabel();
            tvTitle.setText(label);

            int bg, fg;
            if (item.isSectionCompleted()) {
                bg = ctx.getColor(R.color.milestoneDoneBg);
                fg = ctx.getColor(R.color.milestoneDoneText);
            } else {
                bg = ctx.getColor(R.color.milestoneLockedBg);
                fg = ctx.getColor(R.color.milestoneLockedText);
            }
            tvTitle.setBackgroundTintList(ColorStateList.valueOf(bg));
            tvTitle.setTextColor(fg);

            int total = item.getPhaseTotal();
            int done  = item.getPhaseDone();
            int xp    = item.getPhaseXpRemaining();
            if (total > 0) {
                String stats = xp > 0
                        ? String.format(Locale.getDefault(), "%d/%d done · +%d XP", done, total, xp)
                        : String.format(Locale.getDefault(), "%d/%d done", done, total);
                tvStats.setText(stats);
                tvStats.setVisibility(View.VISIBLE);
            } else {
                tvStats.setVisibility(View.GONE);
            }
        }
    }

    // ── Divider ViewHolder ────────────────────────────────────────────────────

    static class DividerViewHolder extends RecyclerView.ViewHolder {
        final TextView tvLabel;

        DividerViewHolder(@NonNull View itemView) {
            super(itemView);
            tvLabel = itemView.findViewById(R.id.tvDividerLabel);
        }

        void bind(String label) {
            tvLabel.setText(label);
        }
    }

    // ── Node Card ViewHolder ──────────────────────────────────────────────────

    static class NodeCardViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final FrameLayout frameIcon;
        final ImageView ivLock;
        final TextView tvOrder;
        final TextView tvCheck;
        final TextView tvTitle;
        final TextView tvTypeBadge;
        final TextView tvHours;
        final View dot1, dot2, dot3, dot4, dot5;
        final TextView tvDiffLabel;
        final TextView tvPrerequisite;
        final TextView tvStatusBadge;
        final ImageView ivChevron;

        NodeCardViewHolder(@NonNull View itemView) {
            super(itemView);
            card           = itemView.findViewById(R.id.cardNode);
            frameIcon      = itemView.findViewById(R.id.frameNodeIcon);
            ivLock         = itemView.findViewById(R.id.ivNodeLock);
            tvOrder        = itemView.findViewById(R.id.tvNodeOrder);
            tvCheck        = itemView.findViewById(R.id.tvNodeCheck);
            tvTitle        = itemView.findViewById(R.id.tvNodeTitle);
            tvTypeBadge    = itemView.findViewById(R.id.tvNodeTypeBadge);
            tvHours        = itemView.findViewById(R.id.tvNodeHours);
            dot1           = itemView.findViewById(R.id.dot1);
            dot2           = itemView.findViewById(R.id.dot2);
            dot3           = itemView.findViewById(R.id.dot3);
            dot4           = itemView.findViewById(R.id.dot4);
            dot5           = itemView.findViewById(R.id.dot5);
            tvDiffLabel    = itemView.findViewById(R.id.tvDifficultyLabel);
            tvPrerequisite = itemView.findViewById(R.id.tvPrerequisiteHint);
            tvStatusBadge  = itemView.findViewById(R.id.tvNodeStatusBadge);
            ivChevron      = itemView.findViewById(R.id.ivChevron);
        }

        void bind(RoadmapNode node, int adapterPos) {
            Context ctx = itemView.getContext();

            // Title
            tvTitle.setText(node.getTitle());

            // Hours
            tvHours.setText(ctx.getString(R.string.node_duration_format, node.getEstimatedHours()));

            // Difficulty dots
            int diff = Math.max(1, Math.min(5, node.getDifficulty()));
            View[] dots = {dot1, dot2, dot3, dot4, dot5};
            for (int i = 0; i < 5; i++) {
                dots[i].setBackgroundResource(i < diff
                        ? R.drawable.bg_circle_yellow
                        : R.drawable.bg_circle_gray);
            }
            tvDiffLabel.setText(node.getDifficultyLabel());

            // Type badge
            bindTypeBadge(node.getNodeType(), ctx);

            // Status-driven styling
            bindStatus(node, ctx);
        }

        private void bindTypeBadge(String nodeType, Context ctx) {
            int bg, fg;
            String label;
            if (nodeType == null) nodeType = RoadmapNode.TYPE_SKILL;
            switch (nodeType) {
                case RoadmapNode.TYPE_PROJECT:
                    label = "PROJECT"; bg = R.color.nodeBadgeProjectBg; fg = R.color.nodeBadgeProjectText;
                    break;
                case RoadmapNode.TYPE_CERTIFICATION:
                    label = "CERT"; bg = R.color.nodeBadgeCertBg; fg = R.color.nodeBadgeCertText;
                    break;
                case RoadmapNode.TYPE_ASSESSMENT:
                    label = "QUIZ"; bg = R.color.nodeBadgeQuizBg; fg = R.color.nodeBadgeQuizText;
                    break;
                case RoadmapNode.TYPE_FINAL_ASSESSMENT:
                    label = "FINAL"; bg = R.color.nodeBadgeQuizBg; fg = R.color.nodeBadgeQuizText;
                    break;
                default:
                    label = "SKILL"; bg = R.color.nodeBadgeSkillBg; fg = R.color.nodeBadgeSkillText;
                    break;
            }
            tvTypeBadge.setText(label);
            tvTypeBadge.setBackgroundTintList(ColorStateList.valueOf(ctx.getColor(bg)));
            tvTypeBadge.setTextColor(ctx.getColor(fg));
        }

        private void bindStatus(RoadmapNode node, Context ctx) {
            String status = node.getStatus();
            if (status == null) status = RoadmapNode.STATUS_LOCKED;

            switch (status) {
                case RoadmapNode.STATUS_LOCKED:
                    applyLockedStyle(node, ctx);
                    break;
                case RoadmapNode.STATUS_AVAILABLE:
                    applyAvailableStyle(node, ctx);
                    break;
                case RoadmapNode.STATUS_IN_PROGRESS:
                    applyInProgressStyle(node, ctx);
                    break;
                case RoadmapNode.STATUS_COMPLETED:
                    applyCompletedStyle(node, ctx);
                    break;
            }
        }

        private void applyLockedStyle(RoadmapNode node, Context ctx) {
            card.setStrokeColor(ctx.getColor(R.color.nodeLockedBorder));
            card.setStrokeWidth(dpToPx(1, ctx));
            card.setCardBackgroundColor(ctx.getColor(R.color.nodeLockedBg));
            card.setAlpha(0.6f);

            frameIcon.setBackgroundTintList(ColorStateList.valueOf(ctx.getColor(R.color.nodeIconLocked)));
            ivLock.setVisibility(View.VISIBLE);
            tvOrder.setVisibility(View.GONE);
            tvCheck.setVisibility(View.GONE);
            ivLock.setColorFilter(ctx.getColor(R.color.nodeIconLockedFg));

            tvTitle.setTextColor(ctx.getColor(R.color.nodeLockedText));
            tvStatusBadge.setText(ctx.getString(R.string.roadmap_node_status_locked));
            tvStatusBadge.setBackgroundTintList(ColorStateList.valueOf(ctx.getColor(R.color.statusLockedBg)));
            tvStatusBadge.setTextColor(ctx.getColor(R.color.statusLockedText));

            ivChevron.setVisibility(View.GONE);

            // Prerequisite hint — find parent node title from the adapter position context
            // Just show a generic locked message here
            tvPrerequisite.setVisibility(View.VISIBLE);
            tvPrerequisite.setText(R.string.node_locked_prerequisites);
        }

        private void applyAvailableStyle(RoadmapNode node, Context ctx) {
            card.setStrokeColor(ctx.getColor(R.color.nodeLockedBorder));
            card.setStrokeWidth(dpToPx(1, ctx));
            card.setCardBackgroundColor(ctx.getColor(R.color.colorSurface));
            card.setAlpha(1f);

            frameIcon.setBackgroundTintList(ColorStateList.valueOf(ctx.getColor(R.color.nodeIconAvailableBg)));
            ivLock.setVisibility(View.GONE);
            tvOrder.setVisibility(View.VISIBLE);
            tvCheck.setVisibility(View.GONE);
            tvOrder.setTextColor(ctx.getColor(R.color.nodeIconAvailableFg));

            tvTitle.setTextColor(ctx.getColor(R.color.colorTextPrimary));
            tvStatusBadge.setText(ctx.getString(R.string.roadmap_node_status_available));
            tvStatusBadge.setBackgroundTintList(ColorStateList.valueOf(ctx.getColor(R.color.colorPrimary)));
            tvStatusBadge.setTextColor(ctx.getColor(R.color.colorOnPrimary));

            tvPrerequisite.setVisibility(View.GONE);
            ivChevron.setVisibility(View.VISIBLE);
        }

        private void applyInProgressStyle(RoadmapNode node, Context ctx) {
            card.setStrokeColor(ctx.getColor(R.color.colorPrimary));
            card.setStrokeWidth(dpToPx(2, ctx));
            card.setCardBackgroundColor(ctx.getColor(R.color.colorSurface));
            card.setAlpha(1f);

            frameIcon.setBackgroundTintList(ColorStateList.valueOf(ctx.getColor(R.color.colorPrimary)));
            ivLock.setVisibility(View.GONE);
            tvOrder.setVisibility(View.VISIBLE);
            tvCheck.setVisibility(View.GONE);
            tvOrder.setTextColor(ctx.getColor(R.color.colorOnPrimary));

            tvTitle.setTextColor(ctx.getColor(R.color.colorTextPrimary));
            tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            tvStatusBadge.setText(ctx.getString(R.string.roadmap_node_status_in_progress));
            tvStatusBadge.setBackgroundTintList(ColorStateList.valueOf(ctx.getColor(R.color.colorPrimary)));
            tvStatusBadge.setTextColor(ctx.getColor(R.color.colorOnPrimary));

            tvPrerequisite.setVisibility(View.GONE);
            ivChevron.setVisibility(View.VISIBLE);
        }

        private void applyCompletedStyle(RoadmapNode node, Context ctx) {
            card.setStrokeColor(ctx.getColor(R.color.colorPrimary));
            card.setStrokeWidth(dpToPx(1, ctx));
            card.setCardBackgroundColor(ctx.getColor(R.color.nodeCompletedBg));
            card.setAlpha(1f);

            frameIcon.setBackgroundTintList(ColorStateList.valueOf(ctx.getColor(R.color.colorTextPrimary)));
            ivLock.setVisibility(View.GONE);
            tvOrder.setVisibility(View.GONE);
            tvCheck.setVisibility(View.VISIBLE);

            tvTitle.setTextColor(ctx.getColor(R.color.colorTextPrimary));
            tvTitle.setTypeface(null, android.graphics.Typeface.NORMAL);
            tvStatusBadge.setText(ctx.getString(R.string.roadmap_node_status_completed));
            tvStatusBadge.setBackgroundTintList(ColorStateList.valueOf(ctx.getColor(R.color.statusCompletedBg)));
            tvStatusBadge.setTextColor(ctx.getColor(R.color.statusCompletedText));

            tvPrerequisite.setVisibility(View.GONE);
            ivChevron.setVisibility(View.VISIBLE);
        }

        private int dpToPx(int dp, Context ctx) {
            return (int) (dp * ctx.getResources().getDisplayMetrics().density);
        }
    }
}
