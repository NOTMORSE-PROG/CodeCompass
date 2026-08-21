package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

public class CreateSessionResponse {

    @SerializedName("sessionId")
    private String sessionId;

    @SerializedName("title")
    private String title;

    public String getSessionId() { return sessionId; }
    public String getTitle() { return title; }
}
