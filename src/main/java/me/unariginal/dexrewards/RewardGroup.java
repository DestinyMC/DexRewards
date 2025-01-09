package me.unariginal.dexrewards;

import java.util.ArrayList;

public record RewardGroup(
    String group_name,
    String icon,
    double required_caught_percent,
    ArrayList<Reward> rewardList) {
}
