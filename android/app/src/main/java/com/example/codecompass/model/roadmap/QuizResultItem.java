package com.example.codecompass.model.roadmap;

import com.google.gson.annotations.SerializedName;

public class QuizResultItem {

    @SerializedName("correct")
    private boolean correct;

    /** Backend returns letter string: "a", "b", "c", or "d" */
    @SerializedName("yourAnswer")
    private String yourAnswer;

    @SerializedName("correctAnswer")
    private String correctAnswer;

    @SerializedName("explanation")
    private String explanation;

    public boolean isCorrect() { return correct; }
    public String getYourAnswer() { return yourAnswer; }
    public String getCorrectAnswer() { return correctAnswer; }
    public String getExplanation() { return explanation; }
}
