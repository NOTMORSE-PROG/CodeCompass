package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

public class UserBadge {

    @SerializedName("id")
    private int id;

    @SerializedName("badge")
    private Badge badge;

    @SerializedName("earnedAt")
    private String earnedAt;

    public int getId() { return id; }
    public Badge getBadge() { return badge; }
    public String getEarnedAt() { return earnedAt; }
}
