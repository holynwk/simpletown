package com.simpletown.jobs;

import com.simpletown.SimpleTownPlugin;
import com.simpletown.service.MessageService;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class JobRewardService {
    private static final DecimalFormat FORMAT = new DecimalFormat("#0.##");
    private static final long COMBO_WINDOW_MS = 1500L;
    private static final long COMBO_WINDOW_TICKS = COMBO_WINDOW_MS / 50L;

    private final SimpleTownPlugin plugin;
    private final MessageService messages;
    private final Map<UUID, RewardAccumulator> accumulators = new HashMap<>();

    public JobRewardService(SimpleTownPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public void reward(Player player, double amount) {
        if (amount <= 0) {
            return;
        }
        if (!plugin.ensureEconomy()) {
            return;
        }
        Economy economy = plugin.getEconomy();
        economy.depositPlayer(player, amount);
        RewardAccumulator accumulator = accumulators.computeIfAbsent(player.getUniqueId(), key -> new RewardAccumulator());
        long now = System.currentTimeMillis();
        accumulator.add(amount, now);
        sendActionBar(player, accumulator.getLastAmount(), accumulator.getCount());
        scheduleComboClear(player, accumulator);
    }

    private void sendActionBar(Player player, double amount, int count) {
        if (!player.isOnline()) {
            return;
        }
        String formatted = FORMAT.format(amount);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("amount", formatted);
        placeholders.put("count", String.valueOf(count));
        String message = messages.format("jobs.reward", placeholders);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
    }

    private void scheduleComboClear(Player player, RewardAccumulator accumulator) {
        long scheduledAt = accumulator.getLastRewardAt();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            RewardAccumulator current = accumulators.get(player.getUniqueId());
            if (current == null) {
                return;
            }
            long now = System.currentTimeMillis();
            if (current.getLastRewardAt() != scheduledAt) {
                return;
            }
            if (now - current.getLastRewardAt() >= COMBO_WINDOW_MS) {
                clearActionBar(player);
                accumulators.remove(player.getUniqueId());
            }
        }, Math.max(1L, COMBO_WINDOW_TICKS));
    }

    private void clearActionBar(Player player) {
        if (!player.isOnline()) {
            return;
        }
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
    }

    private static class RewardAccumulator {
        private double lastAmount;
        private int count;
        private long lastRewardAt;

        void add(double amount, long now) {
            if (now - lastRewardAt > COMBO_WINDOW_MS) {
                lastAmount = amount;
                count = 1;
            } else if (Double.compare(lastAmount, amount) == 0) {
                count++;
            } else {
                lastAmount = amount;
                count = 1;
            }
            lastRewardAt = now;
        }

        double getLastAmount() {
            return lastAmount;
        }

        int getCount() {
            return count;
        }

        long getLastRewardAt() {
            return lastRewardAt;
        }
    }
}
