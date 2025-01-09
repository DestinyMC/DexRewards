package me.unariginal.dexrewards;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Config {
    private final ArrayList<RewardGroup> rewards = new ArrayList<>();
    private final Map<String, PlayerData> playerData = new HashMap<>();

    public Config() {
        DexRewards.LOGGER.info("[DexRewards] Loading configs!");

        try {
            checkFiles();
        } catch (IOException e) {
            e.printStackTrace();
        }
        loadConfig();
        loadPlayerData();
    }

    private void checkFiles() throws IOException {
        Path mainFolder = FabricLoader.getInstance().getConfigDir().resolve("DexRewards");
        File mainFile = mainFolder.toFile();
        if (!mainFile.exists()) {
            try {
                mainFile.mkdirs();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        File configFile = FabricLoader.getInstance().getConfigDir().resolve("DexRewards/dexrewards-config.json").toFile();
        if (!configFile.exists()) {
            configFile.createNewFile();

            InputStream in = DexRewards.class.getResourceAsStream("/dexrewards-config.json");
            OutputStream out = new FileOutputStream(configFile);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }

            in.close();
            out.close();
        }
    }

    private void loadConfig() {
        File configFile = FabricLoader.getInstance().getConfigDir().resolve("DexRewards/dexrewards-config.json").toFile();

        JsonElement root;
        try {
            root = JsonParser.parseReader(new FileReader(configFile));
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        JsonObject rootObj = root.getAsJsonObject();

        JsonObject reward_groups = rootObj.getAsJsonObject("reward_groups");
        this.rewards.clear();
        for (String group : reward_groups.keySet()) {
            DexRewards.LOGGER.info("Config: {}", group);
            JsonObject reward_group = reward_groups.getAsJsonObject(group);
            String icon = reward_group.get("icon").getAsString();
            double required_caught_percent = reward_group.get("required_caught_percent").getAsDouble();
            JsonObject rewards = reward_group.getAsJsonObject("rewards");
            ArrayList<Reward> rewardList = new ArrayList<>();
            for (String reward_key : rewards.keySet()) {
                JsonObject rewardObj = rewards.getAsJsonObject(reward_key);
                String type = rewardObj.get("type").getAsString();
                Reward reward;
                if (type.equalsIgnoreCase("item")) {
                    String item_namespace = rewardObj.get("item").getAsString();
                    int count = rewardObj.get("count").getAsInt();
                    reward = new ItemReward(type, item_namespace, count);
                    rewardList.add(reward);
                } else if (type.equalsIgnoreCase("command")) {
                    JsonArray commands = rewardObj.getAsJsonArray("commands");
                    ArrayList<String> commandList = new ArrayList<>();
                    for (JsonElement commandEle : commands.asList()) {
                        String command = commandEle.getAsString();
                        commandList.add(command);
                    }
                    reward = new CommandReward(type, commandList);
                    rewardList.add(reward);
                } else {
                    DexRewards.LOGGER.error("[DexRewards] Failed to load config rewards!");
                    return;
                }
            }
            this.rewards.add(new RewardGroup(group, icon, required_caught_percent, rewardList));
        }
    }

    private void loadPlayerData() {
        File playerDataFolder = FabricLoader.getInstance().getConfigDir().resolve("DexRewards/players").toFile();
        if (!playerDataFolder.exists()) {
            try {
                playerDataFolder.mkdirs();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        for (File data : Objects.requireNonNull(playerDataFolder.listFiles())) {
            if (data.getName().contains(".json")) {
                JsonObject root;
                try {
                    root = JsonParser.parseReader(new FileReader(data)).getAsJsonObject();
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }

                String uuid = root.get("uuid").getAsString();
                String username = root.get("username").getAsString();
                int caught_count = root.get("caught_count").getAsInt();
                JsonArray claimed = root.getAsJsonArray("claimed_rewards");
                ArrayList<String> claimedRewards = new ArrayList<>();
                for (JsonElement group : claimed.asList()) {
                    claimedRewards.add(group.getAsString());
                }
                JsonArray claimable = root.getAsJsonArray("claimable_rewards");
                ArrayList<String> claimableRewards = new ArrayList<>();
                for (JsonElement group : claimable.asList()) {
                    claimableRewards.add(group.getAsString());
                }
                this.playerData.remove(uuid);
                this.playerData.put(uuid, new PlayerData(uuid, username, caught_count, claimedRewards, claimableRewards));
            }
        }
    }

    public void updatePlayerData(PlayerData playerData) {
        try {
            File dataFile = FabricLoader.getInstance().getConfigDir().resolve("DexRewards/players/" + playerData.uuid() + ".json").toFile();
            if (!dataFile.exists()) {
                dataFile.createNewFile();
            }

            JsonObject root = new JsonObject();
            root.addProperty("uuid", playerData.uuid());
            root.addProperty("username", playerData.username());
            root.addProperty("caught_count", playerData.caught_count());
            JsonArray claimed = new JsonArray();
            for (String group : playerData.claimedRewards()) {
                claimed.add(group);
            }
            root.add("claimed_rewards", claimed);
            JsonArray claimable = new JsonArray();
            for (String group : playerData.claimableRewards()) {
                claimable.add(group);
            }
            root.add("claimable_rewards", claimable);

            Gson gson = new GsonBuilder()
                    .setPrettyPrinting()
                    .create();
            Writer writer = new FileWriter(dataFile);
            gson.toJson(root, writer);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        this.playerData.remove(playerData.uuid());
        this.playerData.put(playerData.uuid(), playerData);
    }

    public ArrayList<RewardGroup> getRewards() {
        return rewards;
    }

    public Map<String, PlayerData> getPlayerData() {
        return playerData;
    }
}
