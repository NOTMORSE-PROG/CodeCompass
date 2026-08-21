package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

public class ResumeStyling {

    @SerializedName("primaryColor")
    private String primaryColor;

    public ResumeStyling() {
        this.primaryColor = "#1A2F5E";
    }

    public String getPrimaryColor() { return primaryColor; }
    public void setPrimaryColor(String primaryColor) { this.primaryColor = primaryColor; }
}
