package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

public class UpdateCertStatusRequest {

    @SerializedName("status")
    private String status;

    public UpdateCertStatusRequest(String status) {
        this.status = status;
    }
}
