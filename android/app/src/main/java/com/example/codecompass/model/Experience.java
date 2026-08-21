package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Experience {

    @SerializedName("id")
    private String id;

    @SerializedName("company")
    private String company;

    @SerializedName("title")
    private String title;

    @SerializedName("location")
    private String location;

    @SerializedName("startDate")
    private String startDate;

    @SerializedName("endDate")
    private String endDate;

    @SerializedName("current")
    private boolean current;

    @SerializedName("bullets")
    private List<String> bullets;

    public Experience() {
        this.id = UUID.randomUUID().toString();
        this.company = "";
        this.title = "";
        this.location = "";
        this.startDate = "";
        this.endDate = "";
        this.current = false;
        this.bullets = new ArrayList<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }

    public boolean isCurrent() { return current; }
    public void setCurrent(boolean current) { this.current = current; }

    public List<String> getBullets() { return bullets; }
    public void setBullets(List<String> bullets) { this.bullets = bullets; }
}
