package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

public class RegisterResponse {

    @SerializedName("access")
    private String access;

    @SerializedName("refresh")
    private String refresh;

    @SerializedName("user")
    private User user;

    public String getAccess() { return access; }
    public String getRefresh() { return refresh; }
    public User getUser() { return user; }
}
