package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class PagedResponse<T> {

    @SerializedName("count")
    private int count;

    @SerializedName("next")
    private String next;

    @SerializedName("previous")
    private String previous;

    @SerializedName("results")
    private List<T> results;

    public int getCount()       { return count; }
    public String getNext()     { return next; }
    public List<T> getResults() { return results; }
}
