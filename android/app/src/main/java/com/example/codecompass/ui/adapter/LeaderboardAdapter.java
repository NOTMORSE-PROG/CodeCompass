package com.example.codecompass.ui.adapter;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codecompass.R;
import com.example.codecompass.model.LeaderboardEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardAdapter.ViewHolder> {

    private final List<LeaderboardEntry> entries = new ArrayList<>();
    private String currentUserName = "";

    @SuppressLint("NotifyDataSetChanged")
    public void setEntries(List<LeaderboardEntry> list, String myName) {
        entries.clear();
        entries.addAll(list);
        this.currentUserName = myName != null ? myName : "";
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_leaderboard_entry, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        LeaderboardEntry entry = entries.get(position);
        int rank = entry.getRank();

        h.tvRank.setText(String.valueOf(rank));
        String name = entry.getUser() != null ? entry.getUser().getFullName() : "";
        if (name == null || name.isEmpty()) name = "User #" + rank;
        h.tvName.setText(name);
        h.tvXp.setText(String.format(Locale.getDefault(), "%,d XP", entry.getXpEarned()));

        // Rank badge colors
        int bgColor;
        int textColor;
        switch (rank) {
            case 1:
                bgColor   = h.tvRank.getContext().getColor(R.color.colorPrimary);
                textColor = h.tvRank.getContext().getColor(R.color.colorOnPrimary);
                break;
            case 2:
                bgColor   = 0xFFB0B0B0; // silver
                textColor = 0xFFFFFFFF;
                break;
            case 3:
                bgColor   = 0xFFF97316; // orange
                textColor = 0xFFFFFFFF;
                break;
            default:
                bgColor   = h.tvRank.getContext().getColor(R.color.colorDivider);
                textColor = h.tvRank.getContext().getColor(R.color.colorTextSecondary);
                break;
        }
        h.tvRank.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(bgColor));
        h.tvRank.setTextColor(textColor);

        // Highlight current user
        boolean isMe = !currentUserName.isEmpty() && currentUserName.equals(name);
        h.row.setBackgroundColor(isMe
                ? h.row.getContext().getColor(R.color.colorBrandYellowPale)
                : 0x00000000);
    }

    @Override
    public int getItemCount() { return entries.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        LinearLayout row;
        TextView tvRank, tvName, tvXp;

        ViewHolder(@NonNull View v) {
            super(v);
            row    = v.findViewById(R.id.rowLeaderboard);
            tvRank = v.findViewById(R.id.tvRank);
            tvName = v.findViewById(R.id.tvLeaderboardName);
            tvXp   = v.findViewById(R.id.tvLeaderboardXp);
        }
    }
}
