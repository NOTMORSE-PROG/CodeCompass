package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

public class CreateResumeRequest {

    @SerializedName("title")
    private String title;

    @SerializedName("templateName")
    private String templateName;

    @SerializedName("content")
    private ResumeContent content;

    public CreateResumeRequest(String title, String templateName) {
        this.title = title;
        this.templateName = templateName;
        this.content = new ResumeContent();
    }

    public String getTitle() { return title; }
    public String getTemplateName() { return templateName; }
    public ResumeContent getContent() { return content; }
}
