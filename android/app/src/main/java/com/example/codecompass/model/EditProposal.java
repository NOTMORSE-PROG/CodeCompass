package com.example.codecompass.model;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

/**
 * A single AI-suggested roadmap edit, received in the `edit_proposals` array
 * of a `stream_end` WebSocket message.
 *
 * Keys are snake_case — these come from raw AI text parsing, not DRF's
 * CamelCaseJSONRenderer.
 */
public class EditProposal {

    @SerializedName("action")
    private String action;          // edit_node | edit_roadmap | replace_node

    @SerializedName("roadmap_id")
    private int roadmapId;

    @SerializedName("node_id")
    private int nodeId;             // 0 if not applicable

    @SerializedName("changes")
    private JsonObject changes;     // passed through as the API request body

    @SerializedName("summary")
    private String summary;

    public String    getAction()    { return action; }
    public int       getRoadmapId() { return roadmapId; }
    public int       getNodeId()    { return nodeId; }
    public JsonObject getChanges()  { return changes; }
    public String    getSummary()   { return summary; }

    public boolean isDangerous() {
        return "replace_node".equals(action);
    }
}
