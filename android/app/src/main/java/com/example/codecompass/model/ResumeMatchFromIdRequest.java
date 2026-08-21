package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

public class ResumeMatchFromIdRequest {

    @SerializedName("resume_id")
    private final int resumeId;

    public ResumeMatchFromIdRequest(int resumeId) {
        this.resumeId = resumeId;
    }

    public int getResumeId() {
        return resumeId;
    }
}
