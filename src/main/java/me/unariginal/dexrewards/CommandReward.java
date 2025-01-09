package me.unariginal.dexrewards;

import java.util.ArrayList;

public class CommandReward extends Reward {
    ArrayList<String> commands;
    public CommandReward(String type, ArrayList<String> commands) {
        super(type);
        this.commands = commands;
    }
}
