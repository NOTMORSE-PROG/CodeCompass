package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

public class ChatSession {

    @SerializedName("sessionId")
    private String sessionId;

    @SerializedName("contextType")
    private String contextType;

    @SerializedName("title")
    private String title;

    @SerializedName("updatedAt")
    private String updatedAt;

    public String getSessionId()   { return sessionId; }
    public String getContextType() { return contextType; }
    public String getTitle()       { return title; }
    public String getUpdatedAt()   { return updatedAt; }
}
