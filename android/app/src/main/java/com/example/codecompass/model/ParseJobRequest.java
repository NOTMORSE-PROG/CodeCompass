package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

public class ParseJobRequest {

    @SerializedName("jobDescription")
    private String jobDescription;

    public ParseJobRequest(String jobDescription) {
        this.jobDescription = jobDescription;
    }
}
