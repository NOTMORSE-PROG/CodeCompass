package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

/**
 * Generic response for endpoints that return a single detail message,
 * e.g. send-change-password-otp, resend-verification, etc.
 */
public class MessageResponse {

    @SerializedName("detail")
    private String detail;

    public String getDetail() { return detail; }
}
