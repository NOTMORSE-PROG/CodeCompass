package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class GenerateBulletsResponse {

    @SerializedName("bullets")
    private List<String> bullets;

    public List<String> getBullets() { return bullets; }
}
