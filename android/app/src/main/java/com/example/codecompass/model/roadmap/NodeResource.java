package com.example.codecompass.model.roadmap;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class NodeResource implements Serializable {

    public static final String TYPE_YOUTUBE_VIDEO    = "youtube_video";
    public static final String TYPE_YOUTUBE_PLAYLIST = "youtube_playlist";
    public static final String TYPE_ARTICLE          = "article";
    public static final String TYPE_DOCUMENTATION    = "documentation";
    public static final String TYPE_GITHUB_REPO      = "github_repo";
    public static final String TYPE_COURSE           = "course";

    /** Sentinel URL set by backend when no YouTube video could be found. */
    public static final String URL_UNAVAILABLE = "yt:unavailable";

    @SerializedName("id")
    private int id;

    @SerializedName("resourceType")
    private String resourceType;

    @SerializedName("title")
    private String title;

    @SerializedName("url")
    private String url;

    @SerializedName("description")
    private String description;

    @SerializedName("thumbnailUrl")
    private String thumbnailUrl;

    @SerializedName("durationMinutes")
    private Integer durationMinutes;

    @SerializedName("isFree")
    private boolean isFree;

    @SerializedName("language")
    private String language;

    @SerializedName("youtubeVideoId")
    private String youtubeVideoId;

    @SerializedName("youtubeChannel")
    private String youtubeChannel;

    @SerializedName("order")
    private int order;

    @SerializedName("watch_unlocked")
    private boolean watchUnlocked;

    // ── Getters ───────────────────────────────────────────────────────────────

    public int getId() { return id; }
    public String getResourceType() { return resourceType; }
    public String getTitle() { return title; }
    public String getUrl() { return url; }
    public String getDescription() { return description; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public boolean isFree() { return isFree; }
    public String getLanguage() { return language; }
    public String getYoutubeVideoId() { return youtubeVideoId; }
    public String getYoutubeChannel() { return youtubeChannel; }
    public int getOrder() { return order; }
    public boolean isWatchUnlocked() { return watchUnlocked; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Resource URL not yet populated by backend (needs fetch-resources call). */
    public boolean isPlaceholder() {
        return url == null || url.isEmpty();
    }

    /** Backend searched but couldn't find a video for this resource. */
    public boolean isUnavailable() {
        return URL_UNAVAILABLE.equals(url);
    }

    public boolean isYouTube() {
        return TYPE_YOUTUBE_VIDEO.equals(resourceType) || TYPE_YOUTUBE_PLAYLIST.equals(resourceType);
    }

    /** Short badge label shown in the resource row (e.g. "YT", "A", "D"). */
    public String getTypeBadge() {
        if (isYouTube()) return "YT";
        switch (resourceType != null ? resourceType : "") {
            case TYPE_GITHUB_REPO:    return "GH";
            case TYPE_ARTICLE:        return "A";
            case TYPE_DOCUMENTATION:  return "D";
            case TYPE_COURSE:         return "C";
            default:                  return "R";
        }
    }

    /** Human-readable duration string, e.g. "25 min" or "1h 30m". */
    public String getDurationLabel() {
        if (durationMinutes == null || durationMinutes <= 0) return "";
        if (durationMinutes < 60) return durationMinutes + " min";
        int h = durationMinutes / 60;
        int m = durationMinutes % 60;
        return m == 0 ? h + "h" : h + "h " + m + "m";
    }
}
