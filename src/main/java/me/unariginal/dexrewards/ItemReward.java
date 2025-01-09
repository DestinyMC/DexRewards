package me.unariginal.dexrewards;

public class ItemReward extends Reward {
    String item_namespace;
    int count;

    public ItemReward(String type, String item_namespace, int count) {
        super(type);
        this.item_namespace = item_namespace;
        this.count = count;
    }
}
