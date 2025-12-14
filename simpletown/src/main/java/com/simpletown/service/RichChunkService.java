package com.simpletown.service;

import com.simpletown.data.ChunkPosition;
import com.simpletown.data.Town;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class RichChunkService {
    public enum ResourceType {
        COAL(Material.COAL, "Уголь"),
        IRON(Material.IRON_INGOT, "Железо"),
        GOLD(Material.GOLD_INGOT, "Золото"),
        DIAMOND(Material.DIAMOND, "Алмазы"),
        NETHERITE_SCRAP(Material.NETHERITE_SCRAP, "Незеритовый лом");

        private final Material icon;
        private final String displayName;

        ResourceType(Material icon, String displayName) {
            this.icon = icon;
            this.displayName = displayName;
        }

        public Material getIcon() {
            return icon;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static ResourceType random(Random random) {
            ResourceType[] values = values();
            return values[random.nextInt(values.length)];
        }
    }

    public record RichChunkEntry(ChunkPosition position, ResourceType type, LocalDate discovered) {}

    private static final int RICH_CHANCE_PERCENT = 5;
    private static final int REWARD_AMOUNT = 32;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_DATE;

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
            RichChunkEntry entry = new RichChunkEntry(position, ResourceType.random(random), LocalDate.now());
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
        entries.sort(Comparator.comparing((RichChunkEntry entry) -> entry.position().getWorld())
                .thenComparing(entry -> entry.position().getX())
                .thenComparing(entry -> entry.position().getZ()));
        return entries;
    }

    public ResourceType getResource(ChunkPosition position) {
        RichChunkEntry entry = richChunks.get(position);
        return entry == null ? null : entry.type();
    }

    public int getRewardAmount() {
        return REWARD_AMOUNT;
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<ChunkPosition, RichChunkEntry> entry : richChunks.entrySet()) {
            String key = entry.getKey().serialize();
            config.set(key + ".type", entry.getValue().type().name());
            config.set(key + ".discovered", DATE_FORMAT.format(entry.getValue().discovered()));
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
                ResourceType type = ResourceType.valueOf(typeName);
                LocalDate discovered = LocalDate.parse(discoveredRaw, DATE_FORMAT);
                richChunks.put(position, new RichChunkEntry(position, type, discovered));
            } catch (Exception ex) {
                plugin.getLogger().warning("Пропущен некорректный богатый чанк " + key + ": " + ex.getMessage());
            }
        }
    }
}
