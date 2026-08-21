package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;

public class GamificationProfile {

    @SerializedName("xpTotal")
    private int xpTotal;

    @SerializedName("streakCount")
    private int streakCount;

    @SerializedName("badgesEarnedCount")
    private int badgesEarnedCount;

    public int getXpTotal() { return xpTotal; }
    public int getStreakCount() { return streakCount; }
    public int getBadgesEarnedCount() { return badgesEarnedCount; }
}
