package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class Certification {

    @SerializedName("id")
    private int id;

    @SerializedName("name")
    private String name;

    @SerializedName("abbreviation")
    private String abbreviation;

    @SerializedName("provider")
    private String provider;

    @SerializedName("level")
    private String level;

    @SerializedName("levelDisplay")
    private String levelDisplay;

    @SerializedName("track")
    private String track;

    @SerializedName("trackDisplay")
    private String trackDisplay;

    @SerializedName("description")
    private String description;

    @SerializedName("relevantSkills")
    private List<String> relevantSkills;

    @SerializedName("careerPaths")
    private List<String> careerPaths;

    @SerializedName("examUrl")
    private String examUrl;

    @SerializedName("studyGuideUrl")
    private String studyGuideUrl;

    @SerializedName("tesdaNcLevel")
    private String tesdaNcLevel;

    @SerializedName("isFree")
    private boolean isFree;

    @SerializedName("optionalPaidUpgrade")
    private String optionalPaidUpgrade;

    @SerializedName("estimatedCostPhp")
    private Integer estimatedCostPhp;

    @SerializedName("estimatedStudyHours")
    private Integer estimatedStudyHours;

    public int getId() { return id; }
    public String getName() { return name; }
    public String getAbbreviation() { return abbreviation; }
    public String getProvider() { return provider; }
    public String getLevel() { return level; }
    public String getLevelDisplay() { return levelDisplay; }
    public String getTrack() { return track; }
    public String getTrackDisplay() { return trackDisplay; }
    public String getDescription() { return description; }
    public List<String> getRelevantSkills() { return relevantSkills; }
    public List<String> getCareerPaths() { return careerPaths; }
    public String getExamUrl() { return examUrl; }
    public String getStudyGuideUrl() { return studyGuideUrl; }
    public String getTesdaNcLevel() { return tesdaNcLevel; }
    public boolean isFree() { return isFree; }
    public String getOptionalPaidUpgrade() { return optionalPaidUpgrade; }
    public Integer getEstimatedCostPhp() { return estimatedCostPhp; }
    public Integer getEstimatedStudyHours() { return estimatedStudyHours; }
}
