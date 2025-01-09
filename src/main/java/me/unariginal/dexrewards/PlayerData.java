package me.unariginal.dexrewards;

import java.util.ArrayList;

public class PlayerData {
    private String uuid;
    private String username;
    private int caught_count;
    private ArrayList<String> claimedRewards;
    private ArrayList<String> claimableRewards;

    public PlayerData(String uuid, String username, int caught_count, ArrayList<String> claimedRewards, ArrayList<String> claimableRewards) {
        this.uuid = uuid;
        this.username = username;
        this.caught_count = caught_count;
        this.claimedRewards = claimedRewards;
        this.claimableRewards = claimableRewards;
    }

    public void setCaught_count(int caught_count) {
        this.caught_count = caught_count;
    }

    public String uuid() {
        return uuid;
    }

    public String username() {
        return username;
    }

    public int caught_count() {
        return caught_count;
    }

    public ArrayList<String> claimedRewards() {
        return claimedRewards;
    }

    public ArrayList<String> claimableRewards() {
        return claimableRewards;
    }
}
