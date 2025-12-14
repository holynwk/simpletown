package com.simpletown.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class TownStorage {
    private final File file;
    private final TownFlags defaultCitizenFlags;
    private final TownFlags defaultOutsiderFlags;
    private final boolean defaultOpen;
    private final String defaultColor;

    public TownStorage(File dataFolder, TownFlags defaultCitizenFlags, TownFlags defaultOutsiderFlags, boolean defaultOpen, String defaultColor) {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        this.file = new File(dataFolder, "towns.yml");
        this.defaultCitizenFlags = defaultCitizenFlags;
        this.defaultOutsiderFlags = defaultOutsiderFlags;
        this.defaultOpen = defaultOpen;
        this.defaultColor = defaultColor;
    }

    public List<Town> loadTowns() {
        if (!file.exists()) {
            return new ArrayList<>();
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<Town> towns = new ArrayList<>();
        for (String townName : config.getKeys(false)) {
            String path = townName + ".";
            String mayorName = config.getString(path + "mayor");
            String capitalKey = config.getString(path + "capital");
            double bank = config.getDouble(path + "bank", 0.0);
            boolean open = config.getBoolean(path + "open", defaultOpen);
            int ageLevel = config.getInt(path + "age", AgeTier.AGE1.getLevel());
            String color = config.getString(path + "color", defaultColor);
            if (mayorName == null || capitalKey == null) {
                continue;
            }
            ChunkPosition capital = ChunkPosition.deserialize(capitalKey);

            TownFlags citizenFlags = TownFlags.fromSection(config.getConfigurationSection(path + "citizen-flags"), defaultCitizenFlags);
            TownFlags outsiderFlags = TownFlags.fromSection(config.getConfigurationSection(path + "outsider-flags"), defaultOutsiderFlags);

            Set<ChunkPosition> chunks = new HashSet<>();
            for (String chunkKey : config.getStringList(path + "chunks")) {
                chunks.add(ChunkPosition.deserialize(chunkKey));
            }

            Set<String> citizens = new HashSet<>();
            for (String citizen : config.getStringList(path + "citizens")) {
                citizens.add(citizen);
            }

            Map<BuildingType, Integer> buildings = new HashMap<>();
            ConfigurationSection buildingsSection = config.getConfigurationSection(path + "buildings");
            if (buildingsSection != null) {
                for (String key : buildingsSection.getKeys(false)) {
                    try {
                        BuildingType type = BuildingType.valueOf(key.toUpperCase(Locale.ROOT));
                        buildings.put(type, buildingsSection.getInt(key));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }

            List<org.bukkit.inventory.ItemStack> inventory = new ArrayList<>();
            List<?> rawInventory = config.getList(path + "inventory", Collections.emptyList());
            for (Object obj : rawInventory) {
                if (obj instanceof org.bukkit.inventory.ItemStack stack) {
                    inventory.add(stack);
                }
            }

            towns.add(new Town(townName, mayorName, citizens, chunks, capital, bank, open, citizenFlags, outsiderFlags, color, ageLevel, buildings, inventory));
        }
        return towns;
    }

    public void saveTowns(List<Town> towns) {
        YamlConfiguration config = new YamlConfiguration();
        for (Town town : towns) {
            String path = town.getName() + ".";
            config.set(path + "mayor", town.getMayor());
            config.set(path + "capital", town.getCapital().serialize());
            config.set(path + "bank", town.getBank());
            config.set(path + "open", town.isOpen());
            config.set(path + "age", town.getAgeLevel());
            config.set(path + "color", town.getMapColor());
            config.set(path + "citizen-flags", town.getCitizenFlags().serialize());
            config.set(path + "outsider-flags", town.getOutsiderFlags().serialize());

            Map<String, Integer> buildingLevels = new HashMap<>();
            for (Map.Entry<BuildingType, Integer> entry : town.getBuildingLevels().entrySet()) {
                buildingLevels.put(entry.getKey().name().toLowerCase(Locale.ROOT), entry.getValue());
            }
            config.set(path + "buildings", buildingLevels);

            List<String> chunkKeys = new ArrayList<>();
            for (ChunkPosition pos : town.getChunks()) {
                chunkKeys.add(pos.serialize());
            }
            config.set(path + "chunks", chunkKeys);

            config.set(path + "citizens", new ArrayList<>(town.getCitizens()));
            config.set(path + "inventory", town.getInventoryContents());
        }

        try {
            config.save(file);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось сохранить города", e);
        }
    }
}