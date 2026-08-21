package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

public class ChangePasswordRequest {

    @SerializedName("otp")
    private final String otp;

    @SerializedName("new_password")
    private final String newPassword;

    @SerializedName("new_password_confirm")
    private final String newPasswordConfirm;

    @SerializedName("refresh")
    private final String refresh;

    public ChangePasswordRequest(String otp, String newPassword,
                                  String newPasswordConfirm, String refresh) {
        this.otp                = otp;
        this.newPassword        = newPassword;
        this.newPasswordConfirm = newPasswordConfirm;
        this.refresh            = refresh;
    }
}
