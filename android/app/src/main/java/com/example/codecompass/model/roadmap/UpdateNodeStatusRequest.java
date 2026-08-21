package com.example.codecompass.model.roadmap;

import com.google.gson.annotations.SerializedName;

public class UpdateNodeStatusRequest {

    @SerializedName("status")
    private final String status;

    public UpdateNodeStatusRequest(String status) {
        this.status = status;
    }
}
