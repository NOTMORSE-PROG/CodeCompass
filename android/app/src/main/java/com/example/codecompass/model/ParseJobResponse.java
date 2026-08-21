package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class ParseJobResponse {

    @SerializedName("jobTitle")
    private String jobTitle;

    @SerializedName("requiredSkills")
    private List<String> requiredSkills;

    @SerializedName("niceToHaveSkills")
    private List<String> niceToHaveSkills;

    @SerializedName("keywords")
    private List<String> keywords;

    @SerializedName("experienceLevel")
    private String experienceLevel;

    @SerializedName("responsibilities")
    private List<String> responsibilities;

    public String getJobTitle() { return jobTitle; }
    public List<String> getRequiredSkills() { return requiredSkills; }
    public List<String> getNiceToHaveSkills() { return niceToHaveSkills; }
    public List<String> getKeywords() { return keywords; }
    public String getExperienceLevel() { return experienceLevel; }
    public List<String> getResponsibilities() { return responsibilities; }
}
