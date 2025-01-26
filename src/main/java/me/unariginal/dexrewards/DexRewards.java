package me.unariginal.dexrewards;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.api.pokedex.PokedexManager;
import com.cobblemon.mod.common.api.pokedex.SpeciesDexRecord;
import com.cobblemon.mod.common.api.pokedex.entry.DexEntries;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCBox;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import kotlin.Unit;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class DexRewards implements ModInitializer {
    private static final String MOD_ID = "dexrewards";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public Config config;
    public MinecraftServer server;

    @Override
    public void onInitialize() {
        LOGGER.info("[DexRewards] Loading..");
        CommandRegistrationCallback.EVENT.register((commandDispatcher, commandRegistryAccess, registrationEnvironment) -> {
            final CommandNode<CommandSourceStack> mainNode = commandDispatcher.register(
                    Commands.literal("dexrewards")
                            .then(
                                    Commands.literal("reload")
                                            .requires(Permissions.require("dexrewards.reload", 4))
                                            .executes(ctx -> {
                                                config = new Config();

                                                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                                                    if (!config.getPlayerData().get(player.getStringUUID()).claimableRewards().isEmpty()) {
                                                        player.sendMessage(MiniMessage.miniMessage().deserialize("<dark_gray>[<blue>DexRewards<dark_gray>] <gray>You have rewards to claim!"));
                                                    }
                                                }

                                                ctx.getSource().sendMessage(Component.literal("[DexRewards] Reloaded Configs!"));

                                                return 1;
                                            })
                            )
                            .then(
                                    Commands.literal("rewards")
                                            .executes(this::openRewardsMenu)
                            )
            );
            commandDispatcher.register(Commands.literal("drewards").redirect(mainNode));
            commandDispatcher.register(Commands.literal("dexr").redirect(mainNode));
            commandDispatcher.register(Commands.literal("dex").redirect(mainNode));
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            this.server = server;
            this.config = new Config();

            Placeholders.register(ResourceLocation.tryBuild("pokedex", "total_implemented"), (ctx, arg) -> PlaceholderResult.value(String.valueOf(DexEntries.INSTANCE.getEntries().size())));

            Placeholders.register(ResourceLocation.tryBuild("pokedex", "total_caught"), (ctx, arg) -> {
                if (!ctx.hasPlayer()) {
                    return PlaceholderResult.invalid("No Player!");
                }

                ServerPlayer player = ctx.player();
                assert player != null;
                return PlaceholderResult.value(String.valueOf(getCaught(player)));
            });

            Placeholders.register(ResourceLocation.tryBuild("pokedex", "total_seen"), (ctx, arg) -> {
                if (!ctx.hasPlayer()) {
                    return PlaceholderResult.invalid("No Player!");
                }

                ServerPlayer player = ctx.player();
                assert player != null;
                return PlaceholderResult.value(String.valueOf(getEncountered(player) + getCaught(player)));
            });

            Placeholders.register(ResourceLocation.tryBuild("pokedex", "last_claimed_reward"), (ctx, arg) -> {
                if (!ctx.hasPlayer()) {
                    return PlaceholderResult.invalid("No Player!");
                }

                ServerPlayer player = ctx.player();

                String currentRank = "None";
                assert player != null;
                for (RewardGroup group : config.getRewards()) {
                    if (config.getPlayerData().get(player.getStringUUID()).claimedRewards().contains(group.group_name())) {
                        currentRank = group.group_name();
                    }
                }

                return PlaceholderResult.value(currentRank);
            });

            Placeholders.register(ResourceLocation.tryBuild("pokedex", "total_reward_groups"), (ctx, arg) -> PlaceholderResult.value(String.valueOf(config.getRewards().size())));

            Placeholders.register(ResourceLocation.tryBuild("pokedex", "percent_completed"), (ctx, arg) -> {
                if (!ctx.hasPlayer()) {
                    return PlaceholderResult.invalid("No Player!");
                }

                ServerPlayer player = ctx.player();

                assert player != null;
                int dex_total = DexEntries.INSTANCE.getEntries().size();
                int caught_total = getCaught(player);
                double percent_completed = (double) caught_total / dex_total;
                return PlaceholderResult.value(String.valueOf(new DecimalFormat("#.##").format(percent_completed)));
            });

            ServerPlayConnectionEvents.JOIN.register((serverPlayNetworkHandler, packetSender, minecraftServer) -> {
                ServerPlayer player = serverPlayNetworkHandler.getPlayer();
                PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(player);
                for (PCBox box : pc.getBoxes()) {
                    for (Pokemon pokemon : box.getNonEmptySlots().values()) {
                        // Doing cursed shit because of poor method naming on Cobblemon's behalf
                        try {
                            PokedexManager manager = Cobblemon.playerDataManager.getPokedexData(player);
                            manager.getClass().getDeclaredMethod("catch", Pokemon.class).invoke(manager, pokemon);
                        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
                PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
                for (Pokemon pokemon : party) {
                    // Doing cursed shit because of poor method naming on Cobblemon's behalf
                    try {
                        PokedexManager manager = Cobblemon.playerDataManager.getPokedexData(player);
                        manager.getClass().getDeclaredMethod("catch", Pokemon.class).invoke(manager, pokemon);
                    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                }

                if (!config.getPlayerData().containsKey(player.getStringUUID())) {
                    int caughtCount = getCaught(player);

                    ArrayList<String> claimableRewards = new ArrayList<>();

                    for (RewardGroup group : config.getRewards()) {
                        double required_percent = group.required_caught_percent();
                        int totalDex = DexEntries.INSTANCE.getEntries().size();
                        double percent_caught = (double) caughtCount / totalDex;
                        if (percent_caught * 100 > required_percent) {
                            claimableRewards.add(group.group_name());
                            player.sendMessage(MiniMessage.miniMessage().deserialize("<dark_gray>[<blue>DexRewards<dark_gray>] <gray>You can claim the " + group.group_name() + " rewards!"));
                        }
                    }

                    config.updatePlayerData(new PlayerData(player.getStringUUID(), player.getName().getString(), caughtCount, new ArrayList<>(), claimableRewards));
                } else {
                    int caughtCount = getCaught(player);

                    for (RewardGroup group : config.getRewards()) {
                        double required_percent = group.required_caught_percent();
                        int totalDex = DexEntries.INSTANCE.getEntries().size();
                        double percent_caught = (double) caughtCount / totalDex;
                        if (percent_caught * 100 > required_percent) {
                            if (!config.getPlayerData().get(player.getStringUUID()).claimableRewards().contains(group.group_name())) {
                                if (!config.getPlayerData().get(player.getStringUUID()).claimedRewards().contains(group.group_name())) {
                                    config.getPlayerData().get(player.getStringUUID()).claimableRewards().add(group.group_name());
                                    player.sendMessage(MiniMessage.miniMessage().deserialize("<dark_gray>[<blue>DexRewards<dark_gray>] <gray>You can claim the " + group.group_name() + " rewards!"));
                                }
                            }
                        }
                    }

                    config.getPlayerData().get(player.getStringUUID()).setCaught_count(caughtCount);
                    config.updatePlayerData(config.getPlayerData().get(player.getStringUUID()));

                    if (!config.getPlayerData().get(player.getStringUUID()).claimableRewards().isEmpty()) {
                        player.sendMessage(MiniMessage.miniMessage().deserialize("<dark_gray>[<blue>DexRewards<dark_gray>] <gray>You have rewards to claim!"));
                    }
                }
            });

            ServerPlayConnectionEvents.DISCONNECT.register((serverPlayNetworkHandler, minecraftServer) -> {
                config.updatePlayerData(config.getPlayerData().get(serverPlayNetworkHandler.getPlayer().getStringUUID()));
            });

            CobblemonEvents.POKEDEX_DATA_CHANGED_POST.subscribe(Priority.NORMAL, event -> {
                ServerPlayer player = server.getPlayerList().getPlayer(event.getPlayerUUID());
                if (player != null) {
                    int caughtCount = getCaught(player);

                    for (RewardGroup group : config.getRewards()) {
                        double required_percent = group.required_caught_percent();
                        int totalDex = DexEntries.INSTANCE.getEntries().size();
                        double percent_caught = (double) caughtCount / totalDex;
                        if (percent_caught * 100 > required_percent) {
                            if (!config.getPlayerData().get(player.getStringUUID()).claimableRewards().contains(group.group_name())) {
                                if (!config.getPlayerData().get(player.getStringUUID()).claimedRewards().contains(group.group_name())) {
                                    config.getPlayerData().get(player.getStringUUID()).claimableRewards().add(group.group_name());
                                    player.sendMessage(MiniMessage.miniMessage().deserialize("<dark_gray>[<blue>DexRewards<dark_gray>] <gray>You can claim the " + group.group_name() + " rewards!"));
                                }
                            }
                        }
                    }

                    config.getPlayerData().get(player.getStringUUID()).setCaught_count(caughtCount);
                    config.updatePlayerData(config.getPlayerData().get(player.getStringUUID()));
                }
                return Unit.INSTANCE;
            });
        });
    }

    private int getCaught(ServerPlayer player) {
        int caughtCount = 0;
        for (SpeciesDexRecord record : Cobblemon.playerDataManager.getPokedexData(player).getSpeciesRecords().values()) {
            if (record.getKnowledge().equals(PokedexEntryProgress.CAUGHT)) {
                caughtCount++;
            }
        }

        return caughtCount;
    }

    private int getEncountered(ServerPlayer player) {
        int seenCount = 0;
        for (SpeciesDexRecord record : Cobblemon.playerDataManager.getPokedexData(player).getSpeciesRecords().values()) {
            if (record.getKnowledge().equals(PokedexEntryProgress.ENCOUNTERED)) {
                seenCount++;
            }
        }

        return seenCount;
    }

    public int openRewardsMenu(CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().isPlayer()) {
            ServerPlayer player = ctx.getSource().getPlayer();
            if (player != null) {
                try {
                    ItemStack bgItem = new ItemStack(Items.GRAY_STAINED_GLASS_PANE, 1);
                    bgItem.applyComponents(DataComponentMap.builder().set(DataComponents.ITEM_NAME, Component.empty()).build());

                    GooeyButton background = GooeyButton.builder()
                            .display(bgItem)
                            .build();

                    // PLAYER INFO SKULL --------------------------------------
                    ItemStack playerSkull = new ItemStack(Items.PLAYER_HEAD, 1);
                    playerSkull.applyComponents(DataComponentMap.builder().set(DataComponents.PROFILE, new ResolvableProfile(player.getGameProfile())).build());
                    playerSkull.applyComponents(DataComponentMap.builder().set(DataComponents.ITEM_NAME, Component.literal("Pokedex Info").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)).build());

                    if (config.getPlayerData().containsKey(player.getStringUUID())) {
                        String currentRank = "None";
                        for (RewardGroup testGroup : config.getRewards()) {
                            if (config.getPlayerData().get(player.getStringUUID()).claimedRewards().contains(testGroup.group_name())) {
                                currentRank = testGroup.group_name();
                            }
                        }

                        playerSkull.applyComponents(DataComponentMap.builder().set(DataComponents.LORE, new ItemLore(List.of(
                                Component.literal("Current Rank: " + currentRank).withStyle(ChatFormatting.GRAY),
                                Component.literal("Progress: " + config.getPlayerData().get(player.getStringUUID()).caught_count() +
                                                "/" + DexEntries.INSTANCE.getEntries().size() +
                                                " (" + new DecimalFormat("#.##").format((double) config.getPlayerData().get(player.getStringUUID()).caught_count() / DexEntries.INSTANCE.getEntries().size() * 100) + "%)")
                                        .withStyle(ChatFormatting.GRAY)
                        ))).build());

                        GooeyButton playerInfo = GooeyButton.builder()
                                .display(playerSkull)
                                .build();

                        // -----------------------------------------------------------------------------
                        // REWARDS SECTION -------------------------
                        ChestTemplate.Builder rewardGuiBuilder = ChestTemplate.builder(5)
                                .set(1, 4, playerInfo);
                        for (int index = 0; index < config.getRewards().size(); index++) {
                            RewardGroup group = config.getRewards().get(index);

                            ItemStack groupItem = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(group.icon())), 1);
                            Runnable onClickAction = null;

                            // Claimable Reward
                            if (config.getPlayerData().get(player.getStringUUID()).claimableRewards().contains(group.group_name())) {
                                groupItem.applyComponents(DataComponentMap.builder().set(DataComponents.ITEM_NAME, Component.literal(group.group_name() + " (" + group.required_caught_percent() + "%)").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)).build());
                                groupItem.applyComponents(DataComponentMap.builder().set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true).build());
                                groupItem.applyComponents(DataComponentMap.builder().set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Claimable!").withStyle(ChatFormatting.GREEN)))).build());

                                // Click Action
                                onClickAction = () -> {
                                    config.getPlayerData().get(player.getStringUUID()).claimableRewards().remove(group.group_name());
                                    if (!config.getPlayerData().get(player.getStringUUID()).claimedRewards().contains(group.group_name())) {
                                        config.getPlayerData().get(player.getStringUUID()).claimedRewards().add(group.group_name());
                                    }

                                    for (Reward reward : group.rewardList()) {
                                        if (reward instanceof ItemReward) {
                                            String namespace = ((ItemReward) reward).item_namespace;
                                            int count = ((ItemReward) reward).count;
                                            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(namespace));
                                            ItemStack itemStack = new ItemStack(item, count);
                                            player.addItem(itemStack);
                                        } else if (reward instanceof CommandReward) {
                                            ArrayList<String> commands = ((CommandReward) reward).commands;

                                            for (String command : commands) {
                                                command = command.replaceAll("%player%", player.getName().getString());
                                                server.getCommands().performCommand(server.getCommands().getDispatcher().parse(command, server.createCommandSourceStack()), command);
                                            }
                                        }
                                    }
                                    player.sendMessage(MiniMessage.miniMessage().deserialize("<dark_gray>[<blue>DexRewards<dark_gray>] <gray>You've claimed your rewards for " + group.group_name() + "!"));
                                    UIManager.closeUI(player);
                                };
                            // Already Claimed Reward
                            } else if (config.getPlayerData().get(player.getStringUUID()).claimedRewards().contains(group.group_name())) {
                                groupItem.applyComponents(DataComponentMap.builder().set(DataComponents.ITEM_NAME, Component.literal(group.group_name() + " (" + group.required_caught_percent() + "%)").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)).build());
                                groupItem.applyComponents(DataComponentMap.builder().set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Already Claimed!").withStyle(ChatFormatting.BLUE)))).build());
                            // Reward Not Reached Yet
                            } else {
                                groupItem.applyComponents(DataComponentMap.builder().set(DataComponents.ITEM_NAME, Component.literal(group.group_name() + " (" + group.required_caught_percent() + "%)").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)).build());
                                groupItem.applyComponents(DataComponentMap.builder().set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Not Unlocked!").withStyle(ChatFormatting.RED)))).build());
                            }

                            GooeyButton.Builder groupButtonBuilder = GooeyButton.builder()
                                    .display(groupItem);
                            if (onClickAction != null) {
                                groupButtonBuilder = groupButtonBuilder.onClick(onClickAction);
                            }
                            GooeyButton groupButton = groupButtonBuilder.build();


                            if (index < 7) {
                                rewardGuiBuilder = rewardGuiBuilder.set(2, 1 + index, groupButton);
                            } else if (index < 14) {
                                rewardGuiBuilder = rewardGuiBuilder.set(3, 1 + (index - 7), groupButton);
                            } else if (index < 21) {
                                rewardGuiBuilder = rewardGuiBuilder.set(4, 1 + (index - 14), groupButton);
                            }
                        }
                        ChestTemplate rewardGui = rewardGuiBuilder.fill(background).build();
                        GooeyPage rewardPage = GooeyPage.builder()
                                .onClose((action) -> config.updatePlayerData(config.getPlayerData().get(player.getStringUUID())))
                                .title(Component.literal("Pokedex Rewards").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                                .template(rewardGui)
                                .build();
                        UIManager.openUIForcefully(player, rewardPage);
                    } else {
                        player.sendMessage(Component.literal("[DexRewards] There was an error getting your data, contact an admin!").withStyle(ChatFormatting.RED));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return 1;
    }
}
