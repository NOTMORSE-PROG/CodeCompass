package com.example.codecompass.model.roadmap;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class QuizResult {

    @SerializedName("passed")
    private boolean passed;

    @SerializedName("score")
    private int score;

    @SerializedName("passThreshold")
    private int passThreshold;

    @SerializedName("correctCount")
    private int correctCount;

    @SerializedName("totalQuestions")
    private int totalQuestions;

    @SerializedName("results")
    private List<QuizResultItem> results;

    public boolean isPassed() { return passed; }
    public int getScore() { return score; }
    public int getPassThreshold() { return passThreshold; }
    public int getCorrectCount() { return correctCount; }
    public int getTotalQuestions() { return totalQuestions; }
    public List<QuizResultItem> getResults() { return results; }
}
