package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

public class GoogleAuthRequest {

    @SerializedName("credential")
    private final String credential;

    public GoogleAuthRequest(String credential) {
        this.credential = credential;
    }
}
