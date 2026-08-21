package com.example.codecompass.ui.adapter;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codecompass.R;
import com.example.codecompass.model.Badge;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BadgeAdapter extends RecyclerView.Adapter<BadgeAdapter.ViewHolder> {

    private final List<Badge> badges = new ArrayList<>();
    private final Set<String> earnedSlugs = new HashSet<>();

    @SuppressLint("NotifyDataSetChanged")
    public void setData(List<Badge> allBadges, Set<String> slugs) {
        badges.clear();
        badges.addAll(allBadges);
        earnedSlugs.clear();
        earnedSlugs.addAll(slugs);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_badge, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        Badge badge = badges.get(position);
        boolean earned = earnedSlugs.contains(badge.getSlug());

        h.tvIcon.setText(iconEmoji(badge.getIconName()));
        h.tvName.setText(badge.getName());
        h.tvDesc.setText(badge.getDescription());
        h.ivLock.setVisibility(earned ? View.GONE : View.VISIBLE);

        if (earned) {
            h.card.setStrokeColor(h.card.getContext().getColor(R.color.colorPrimary));
            h.card.setCardBackgroundColor(h.card.getContext().getColor(R.color.colorBrandYellowPale));
            h.tvName.setTextColor(h.tvName.getContext().getColor(R.color.colorTextPrimary));
            h.tvDesc.setTextColor(h.tvDesc.getContext().getColor(R.color.colorTextSecondary));
            h.card.setAlpha(1f);
        } else {
            h.card.setStrokeColor(h.card.getContext().getColor(R.color.colorDivider));
            h.card.setCardBackgroundColor(h.card.getContext().getColor(R.color.colorSurface));
            h.tvName.setTextColor(h.tvName.getContext().getColor(R.color.colorTextSecondary));
            h.tvDesc.setTextColor(h.tvDesc.getContext().getColor(R.color.colorTextSecondary));
            h.card.setAlpha(0.5f);
        }
    }

    @Override
    public int getItemCount() { return badges.size(); }

    private String iconEmoji(String iconName) {
        if (iconName == null) return "🏅";
        switch (iconName) {
            case "star":        return "⭐";
            case "fire":        return "🔥";
            case "trophy":      return "🏆";
            case "book":        return "📖";
            case "check":       return "✅";
            case "lightning":   return "⚡";
            case "medal":       return "🥇";
            case "rocket":      return "🚀";
            case "crown":       return "👑";
            case "target":      return "🎯";
            default:            return "🏅";
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView card;
        TextView tvIcon, tvName, tvDesc;
        ImageView ivLock;

        ViewHolder(@NonNull View v) {
            super(v);
            card   = v.findViewById(R.id.cardBadge);
            tvIcon = v.findViewById(R.id.tvBadgeIcon);
            tvName = v.findViewById(R.id.tvBadgeName);
            tvDesc = v.findViewById(R.id.tvBadgeDesc);
            ivLock = v.findViewById(R.id.ivBadgeLock);
        }
    }
}
