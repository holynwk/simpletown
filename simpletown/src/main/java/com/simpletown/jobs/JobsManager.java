package com.simpletown.jobs;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class JobsManager {
    private final File file;
    private final Map<UUID, JobType> jobs = new HashMap<>();
    private final Map<UUID, KillerStatus> killerStatuses = new HashMap<>();

    public JobsManager(Plugin plugin) {
        File folder = plugin.getDataFolder();
        if (!folder.exists()) {
            folder.mkdirs();
        }
        this.file = new File(folder, "jobs.yml");
        load();
    }

    public JobType getJob(UUID uuid) {
        return jobs.getOrDefault(uuid, JobType.UNEMPLOYED);
    }

    public void setJob(UUID uuid, JobType type) {
        if (type == null) {
            jobs.remove(uuid);
        } else {
            jobs.put(uuid, type);
        }
        save();
    }

    public KillerStatus getKillerStatus(UUID uuid) {
        return killerStatuses.get(uuid);
    }

    public void setKillerStatus(UUID uuid, KillerStatus status) {
        if (status == null) {
            killerStatuses.remove(uuid);
        } else {
            killerStatuses.put(uuid, status);
        }
        save();
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, JobType> entry : jobs.entrySet()) {
            config.set("jobs." + entry.getKey(), entry.getValue().name());
        }
        for (Map.Entry<UUID, KillerStatus> entry : killerStatuses.entrySet()) {
            config.set("killer." + entry.getKey(), entry.getValue().serialize());
        }
        try {
            config.save(file);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось сохранить jobs.yml", e);
        }
    }

    private void load() {
        jobs.clear();
        killerStatuses.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("jobs");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    JobType type = JobType.valueOf(section.getString(key, JobType.UNEMPLOYED.name()));
                    jobs.put(uuid, type);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        ConfigurationSection killer = config.getConfigurationSection("killer");
        if (killer != null) {
            for (String key : killer.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    String raw = killer.getString(key);
                    if (raw != null) {
                        killerStatuses.put(uuid, KillerStatus.deserialize(raw));
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }
}