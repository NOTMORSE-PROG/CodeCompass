package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

public class SessionMessage {

    @SerializedName("role")
    private String role;     // "user" | "assistant" | "system"

    @SerializedName("content")
    private String content;

    public String getRole()    { return role; }
    public String getContent() { return content; }
}
