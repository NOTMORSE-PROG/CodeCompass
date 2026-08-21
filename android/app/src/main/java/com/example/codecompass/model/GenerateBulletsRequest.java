package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

public class GenerateBulletsRequest {

    @SerializedName("jobTitle")
    private String jobTitle;

    @SerializedName("achievement")
    private String achievement;

    public GenerateBulletsRequest(String jobTitle, String achievement) {
        this.jobTitle = jobTitle;
        this.achievement = achievement;
    }
}
