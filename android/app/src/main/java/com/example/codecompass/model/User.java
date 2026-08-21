package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

public class User {

    @SerializedName("id")
    private int id;

    @SerializedName("email")
    private String email;

    @SerializedName("username")
    private String username;

    @SerializedName("firstName")
    private String firstName;

    @SerializedName("lastName")
    private String lastName;

    @SerializedName("role")
    private String role;

    @SerializedName("isOnboarded")
    private boolean isOnboarded;

    @SerializedName("hasPassword")
    private boolean hasPassword;

    @SerializedName("googleConnected")
    private boolean googleConnected;

    public int getId() { return id; }
    public String getEmail() { return email; }
    public String getUsername() { return username; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getRole() { return role; }
    public boolean isOnboarded() { return isOnboarded; }
    public boolean isHasPassword() { return hasPassword; }
    public boolean isGoogleConnected() { return googleConnected; }

    public String getFullName() {
        String first = firstName != null ? firstName : "";
        String last = lastName != null ? lastName : "";
        return (first + " " + last).trim();
    }
}
