package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;
import java.util.UUID;

public class Education {

    @SerializedName("id")
    private String id;

    @SerializedName("school")
    private String school;

    @SerializedName("degree")
    private String degree;

    @SerializedName("field")
    private String field;

    @SerializedName("startDate")
    private String startDate;

    @SerializedName("endDate")
    private String endDate;

    @SerializedName("gpa")
    private String gpa;

    public Education() {
        this.id = UUID.randomUUID().toString();
        this.school = "";
        this.degree = "";
        this.field = "";
        this.startDate = "";
        this.endDate = "";
        this.gpa = "";
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSchool() { return school; }
    public void setSchool(String school) { this.school = school; }

    public String getDegree() { return degree; }
    public void setDegree(String degree) { this.degree = degree; }

    public String getField() { return field; }
    public void setField(String field) { this.field = field; }

    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }

    public String getGpa() { return gpa; }
    public void setGpa(String gpa) { this.gpa = gpa; }
}
