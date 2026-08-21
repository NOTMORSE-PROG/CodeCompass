package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

public class SavedJob {

    private int id;
    private JobListing job;

    @SerializedName("saved_at")
    private String savedAt;

    private String notes;

    // ── Getters ───────────────────────────────────────────────────────────────

    public int getId() { return id; }
    public JobListing getJob() { return job; }
    public String getSavedAt() { return savedAt; }
    public String getNotes() { return notes; }
}
