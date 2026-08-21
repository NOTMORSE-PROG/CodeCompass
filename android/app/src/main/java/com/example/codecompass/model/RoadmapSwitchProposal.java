package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

/**
 * AI-suggested career path switch, received in the `roadmap_switch` field of
 * a `stream_end` WebSocket message. Keys are snake_case — raw AI text parsing,
 * not through CamelCaseJSONRenderer.
 */
public class RoadmapSwitchProposal {

    @SerializedName("roadmap_id")
    private int roadmapId;

    @SerializedName("new_path")
    private String newPath;

    @SerializedName("career_goal")
    private String careerGoal;

    @SerializedName("summary")
    private String summary;

    public int    getRoadmapId()  { return roadmapId; }
    public String getNewPath()    { return newPath; }
    public String getCareerGoal() { return careerGoal; }
    public String getSummary()    { return summary; }
}
