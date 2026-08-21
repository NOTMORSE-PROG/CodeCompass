package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

public class Resume {

    @SerializedName("id")
    private int id;

    @SerializedName("title")
    private String title;

    @SerializedName("templateName")
    private String templateName;

    @SerializedName("content")
    private ResumeContent content;

    @SerializedName("createdAt")
    private String createdAt;

    @SerializedName("updatedAt")
    private String updatedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }

    public ResumeContent getContent() { return content; }
    public void setContent(ResumeContent content) { this.content = content; }

    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
}
