package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

public class LeaderboardEntry {

    @SerializedName("rank")
    private int rank;

    @SerializedName("user")
    private LeaderboardUser user;

    @SerializedName("xpEarned")
    private int xpEarned;

    @SerializedName("period")
    private String period;

    public int getRank() { return rank; }
    public LeaderboardUser getUser() { return user; }
    public int getXpEarned() { return xpEarned; }
    public String getPeriod() { return period; }

    public static class LeaderboardUser {
        @SerializedName("fullName")
        private String fullName;

        public String getFullName() { return fullName; }
    }
}
