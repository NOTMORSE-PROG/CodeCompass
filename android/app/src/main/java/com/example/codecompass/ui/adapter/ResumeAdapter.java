package com.example.codecompass.ui.adapter;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codecompass.R;
import com.example.codecompass.model.Resume;

import java.util.ArrayList;
import java.util.List;

public class ResumeAdapter extends RecyclerView.Adapter<ResumeAdapter.ViewHolder> {

    public interface OnResumeActionListener {
        void onResumeTap(Resume resume);
        void onResumeDelete(Resume resume);
    }

    private List<Resume> resumes = new ArrayList<>();
    private final OnResumeActionListener listener;

    public ResumeAdapter(OnResumeActionListener listener) {
        this.listener = listener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setData(List<Resume> resumes) {
        this.resumes = resumes != null ? resumes : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_resume, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(resumes.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return resumes.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        void bind(Resume resume, OnResumeActionListener listener) {
            ResumeRowBinder.bind(itemView, resume);

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onResumeTap(resume);
            });

            itemView.setOnLongClickListener(v -> {
                if (listener != null) listener.onResumeDelete(resume);
                return true;
            });
        }
    }
}
