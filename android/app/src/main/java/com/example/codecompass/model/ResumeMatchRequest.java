package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

public class ResumeMatchRequest {

    @SerializedName("resume_text")
    private final String resumeText;

    public ResumeMatchRequest(String resumeText) {
        this.resumeText = resumeText;
    }
}
