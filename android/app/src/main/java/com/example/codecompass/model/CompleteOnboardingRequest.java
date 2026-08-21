package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

public class CompleteOnboardingRequest {

    @SerializedName("chat_session_id")
    private final String chatSessionId;

    public CompleteOnboardingRequest(String chatSessionId) {
        this.chatSessionId = chatSessionId;
    }
}
