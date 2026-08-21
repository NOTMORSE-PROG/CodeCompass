package com.example.codecompass.ui.adapter;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codecompass.R;
import com.example.codecompass.model.XPEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class XPHistoryAdapter extends RecyclerView.Adapter<XPHistoryAdapter.ViewHolder> {

    private final List<XPEvent> events = new ArrayList<>();

    @SuppressLint("NotifyDataSetChanged")
    public void setEvents(List<XPEvent> list) {
        events.clear();
        events.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_xp_event, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        XPEvent event = events.get(position);
        h.tvDesc.setText(event.getDescription());
        h.tvAmount.setText(String.format(Locale.getDefault(), "+%d XP", event.getXpEarned()));
    }

    @Override
    public int getItemCount() { return events.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDesc, tvAmount;

        ViewHolder(@NonNull View v) {
            super(v);
            tvDesc   = v.findViewById(R.id.tvXpEventDesc);
            tvAmount = v.findViewById(R.id.tvXpEventAmount);
        }
    }
}
