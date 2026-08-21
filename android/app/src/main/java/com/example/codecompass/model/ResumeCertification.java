package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;
import java.util.UUID;

public class ResumeCertification {

    @SerializedName("id")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("issuer")
    private String issuer;

    @SerializedName("date")
    private String date;

    public ResumeCertification() {
        this.id = UUID.randomUUID().toString();
        this.name = "";
        this.issuer = "";
        this.date = "";
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
}
