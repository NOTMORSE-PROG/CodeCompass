package com.example.codecompass.model;

import com.example.codecompass.model.roadmap.NodeResource;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.List;

public class RoadmapNode implements Serializable {

    // ── Status constants ──────────────────────────────────────────────────────
    public static final String STATUS_LOCKED      = "locked";
    public static final String STATUS_AVAILABLE   = "available";
    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_COMPLETED   = "completed";

    // ── Node type constants ───────────────────────────────────────────────────
    public static final String TYPE_MILESTONE        = "milestone";
    public static final String TYPE_SKILL            = "skill";
    public static final String TYPE_PROJECT          = "project";
    public static final String TYPE_ASSESSMENT       = "assessment";
    public static final String TYPE_FINAL_ASSESSMENT = "final_assessment";
    public static final String TYPE_CERTIFICATION    = "certification";

    // ── Fields ────────────────────────────────────────────────────────────────

    @SerializedName("id")
    private int id;

    @SerializedName("parentNode")
    private Integer parentNode;

    @SerializedName("nodeType")
    private String nodeType;

    @SerializedName("title")
    private String title;

    @SerializedName("description")
    private String description;

    @SerializedName("skillSlug")
    private String skillSlug;

    @SerializedName("positionX")
    private float positionX;

    @SerializedName("positionY")
    private float positionY;

    @SerializedName("nodeOrder")
    private int nodeOrder;

    @SerializedName("estimatedHours")
    private int estimatedHours;

    @SerializedName("difficulty")
    private int difficulty;

    @SerializedName("status")
    private String status;

    @SerializedName("xpReward")
    private int xpReward;

    @SerializedName("completedAt")
    private String completedAt;

    @SerializedName("resources")
    private List<NodeResource> resources;

    // ── Getters ───────────────────────────────────────────────────────────────

    public int getId() { return id; }
    public Integer getParentNode() { return parentNode; }
    public String getNodeType() { return nodeType; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getSkillSlug() { return skillSlug; }
    public float getPositionX() { return positionX; }
    public float getPositionY() { return positionY; }
    public int getNodeOrder() { return nodeOrder; }
    public int getEstimatedHours() { return estimatedHours; }
    public int getDifficulty() { return difficulty; }
    public String getStatus() { return status; }
    public int getXpReward() { return xpReward; }
    public String getCompletedAt() { return completedAt; }
    public List<NodeResource> getResources() { return resources; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public boolean isAvailableOrInProgress() {
        return STATUS_AVAILABLE.equals(status) || STATUS_IN_PROGRESS.equals(status);
    }

    public boolean isLocked() { return STATUS_LOCKED.equals(status); }
    public boolean isAvailable() { return STATUS_AVAILABLE.equals(status); }
    public boolean isInProgress() { return STATUS_IN_PROGRESS.equals(status); }
    public boolean isCompleted() { return STATUS_COMPLETED.equals(status); }
    public boolean isMilestone() { return TYPE_MILESTONE.equals(nodeType); }

    /** True if this node has any YouTube resources (relevant for quiz gate checks). */
    public boolean hasYouTubeResources() {
        if (resources == null) return false;
        for (NodeResource r : resources) {
            if (NodeResource.TYPE_YOUTUBE_VIDEO.equals(r.getResourceType())
                    || NodeResource.TYPE_YOUTUBE_PLAYLIST.equals(r.getResourceType())) {
                if (!r.isPlaceholder()) return true;
            }
        }
        return false;
    }

    /** True if resources have been fetched (no placeholder URLs). */
    public boolean areResourcesFetched() {
        if (resources == null || resources.isEmpty()) return true;
        for (NodeResource r : resources) {
            if (r.isPlaceholder()) return false;
        }
        return true;
    }

    public String getDifficultyLabel() {
        switch (difficulty) {
            case 1: return "Beginner";
            case 2: return "Easy";
            case 3: return "Intermediate";
            case 4: return "Hard";
            case 5: return "Expert";
            default: return "Beginner";
        }
    }
}
