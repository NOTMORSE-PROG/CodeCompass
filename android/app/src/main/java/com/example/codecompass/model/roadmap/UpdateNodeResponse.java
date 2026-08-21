package com.example.codecompass.model.roadmap;

import com.example.codecompass.model.RoadmapNode;
import com.google.gson.annotations.SerializedName;

public class UpdateNodeResponse {

    @SerializedName("node")
    private RoadmapNode node;

    @SerializedName("completionPercentage")
    private double completionPercentage;

    @SerializedName("xpAwarded")
    private boolean xpAwarded;

    public RoadmapNode getNode() { return node; }

    /** Returns completion as an int 0-100 for progress bars. */
    public int getCompletionPercentageInt() { return (int) completionPercentage; }

    public boolean isXpAwarded() { return xpAwarded; }
}
