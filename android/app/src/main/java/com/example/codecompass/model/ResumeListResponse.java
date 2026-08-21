package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class ResumeListResponse {

    @SerializedName("count")
    private int count;

    @SerializedName("results")
    private List<Resume> results;

    public int getCount() { return count; }
    public List<Resume> getResults() { return results != null ? results : new java.util.ArrayList<>(); }
}
