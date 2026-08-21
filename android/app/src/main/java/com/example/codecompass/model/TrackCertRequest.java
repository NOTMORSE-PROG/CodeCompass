package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

public class TrackCertRequest {

    @SerializedName("certificationId")
    private int certificationId;

    @SerializedName("status")
    private String status;

    public TrackCertRequest(int certificationId, String status) {
        this.certificationId = certificationId;
        this.status = status;
    }
}
