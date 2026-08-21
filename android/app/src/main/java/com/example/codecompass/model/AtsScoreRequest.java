package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class AtsScoreRequest {

    @SerializedName("parsedJob")
    private ParsedJob parsedJob;

    public AtsScoreRequest(String jobTitle, List<String> requiredSkills,
                           List<String> niceToHaveSkills, List<String> keywords) {
        this.parsedJob = new ParsedJob(jobTitle, requiredSkills, niceToHaveSkills, keywords);
    }

    public static class ParsedJob {
        @SerializedName("jobTitle")
        private String jobTitle;

        @SerializedName("requiredSkills")
        private List<String> requiredSkills;

        @SerializedName("niceToHaveSkills")
        private List<String> niceToHaveSkills;

        @SerializedName("keywords")
        private List<String> keywords;

        public ParsedJob(String jobTitle, List<String> requiredSkills,
                         List<String> niceToHaveSkills, List<String> keywords) {
            this.jobTitle = jobTitle;
            this.requiredSkills = requiredSkills;
            this.niceToHaveSkills = niceToHaveSkills;
            this.keywords = keywords;
        }
    }
}
