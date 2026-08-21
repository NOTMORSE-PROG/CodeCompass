package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

public class CompleteOnboardingResponse {

    @SerializedName("detail")
    private String detail;

    @SerializedName("access")
    private String access;

    @SerializedName("refresh")
    private String refresh;

    public String getDetail() { return detail; }
    public String getAccess() { return access; }
    public String getRefresh() { return refresh; }
}
