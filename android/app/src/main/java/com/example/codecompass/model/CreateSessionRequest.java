package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

public class CreateSessionRequest {

    @SerializedName("context_type")
    private final String contextType;

    public CreateSessionRequest(String contextType) {
        this.contextType = contextType;
    }
}
