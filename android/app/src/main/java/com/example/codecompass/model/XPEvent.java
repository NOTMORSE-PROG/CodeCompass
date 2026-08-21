package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

public class XPEvent {

    @SerializedName("id")
    private int id;

    @SerializedName("eventType")
    private String eventType;

    @SerializedName("xpEarned")
    private int xpEarned;

    @SerializedName("description")
    private String description;

    @SerializedName("createdAt")
    private String createdAt;

    public int getId() { return id; }
    public String getEventType() { return eventType; }
    public int getXpEarned() { return xpEarned; }
    public String getDescription() { return description; }
    public String getCreatedAt() { return createdAt; }
}
