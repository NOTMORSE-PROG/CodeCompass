package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

public class SummaryOption {

    @SerializedName("tone")
    private String tone;

    @SerializedName("text")
    private String text;

    public String getTone() { return tone; }
    public String getText() { return text; }
}
