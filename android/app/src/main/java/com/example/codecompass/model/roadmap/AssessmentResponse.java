package com.example.codecompass.model.roadmap;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.List;

public class AssessmentResponse implements Serializable {

    @SerializedName("sessionId")
    private int sessionId;

    @SerializedName("questions")
    private List<AssessmentQuestion> questions;

    public int getSessionId() { return sessionId; }
    public List<AssessmentQuestion> getQuestions() { return questions; }
}
