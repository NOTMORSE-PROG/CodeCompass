package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

public class TokenRefreshRequest {

    @SerializedName("refresh")
    private final String refresh;

    public TokenRefreshRequest(String refresh) {
        this.refresh = refresh;
    }
}
