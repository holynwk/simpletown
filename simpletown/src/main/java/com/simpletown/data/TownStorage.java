package com.simpletown.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;

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

        YamlConfiguration config = new YamlConfiguration();
        boolean locationWasRegistered = ConfigurationSerialization.getClassByAlias(Location.class.getName()) != null
                || ConfigurationSerialization.getClassByAlias(Location.class.getSimpleName()) != null;
        try {
            ConfigurationSerialization.unregisterClass(Location.class);
            config.load(file);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось загрузить города", e);
        } finally {
            if (locationWasRegistered) {
                ConfigurationSerialization.registerClass(Location.class);
            }
        }
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

            Map<ChunkPosition, PlotData> plots = new HashMap<>();
            ConfigurationSection plotsSection = config.getConfigurationSection(path + "plots");
            TownFlags plotDefaults = new TownFlags(defaultCitizenFlags);
            if (plotsSection != null) {
                for (String chunkKey : plotsSection.getKeys(false)) {
                    ChunkPosition position = ChunkPosition.deserialize(chunkKey);
                    if (position == null) {
                        continue;
                    }
                    ConfigurationSection plotSection = plotsSection.getConfigurationSection(chunkKey);
                    if (plotSection == null) {
                        continue;
                    }
                    String owner = plotSection.getString("owner");
                    List<String> residents = plotSection.getStringList("residents");
                    double sale = plotSection.getDouble("sale", 0.0);
                    TownFlags flags = TownFlags.fromSection(plotSection.getConfigurationSection("flags"), plotDefaults);
                    plots.put(position, new PlotData(owner, residents, sale, flags));
                }
            }

            for (ChunkPosition position : chunks) {
                plots.putIfAbsent(position, PlotData.createMunicipal(plotDefaults));
            }

            Location spawn = deserializeLocation(config.get(path + "spawn"));

            towns.add(new Town(townName, mayorName, citizens, chunks, capital, bank, open, citizenFlags, outsiderFlags, color, ageLevel, buildings, inventory, plots, spawn));
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

            Map<String, Object> plots = new HashMap<>();
            for (Map.Entry<ChunkPosition, PlotData> entry : town.getPlots().entrySet()) {
                Map<String, Object> plotData = new HashMap<>();
                PlotData plot = entry.getValue();
                plotData.put("owner", plot.getOwner());
                plotData.put("residents", new ArrayList<>(plot.getResidents()));
                plotData.put("sale", plot.getSalePrice());
                plotData.put("flags", plot.getFlags().serialize());
                plots.put(entry.getKey().serialize(), plotData);
            }
            config.set(path + "plots", plots);
            config.set(path + "spawn", serializeLocation(town.getSpawn()));
        }

        try {
            config.save(file);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось сохранить города", e);
        }
    }
    private Map<String, Object> serializeLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("world", location.getWorld().getName());
        data.put("x", location.getX());
        data.put("y", location.getY());
        data.put("z", location.getZ());
        data.put("yaw", location.getYaw());
        data.put("pitch", location.getPitch());
        return data;
    }

    @SuppressWarnings("unchecked")
    private Location deserializeLocation(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Location location) {
            return location.getWorld() != null ? location : null;
        }
        Map<String, Object> data;
        if (raw instanceof ConfigurationSection section) {
            data = new HashMap<>();
            for (String key : section.getKeys(false)) {
                data.put(key, section.get(key));
            }
        } else if (raw instanceof Map<?, ?> map) {
            data = new HashMap<>();
            map.forEach((k, v) -> {
                if (k != null) {
                    data.put(k.toString(), v);
                }
            });
        } else {
            return null;
        }

        String worldName = Objects.toString(data.get("world"), null);
        if (worldName == null) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        double x = parseDouble(data.get("x"));
        double y = parseDouble(data.get("y"));
        double z = parseDouble(data.get("z"));
        float yaw = (float) parseDouble(data.getOrDefault("yaw", 0.0));
        float pitch = (float) parseDouble(data.getOrDefault("pitch", 0.0));
        return new Location(world, x, y, z, yaw, pitch);
    }

    private double parseDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}