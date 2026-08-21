package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

/**
 * AI-suggested roadmap upskill (advance the current path), received in the
 * `roadmap_upskill` field of a `stream_end` WebSocket message. Keys are
 * snake_case — raw AI text parsing, not through CamelCaseJSONRenderer.
 */
public class RoadmapUpskillProposal {

    @SerializedName("roadmap_id")
    private int roadmapId;

    @SerializedName("summary")
    private String summary;

    public int    getRoadmapId() { return roadmapId; }
    public String getSummary()   { return summary; }
}
