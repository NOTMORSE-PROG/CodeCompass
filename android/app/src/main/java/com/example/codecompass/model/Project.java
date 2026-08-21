package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Project {

    @SerializedName("id")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("description")
    private String description;

    @SerializedName("tech")
    private List<String> tech;

    @SerializedName("link")
    private String link;

    public Project() {
        this.id = UUID.randomUUID().toString();
        this.name = "";
        this.description = "";
        this.tech = new ArrayList<>();
        this.link = "";
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getTech() { return tech; }
    public void setTech(List<String> tech) { this.tech = tech; }

    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }
}
