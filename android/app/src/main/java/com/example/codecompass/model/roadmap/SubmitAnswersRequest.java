package com.example.codecompass.model.roadmap;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

public class SubmitAnswersRequest {

    /** Backend expects {"0": "a", "1": "c", ...} — question index → letter answer */
    @SerializedName("answers")
    private final Map<String, String> answers;

    public SubmitAnswersRequest(Map<String, String> answers) {
        this.answers = answers;
    }
}
