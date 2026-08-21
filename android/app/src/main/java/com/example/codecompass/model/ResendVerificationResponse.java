package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

public class ResendVerificationResponse {

    @SerializedName("detail")
    private String detail;

    public String getDetail() { return detail; }
}
