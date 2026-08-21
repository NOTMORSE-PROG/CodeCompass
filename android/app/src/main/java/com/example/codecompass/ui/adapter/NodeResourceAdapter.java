package com.example.codecompass.ui.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codecompass.R;
import com.example.codecompass.model.roadmap.NodeResource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NodeResourceAdapter extends RecyclerView.Adapter<NodeResourceAdapter.ResourceViewHolder> {

    public interface OnResourceClickListener {
        void onResourceClick(NodeResource resource, int position);
    }

    private final List<NodeResource> resources = new ArrayList<>();
    /** IDs of resources the user has marked as completed (visited / quiz passed). */
    private final Set<Integer> completedIds = new HashSet<>();
    /** Currently active (selected) resource index. */
    private int activeIndex = 0;
    private OnResourceClickListener listener;

    public void setListener(OnResourceClickListener listener) {
        this.listener = listener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setResources(List<NodeResource> newResources) {
        resources.clear();
        if (newResources != null) resources.addAll(newResources);
        notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void markCompleted(int resourceId) {
        completedIds.add(resourceId);
        notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setActiveIndex(int index) {
        this.activeIndex = index;
        notifyDataSetChanged();
    }

    public int getActiveIndex() { return activeIndex; }

    public boolean allCompleted() {
        if (resources.isEmpty()) return true;
        for (NodeResource r : resources) {
            if (!completedIds.contains(r.getId())) return false;
        }
        return true;
    }

    public int getCompletedCount() { return completedIds.size(); }

    /** Returns a copy of the currently completed resource ID set (for SharedPrefs persistence). */
    public Set<Integer> getCompletedIds() { return new HashSet<>(completedIds); }

    /** Restores the completed-IDs set (called from SharedPrefs on sheet open). */
    @SuppressLint("NotifyDataSetChanged")
    public void restoreCompletedIds(Set<Integer> ids) {
        completedIds.clear();
        if (ids != null) completedIds.addAll(ids);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ResourceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_node_resource, parent, false);
        return new ResourceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ResourceViewHolder holder, int position) {
        NodeResource resource = resources.get(position);
        Context ctx = holder.itemView.getContext();

        boolean isDone   = completedIds.contains(resource.getId());
        boolean isActive = (position == activeIndex);

        holder.bind(resource, position, isDone, isActive, ctx, listener);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onResourceClick(resource, position);
        });
    }

    @Override
    public int getItemCount() { return resources.size(); }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    static class ResourceViewHolder extends RecyclerView.ViewHolder {
        final View rootRow;
        final View viewBorder;
        final TextView tvIndex;
        final TextView tvTypeBadge;
        final TextView tvTitle;
        final TextView tvChannel;
        final TextView tvDuration;
        final TextView tvStatus;

        ResourceViewHolder(@NonNull View itemView) {
            super(itemView);
            rootRow        = itemView.findViewById(R.id.rootResourceRow);
            viewBorder     = itemView.findViewById(R.id.viewResourceBorder);
            tvIndex        = itemView.findViewById(R.id.tvResourceIndex);
            tvTypeBadge    = itemView.findViewById(R.id.tvResourceTypeBadge);
            tvTitle        = itemView.findViewById(R.id.tvResourceTitle);
            tvChannel      = itemView.findViewById(R.id.tvResourceChannel);
            tvDuration     = itemView.findViewById(R.id.tvResourceDuration);
            tvStatus       = itemView.findViewById(R.id.tvResourceStatus);
        }

        void bind(NodeResource r, int pos, boolean isDone, boolean isActive,
                  Context ctx, OnResourceClickListener listener) {
            tvIndex.setText(String.format(java.util.Locale.getDefault(), "%02d", pos + 1));
            tvTitle.setText(r.getTitle());

            // Type badge
            tvTypeBadge.setText(r.getTypeBadge());
            applyTypeBadgeStyle(r.getResourceType(), ctx);

            // Unavailable resource defensive styling. The bottom sheet auto-marks
            // unavailable rows done on display, so this branch is normally skipped
            // (the isDone branch below fires instead), but keep the styling as a
            // fallback in case of re-bind races.
            if (r.isUnavailable() && !isDone) {
                tvDuration.setText(ctx.getString(R.string.roadmap_resource_unavailable));
                tvDuration.setVisibility(View.VISIBLE);
                tvStatus.setVisibility(View.GONE);
                rootRow.setBackgroundColor(ctx.getColor(R.color.colorSurface));
                viewBorder.setBackgroundColor(ctx.getColor(R.color.colorSurface));
                tvTitle.setTextColor(ctx.getColor(R.color.colorTextSecondary));
                tvTitle.setTypeface(null, android.graphics.Typeface.NORMAL);
                tvChannel.setVisibility(View.GONE);
                return;
            }

            // Duration
            String dur = r.getDurationLabel();
            tvDuration.setText(dur.isEmpty() ? "" : dur);
            tvDuration.setVisibility(dur.isEmpty() ? View.GONE : View.VISIBLE);

            // Channel (YouTube only)
            String channel = r.getYoutubeChannel();
            if (channel != null && !channel.isEmpty()) {
                tvChannel.setText(channel);
                tvChannel.setVisibility(View.VISIBLE);
            } else {
                tvChannel.setVisibility(View.GONE);
            }

            // Row background + border based on state
            tvStatus.setVisibility(View.VISIBLE);
            if (isDone) {
                rootRow.setBackgroundColor(ctx.getColor(R.color.resourceRowDoneBg));
                viewBorder.setBackgroundColor(ctx.getColor(R.color.resourceRowDoneBorder));
                tvStatus.setText("✓");
                tvStatus.setTextColor(ctx.getColor(R.color.completedGreen));
                tvTitle.setTextColor(ctx.getColor(R.color.colorTextSecondary));
                tvTitle.setTypeface(null, android.graphics.Typeface.NORMAL);
            } else if (isActive) {
                rootRow.setBackgroundColor(ctx.getColor(R.color.resourceRowActiveBg));
                viewBorder.setBackgroundColor(ctx.getColor(R.color.resourceRowActiveBorder));
                tvStatus.setText("▶");
                tvStatus.setTextColor(ctx.getColor(R.color.colorPrimary));
                tvTitle.setTextColor(ctx.getColor(R.color.colorTextPrimary));
                tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                rootRow.setBackgroundColor(ctx.getColor(R.color.colorSurface));
                viewBorder.setBackgroundColor(ctx.getColor(R.color.colorSurface));
                tvStatus.setText("○");
                tvStatus.setTextColor(ctx.getColor(R.color.textSecondaryLight));
                tvTitle.setTextColor(ctx.getColor(R.color.colorTextPrimary));
                tvTitle.setTypeface(null, android.graphics.Typeface.NORMAL);
            }
        }

        private void applyTypeBadgeStyle(String resourceType, Context ctx) {
            if (resourceType == null) resourceType = "";
            int bg, fg;
            switch (resourceType) {
                case NodeResource.TYPE_YOUTUBE_VIDEO:
                case NodeResource.TYPE_YOUTUBE_PLAYLIST:
                    bg = ctx.getColor(R.color.resourceYtBg);
                    fg = ctx.getColor(R.color.resourceYtText);
                    break;
                case NodeResource.TYPE_ARTICLE:
                    bg = ctx.getColor(R.color.resourceArticleBg);
                    fg = ctx.getColor(R.color.resourceArticleText);
                    break;
                case NodeResource.TYPE_DOCUMENTATION:
                    bg = ctx.getColor(R.color.resourceDocBg);
                    fg = ctx.getColor(R.color.resourceDocText);
                    break;
                case NodeResource.TYPE_COURSE:
                    bg = ctx.getColor(R.color.resourceCourseBg);
                    fg = ctx.getColor(R.color.resourceCourseText);
                    break;
                case NodeResource.TYPE_GITHUB_REPO:
                    bg = ctx.getColor(R.color.resourceGithubBg);
                    fg = ctx.getColor(R.color.resourceGithubText);
                    break;
                default:
                    bg = ctx.getColor(R.color.resourceGenericBg);
                    fg = ctx.getColor(R.color.resourceGenericText);
                    break;
            }
            tvTypeBadge.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(bg));
            tvTypeBadge.setTextColor(fg);
        }
    }
}
