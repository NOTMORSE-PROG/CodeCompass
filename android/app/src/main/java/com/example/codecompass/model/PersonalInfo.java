package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

public class PersonalInfo {

    @SerializedName("name")
    private String name;

    @SerializedName("title")
    private String title;

    @SerializedName("email")
    private String email;

    @SerializedName("phone")
    private String phone;

    @SerializedName("location")
    private String location;

    @SerializedName("linkedin")
    private String linkedin;

    @SerializedName("github")
    private String github;

    @SerializedName("website")
    private String website;

    public PersonalInfo() {
        this.name = "";
        this.title = "";
        this.email = "";
        this.phone = "";
        this.location = "";
        this.linkedin = "";
        this.github = "";
        this.website = "";
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getLinkedin() { return linkedin; }
    public void setLinkedin(String linkedin) { this.linkedin = linkedin; }

    public String getGithub() { return github; }
    public void setGithub(String github) { this.github = github; }

    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }
}
