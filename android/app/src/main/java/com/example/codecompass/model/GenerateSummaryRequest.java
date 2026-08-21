package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class GenerateSummaryRequest {

    @SerializedName("targetRole")
    private String targetRole;

    @SerializedName("strengths")
    private List<String> strengths;

    @SerializedName("yearsExp")
    private String yearsExp;

    public GenerateSummaryRequest(String targetRole, List<String> strengths, String yearsExp) {
        this.targetRole = targetRole;
        this.strengths = strengths;
        this.yearsExp = yearsExp;
    }
}
