package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

public class ResumeSkills {

    @SerializedName("technical")
    private List<String> technical;

    @SerializedName("soft")
    private List<String> soft;

    @SerializedName("tools")
    private List<String> tools;

    public ResumeSkills() {
        this.technical = new ArrayList<>();
        this.soft = new ArrayList<>();
        this.tools = new ArrayList<>();
    }

    public List<String> getTechnical() { return technical; }
    public void setTechnical(List<String> technical) { this.technical = technical; }

    public List<String> getSoft() { return soft; }
    public void setSoft(List<String> soft) { this.soft = soft; }

    public List<String> getTools() { return tools; }
    public void setTools(List<String> tools) { this.tools = tools; }
}
