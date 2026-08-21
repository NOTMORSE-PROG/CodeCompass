package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class Roadmap {

    @SerializedName("id")
    private int id;

    @SerializedName("title")
    private String title;

    @SerializedName("careerPath")
    private String careerPath;

    @SerializedName("description")
    private String description;

    @SerializedName("status")
    private String status;

    @SerializedName("completionPercentage")
    private double completionPercentage;

    @SerializedName("estimatedWeeks")
    private int estimatedWeeks;

    @SerializedName("isPrimary")
    private boolean isPrimary;

    @SerializedName("generatedAt")
    private String generatedAt;

    @SerializedName("createdAt")
    private String createdAt;

    @SerializedName("updatedAt")
    private String updatedAt;

    @SerializedName("nodes")
    private List<RoadmapNode> nodes;

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getCareerPath() { return careerPath; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }

    /** Returns completion as an int 0-100 for progress bars / display. */
    public int getCompletionPercentage() { return (int) completionPercentage; }
    public double getCompletionPercentageExact() { return completionPercentage; }
    public void setCompletionPercentage(int pct) { this.completionPercentage = pct; }

    public int getEstimatedWeeks() { return estimatedWeeks; }
    public boolean isPrimary() { return isPrimary; }
    public String getGeneratedAt() { return generatedAt; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public List<RoadmapNode> getNodes() { return nodes; }
}
