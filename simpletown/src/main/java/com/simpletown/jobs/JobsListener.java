package com.simpletown.jobs;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.enchantments.Enchantment;

import java.util.Map;

public class JobsListener implements Listener {
    private static final double LUMBERJACK_REWARD = 2.5;
    private static final double SPRUCE_JUNGLE_REWARD = 0.5;
    private static final double OAK_ACACIA_CHERRY_DARK_OAK_REWARD = 2.0;
    private static final double MINER_STONE_REWARD = 0.2;
    private static final double MINER_COAL_REWARD = 2.0;
    private static final double MINER_COPPER_REWARD = 4.0;
    private static final double MINER_IRON_REWARD = 15.0;
    private static final double MINER_GOLD_REWARD = 30.0;
    private static final double MINER_LAPIS_REWARD = 10.0;
    private static final double MINER_REDSTONE_REWARD = 10.0;
    private static final double MINER_DIAMOND_REWARD = 40.0;
    private static final double MINER_EMERALD_REWARD = 80.0;
    private static final double FARMER_REWARD = 0.8;
    private static final double BUTCHER_REWARD = 12.0;

    private final JobsService jobsService;
    private final JobsManager jobsManager;
    private final JobRewardService rewardService;

    private final Map<Material, Double> lumberjackRewards = Map.ofEntries(
            // Spruce
            Map.entry(Material.SPRUCE_LOG, SPRUCE_JUNGLE_REWARD),
            Map.entry(Material.SPRUCE_WOOD, SPRUCE_JUNGLE_REWARD),
            Map.entry(Material.STRIPPED_SPRUCE_LOG, SPRUCE_JUNGLE_REWARD),
            Map.entry(Material.STRIPPED_SPRUCE_WOOD, SPRUCE_JUNGLE_REWARD),
            // Jungle
            Map.entry(Material.JUNGLE_LOG, SPRUCE_JUNGLE_REWARD),
            Map.entry(Material.JUNGLE_WOOD, SPRUCE_JUNGLE_REWARD),
            Map.entry(Material.STRIPPED_JUNGLE_LOG, SPRUCE_JUNGLE_REWARD),
            Map.entry(Material.STRIPPED_JUNGLE_WOOD, SPRUCE_JUNGLE_REWARD),
            // Oak family
            Map.entry(Material.OAK_LOG, OAK_ACACIA_CHERRY_DARK_OAK_REWARD),
            Map.entry(Material.OAK_WOOD, OAK_ACACIA_CHERRY_DARK_OAK_REWARD),
            Map.entry(Material.STRIPPED_OAK_LOG, OAK_ACACIA_CHERRY_DARK_OAK_REWARD),
            Map.entry(Material.STRIPPED_OAK_WOOD, OAK_ACACIA_CHERRY_DARK_OAK_REWARD),
            Map.entry(Material.ACACIA_LOG, OAK_ACACIA_CHERRY_DARK_OAK_REWARD),
            Map.entry(Material.ACACIA_WOOD, OAK_ACACIA_CHERRY_DARK_OAK_REWARD),
            Map.entry(Material.STRIPPED_ACACIA_LOG, OAK_ACACIA_CHERRY_DARK_OAK_REWARD),
            Map.entry(Material.STRIPPED_ACACIA_WOOD, OAK_ACACIA_CHERRY_DARK_OAK_REWARD),
            Map.entry(Material.CHERRY_LOG, OAK_ACACIA_CHERRY_DARK_OAK_REWARD),
            Map.entry(Material.CHERRY_WOOD, OAK_ACACIA_CHERRY_DARK_OAK_REWARD),
            Map.entry(Material.STRIPPED_CHERRY_LOG, OAK_ACACIA_CHERRY_DARK_OAK_REWARD),
            Map.entry(Material.STRIPPED_CHERRY_WOOD, OAK_ACACIA_CHERRY_DARK_OAK_REWARD),
            Map.entry(Material.DARK_OAK_LOG, OAK_ACACIA_CHERRY_DARK_OAK_REWARD),
            Map.entry(Material.DARK_OAK_WOOD, OAK_ACACIA_CHERRY_DARK_OAK_REWARD),
            Map.entry(Material.STRIPPED_DARK_OAK_LOG, OAK_ACACIA_CHERRY_DARK_OAK_REWARD),
            Map.entry(Material.STRIPPED_DARK_OAK_WOOD, OAK_ACACIA_CHERRY_DARK_OAK_REWARD)
    );

    private final Map<Material, Double> minerRewards = Map.ofEntries(
            Map.entry(Material.STONE, MINER_STONE_REWARD),
            Map.entry(Material.COBBLESTONE, MINER_STONE_REWARD),
            Map.entry(Material.DEEPSLATE, MINER_STONE_REWARD),
            Map.entry(Material.COBBLED_DEEPSLATE, MINER_STONE_REWARD),
            Map.entry(Material.COAL_ORE, MINER_COAL_REWARD),
            Map.entry(Material.DEEPSLATE_COAL_ORE, MINER_COAL_REWARD),
            Map.entry(Material.COPPER_ORE, MINER_COPPER_REWARD),
            Map.entry(Material.DEEPSLATE_COPPER_ORE, MINER_COPPER_REWARD),
            Map.entry(Material.IRON_ORE, MINER_IRON_REWARD),
            Map.entry(Material.DEEPSLATE_IRON_ORE, MINER_IRON_REWARD),
            Map.entry(Material.GOLD_ORE, MINER_GOLD_REWARD),
            Map.entry(Material.DEEPSLATE_GOLD_ORE, MINER_GOLD_REWARD),
            Map.entry(Material.LAPIS_ORE, MINER_LAPIS_REWARD),
            Map.entry(Material.DEEPSLATE_LAPIS_ORE, MINER_LAPIS_REWARD),
            Map.entry(Material.REDSTONE_ORE, MINER_REDSTONE_REWARD),
            Map.entry(Material.DEEPSLATE_REDSTONE_ORE, MINER_REDSTONE_REWARD),
            Map.entry(Material.DIAMOND_ORE, MINER_DIAMOND_REWARD),
            Map.entry(Material.DEEPSLATE_DIAMOND_ORE, MINER_DIAMOND_REWARD),
            Map.entry(Material.EMERALD_ORE, MINER_EMERALD_REWARD),
            Map.entry(Material.DEEPSLATE_EMERALD_ORE, MINER_EMERALD_REWARD)
    );

    public JobsListener(JobsService jobsService, JobsManager jobsManager, JobRewardService rewardService) {
        this.jobsService = jobsService;
        this.jobsManager = jobsManager;
        this.rewardService = rewardService;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) {
            return;
        }
        Player player = event.getPlayer();
        JobType job = jobsService.getJob(player);
        if (job == JobType.LUMBERJACK && Tag.LOGS.isTagged(event.getBlock().getType())) {
            event.setDropItems(false);
            double reward = lumberjackRewards.getOrDefault(event.getBlock().getType(), LUMBERJACK_REWARD);
            rewardService.reward(player, reward);
            return;
        }
        if (job == JobType.MINER) {
            if (player.getInventory().getItemInMainHand().containsEnchantment(Enchantment.SILK_TOUCH)) {
                return;
            }
            Double reward = minerRewards.get(event.getBlock().getType());
            if (reward != null) {
                rewardService.reward(player, reward);
            }
            return;
        }
        if (job == JobType.FARMER) {
            Block block = event.getBlock();
            if (block.getBlockData() instanceof Ageable ageable && ageable.getAge() == ageable.getMaximumAge()) {
                if (block.getType() == Material.WHEAT
                        || block.getType() == Material.POTATOES
                        || block.getType() == Material.CARROTS
                        || block.getType() == Material.BEETROOTS) {
                    rewardService.reward(player, FARMER_REWARD);
                }
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Animals)) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        if (jobsService.getJob(killer) == JobType.BUTCHER) {
            rewardService.reward(killer, BUTCHER_REWARD);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        if (jobsService.handleKillerKill(killer, event.getEntity())) {
            rewardService.reward(killer, JobsService.KILLER_REWARD);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (jobsService.getJob(player) == JobType.KILLER) {
            jobsService.ensureKillerTarget(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        jobsService.handleTargetOffline(event.getPlayer().getUniqueId());
    }
}