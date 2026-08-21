package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

public class Badge {

    @SerializedName("id")
    private int id;

    @SerializedName("name")
    private String name;

    @SerializedName("slug")
    private String slug;

    @SerializedName("description")
    private String description;

    @SerializedName("category")
    private String category;

    @SerializedName("iconName")
    private String iconName;

    @SerializedName("iconColor")
    private String iconColor;

    @SerializedName("xpBonus")
    private int xpBonus;

    public int getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public String getIconName() { return iconName; }
    public String getIconColor() { return iconColor; }
    public int getXpBonus() { return xpBonus; }
}
