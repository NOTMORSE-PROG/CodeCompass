package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class JobListing {

    private int id;
    private String title;
    private String company;
    private String location;

    @SerializedName("job_type")
    private String jobType;

    @SerializedName("salary_range")
    private String salaryRange;

    private String description;

    @SerializedName("apply_url")
    private String applyUrl;

    @SerializedName("required_skills")
    private List<String> requiredSkills;

    @SerializedName("experience_level")
    private String experienceLevel;

    @SerializedName("is_philippines_based")
    private boolean isPhilippinesBased;

    @SerializedName("fetched_at")
    private String fetchedAt;

    @SerializedName("match_terms")
    private List<String> matchTerms;

    // ── Getters ───────────────────────────────────────────────────────────────

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getCompany() { return company; }
    public String getLocation() { return location; }
    public String getJobType() { return jobType; }
    public String getSalaryRange() { return salaryRange; }
    public String getDescription() { return description; }
    public String getApplyUrl() { return applyUrl; }
    public List<String> getRequiredSkills() { return requiredSkills; }
    public String getExperienceLevel() { return experienceLevel; }
    public boolean isPhilippinesBased() { return isPhilippinesBased; }
    public String getFetchedAt() { return fetchedAt; }
    public List<String> getMatchTerms() { return matchTerms; }
}
