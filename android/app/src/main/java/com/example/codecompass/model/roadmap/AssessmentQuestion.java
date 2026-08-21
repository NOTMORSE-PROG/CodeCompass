package com.example.codecompass.model.roadmap;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.Map;

public class AssessmentQuestion implements Serializable {

    @SerializedName("question")
    private String question;

    /** Backend returns {"a": "Option A", "b": "Option B", "c": "...", "d": "..."} */
    @SerializedName("options")
    private Map<String, String> options;

    public String getQuestion() { return question; }
    public Map<String, String> getOptions() { return options; }
}
