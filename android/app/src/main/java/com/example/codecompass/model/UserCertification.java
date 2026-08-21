package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

public class UserCertification {

    @SerializedName("id")
    private int id;

    @SerializedName("certification")
    private Certification certification;

    @SerializedName("status")
    private String status;

    @SerializedName("startedStudyingAt")
    private String startedStudyingAt;

    @SerializedName("earnedAt")
    private String earnedAt;

    @SerializedName("expiresAt")
    private String expiresAt;

    @SerializedName("certificateUrl")
    private String certificateUrl;

    @SerializedName("notes")
    private String notes;

    public int getId() { return id; }
    public Certification getCertification() { return certification; }
    public String getStatus() { return status; }
    public String getStartedStudyingAt() { return startedStudyingAt; }
    public String getEarnedAt() { return earnedAt; }
    public String getExpiresAt() { return expiresAt; }
    public String getCertificateUrl() { return certificateUrl; }
    public String getNotes() { return notes; }
}
