package com.simpletown.service;

import com.simpletown.data.ChunkPosition;
import com.simpletown.data.Town;
import com.simpletown.data.TownManager;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.inventory.ItemStack;
import net.md_5.bungee.api.ChatColor;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class RichChunkService {
    public enum ResourceType {
        COAL(Material.COAL, "Угольный чанк", ChatColor.GRAY),
        IRON(Material.IRON_INGOT, "Железный чанк", ChatColor.WHITE),
        GOLD(Material.GOLD_INGOT, "Золотой чанк", ChatColor.GOLD),
        DIAMOND(Material.DIAMOND, "Алмазный чанк", ChatColor.AQUA),
        NETHERITE_SCRAP(Material.NETHERITE_SCRAP, "Незеритовый чанк", ChatColor.DARK_GRAY);

        private final Material icon;
        private final String displayName;
        private final ChatColor color;

        ResourceType(Material icon, String displayName, ChatColor color) {
            this.icon = icon;
            this.displayName = displayName;
            this.color = color;
        }

        public Material getIcon() {
            return icon;
        }

        public String getDisplayName() {
            return displayName;
        }

        public ChatColor getColor() {
            return color;
        }

        public String getColoredName() {
            return color + "" + ChatColor.BOLD + displayName + ChatColor.RESET;
        }

        public String getRewardLabel() {
            return switch (this) {
                case COAL -> "Угля";
                case IRON -> "Железа";
                case GOLD -> "Золота";
                case DIAMOND -> "Алмазов";
                case NETHERITE_SCRAP -> "Незеритового лома";
            };
        }

        public static ResourceType random(Random random) {
            ResourceType[] values = values();
            return values[random.nextInt(values.length)];
        }
    }

    public static class RichChunkEntry {
        private final ChunkPosition position;
        private final ResourceType type;
        private final LocalDate discovered;
        private LocalDateTime lastReward;

        public RichChunkEntry(ChunkPosition position, ResourceType type, LocalDate discovered, LocalDateTime lastReward) {
            this.position = position;
            this.type = type;
            this.discovered = discovered;
            this.lastReward = lastReward;
        }

        public ChunkPosition getPosition() {
            return position;
        }

        public ResourceType getType() {
            return type;
        }

        public LocalDate getDiscovered() {
            return discovered;
        }

        public LocalDateTime getLastReward() {
            return lastReward;
        }

        public void setLastReward(LocalDateTime lastReward) {
            this.lastReward = lastReward;
        }
    }

    private static final int RICH_CHANCE_PERCENT = 1;
    private static final int REWARD_AMOUNT = 32;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_DATE;
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ISO_DATE_TIME;

    private final Plugin plugin;
    private final File file;
    private final Random random = new Random();
    private final Map<ChunkPosition, RichChunkEntry> richChunks = new HashMap<>();

    public RichChunkService(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "rich_chunks.yml");
        load();
    }

    public Optional<RichChunkEntry> recordRichChunkIfEligible(Chunk chunk) {
        ChunkPosition position = ChunkPosition.fromChunk(chunk);
        if (richChunks.containsKey(position)) {
            return Optional.empty();
        }
        if (random.nextInt(100) < RICH_CHANCE_PERCENT) {
            RichChunkEntry entry = new RichChunkEntry(position, ResourceType.random(random), LocalDate.now(), null);
            richChunks.put(position, entry);
            save();
            return Optional.of(entry);
        }
        return Optional.empty();
    }

    public List<RichChunkEntry> getRichChunksForTown(Town town) {
        List<RichChunkEntry> entries = new ArrayList<>();
        for (ChunkPosition position : town.getChunks()) {
            RichChunkEntry entry = richChunks.get(position);
            if (entry != null) {
                entries.add(entry);
            }
        }
        entries.sort(Comparator.comparing((RichChunkEntry entry) -> entry.getPosition().getWorld())
                .thenComparing(entry -> entry.getPosition().getX())
                .thenComparing(entry -> entry.getPosition().getZ()));
        return entries;
    }

    public ResourceType getResource(ChunkPosition position) {
        RichChunkEntry entry = richChunks.get(position);
        return entry == null ? null : entry.getType();
    }

    public int getRewardAmount() {
        return REWARD_AMOUNT;
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<ChunkPosition, RichChunkEntry> entry : richChunks.entrySet()) {
            String key = entry.getKey().serialize();
            config.set(key + ".type", entry.getValue().getType().name());
            config.set(key + ".discovered", DATE_FORMAT.format(entry.getValue().getDiscovered()));
            if (entry.getValue().getLastReward() != null) {
                config.set(key + ".lastReward", DATETIME_FORMAT.format(entry.getValue().getLastReward()));
            }
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить rich_chunks.yml: " + e.getMessage());
        }
    }

    private void load() {
        richChunks.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String key : config.getKeys(false)) {
            try {
                ChunkPosition position = ChunkPosition.deserialize(key);
                String typeName = config.getString(key + ".type", config.getString(key));
                String discoveredRaw = config.getString(key + ".discovered", DATE_FORMAT.format(LocalDate.now()));
                String lastRewardRaw = config.getString(key + ".lastReward", null);
                ResourceType type = ResourceType.valueOf(typeName);
                LocalDate discovered = LocalDate.parse(discoveredRaw, DATE_FORMAT);
                LocalDateTime lastReward = parseDateTime(lastRewardRaw);
                richChunks.put(position, new RichChunkEntry(position, type, discovered, lastReward));
            } catch (Exception ex) {
                plugin.getLogger().warning("Пропущен некорректный богатый чанк " + key + ": " + ex.getMessage());
            }
        }
    }

    public void reload() {
        load();
    }

    public void distributeDailyRewards(TownManager townManager) {
        LocalDate today = LocalDate.now();
        boolean changed = false;
        for (RichChunkEntry entry : richChunks.values()) {
            if (entry.getLastReward() != null && !entry.getLastReward().isBefore(today.atStartOfDay())) {
                continue;
            }

            Town town = townManager.getTownAtPosition(entry.getPosition());
            if (town == null) {
                continue;
            }

            ItemStack reward = new ItemStack(entry.getType().getIcon(), REWARD_AMOUNT);
            if (townManager.addToInventory(town, reward)) {
                entry.setLastReward(today.atStartOfDay());
                changed = true;
            }
        }

        if (changed) {
            save();
        }
    }
    public boolean canClaim(RichChunkEntry entry) {
        if (entry == null) {
            return false;
        }
        LocalDateTime last = entry.getLastReward();
        if (last == null) {
            return true;
        }
        return Duration.between(last, LocalDateTime.now()).toHours() >= 24;
    }

    public boolean claimReward(Town town, RichChunkEntry entry, TownManager townManager) {
        if (town == null || entry == null || townManager == null) {
            return false;
        }
        if (!canClaim(entry)) {
            return false;
        }
        ItemStack reward = new ItemStack(entry.getType().getIcon(), REWARD_AMOUNT);
        if (!townManager.addToInventory(town, reward)) {
            return false;
        }
        entry.setLastReward(LocalDateTime.now());
        save();
        return true;
    }

    public LocalDateTime nextClaimAt(RichChunkEntry entry) {
        if (entry == null || entry.getLastReward() == null) {
            return null;
        }
        return entry.getLastReward().plusHours(24);
    }

    private LocalDateTime parseDateTime(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw, DATETIME_FORMAT);
        } catch (Exception ignored) {
        }
        try {
            LocalDate fallback = LocalDate.parse(raw, DATE_FORMAT);
            return fallback.atStartOfDay();
        } catch (Exception ignored) {
            return null;
        }
    }
}