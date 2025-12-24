package com.simpletown.service;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PoliticalScoreService {
    private final Plugin plugin;
    private final File file;
    private final Map<UUID, Integer> scores = new HashMap<>();

    public PoliticalScoreService(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "polit_score.yml");
        load();
    }

    public int getScore(UUID uuid) {
        return scores.getOrDefault(uuid, 0);
    }

    public void addScore(UUID uuid, int amount) {
        if (uuid == null || amount <= 0) {
            return;
        }
        scores.put(uuid, getScore(uuid) + amount);
        save();
    }

    public void reload() {
        load();
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> entry : scores.entrySet()) {
            config.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить polit_score.yml: " + e.getMessage());
        }
    }

    private void load() {
        scores.clear();
        if (!file.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String key : config.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                int value = config.getInt(key, 0);
                scores.put(uuid, Math.max(0, value));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Пропущено некорректное значение политических очков для ключа " + key);
            }
        }
    }
}