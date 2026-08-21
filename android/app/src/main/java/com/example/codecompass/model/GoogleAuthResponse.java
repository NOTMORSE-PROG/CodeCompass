package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

public class GoogleAuthResponse {

    @SerializedName("access")
    private String access;

    @SerializedName("refresh")
    private String refresh;

    @SerializedName("user")
    private User user;

    @SerializedName("isNewUser")
    private boolean isNewUser;

    public String getAccess() { return access; }
    public String getRefresh() { return refresh; }
    public User getUser() { return user; }
    public boolean isNewUser() { return isNewUser; }
}
