package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class ChatSessionDetail {

    @SerializedName("sessionId")
    private String sessionId;

    @SerializedName("contextType")
    private String contextType;

    @SerializedName("title")
    private String title;

    @SerializedName("messages")
    private List<SessionMessage> messages;

    public String getSessionId()              { return sessionId; }
    public String getContextType()            { return contextType; }
    public String getTitle()                  { return title; }
    public List<SessionMessage> getMessages() { return messages; }
}
