package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

public class RegisterRequest {

    @SerializedName("email")
    private final String email;

    @SerializedName("username")
    private final String username;

    @SerializedName("first_name")
    private final String firstName;

    @SerializedName("last_name")
    private final String lastName;

    @SerializedName("password")
    private final String password;

    @SerializedName("password_confirm")
    private final String passwordConfirm;

    public RegisterRequest(String email, String username, String firstName,
                           String lastName, String password, String passwordConfirm) {
        this.email = email;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.password = password;
        this.passwordConfirm = passwordConfirm;
    }
}
