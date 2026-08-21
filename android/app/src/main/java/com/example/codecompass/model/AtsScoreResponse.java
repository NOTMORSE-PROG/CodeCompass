package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class AtsScoreResponse {

    @SerializedName("score")
    private int score;

    @SerializedName("scoreLabel")
    private String scoreLabel;

    @SerializedName("breakdown")
    private Breakdown breakdown;

    @SerializedName("matchedKeywords")
    private List<String> matchedKeywords;

    @SerializedName("missingRequired")
    private List<String> missingRequired;

    @SerializedName("missingPreferred")
    private List<String> missingPreferred;

    @SerializedName("missingGeneral")
    private List<String> missingGeneral;

    @SerializedName("suggestions")
    private List<AtsSuggestion> suggestions;

    public int getScore() { return score; }
    public String getScoreLabel() { return scoreLabel; }
    public Breakdown getBreakdown() { return breakdown; }
    public List<String> getMatchedKeywords() { return matchedKeywords; }
    public List<String> getMissingRequired() { return missingRequired; }
    public List<String> getMissingPreferred() { return missingPreferred; }
    public List<String> getMissingGeneral() { return missingGeneral; }
    public List<AtsSuggestion> getSuggestions() { return suggestions; }

    public static class Breakdown {
        @SerializedName("keywordScore")
        private int keywordScore;

        @SerializedName("titleScore")
        private int titleScore;

        @SerializedName("structureScore")
        private int structureScore;

        public int getKeywordScore() { return keywordScore; }
        public int getTitleScore() { return titleScore; }
        public int getStructureScore() { return structureScore; }
    }

    public static class AtsSuggestion {
        @SerializedName("priority")
        private String priority;

        @SerializedName("section")
        private String section;

        @SerializedName("keyword")
        private String keyword;

        @SerializedName("text")
        private String text;

        @SerializedName("example")
        private String example;

        public String getPriority() { return priority; }
        public String getSection() { return section; }
        public String getKeyword() { return keyword; }
        public String getText() { return text; }
        public String getExample() { return example; }
    }
}
