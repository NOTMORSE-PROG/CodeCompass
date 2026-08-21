package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class GenerateSummaryResponse {

    @SerializedName("summaries")
    private List<SummaryOption> summaries;

    public List<SummaryOption> getSummaries() { return summaries; }
}
