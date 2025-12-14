package com.simpletown.data;

import org.bukkit.Chunk;

import java.util.*;

public class TownManager {
    private final org.bukkit.plugin.Plugin plugin;
    private final TownStorage storage;
    private final TownFlags defaultCitizenFlags;
    private final TownFlags defaultOutsiderFlags;
    private final boolean defaultOpen;
    private final Map<String, Town> townsByName = new HashMap<>();
    private final Map<String, Town> townsByMayor = new HashMap<>();
    private final Map<String, Town> townsByMember = new HashMap<>();
    private final Map<ChunkPosition, Town> claimedChunks = new HashMap<>();

    public TownManager(org.bukkit.plugin.Plugin plugin) {
        this.plugin = plugin;
        this.defaultCitizenFlags = loadDefaults("town.defaults.citizens");
        this.defaultOutsiderFlags = loadDefaults("town.defaults.outsiders");
        this.defaultOpen = plugin.getConfig().getBoolean("town.defaults.open", true);
        this.storage = new TownStorage(plugin.getDataFolder(), defaultCitizenFlags, defaultOutsiderFlags, defaultOpen);
        reload();
    }

    public void reload() {
        townsByName.clear();
        townsByMayor.clear();
        townsByMember.clear();
        claimedChunks.clear();
        for (Town town : storage.loadTowns()) {
            registerTown(town);
        }
    }

    public void save() {
        storage.saveTowns(new ArrayList<>(townsByName.values()));
    }

    public boolean isChunkClaimed(Chunk chunk) {
        return claimedChunks.containsKey(ChunkPosition.fromChunk(chunk));
    }

    public Town getTownAtChunk(Chunk chunk) {
        return claimedChunks.get(ChunkPosition.fromChunk(chunk));
    }

    public Town getTownByMayor(String name) {
        if (name == null) {
            return null;
        }
        return townsByMayor.get(name.toLowerCase(Locale.ROOT));
    }

    public Town getTownByName(String name) {
        return townsByName.get(name.toLowerCase(Locale.ROOT));
    }

    public Town getTownByMember(String name) {
        if (name == null) {
            return null;
        }
        return townsByMember.get(name.toLowerCase(Locale.ROOT));
    }

    public List<String> getAllTownNames() {
        return new ArrayList<>(townsByName.keySet()).stream()
                .map(key -> townsByName.get(key).getName())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public TownFlags getDefaultCitizenFlags() {
        return defaultCitizenFlags.copy();
    }

    public TownFlags getDefaultOutsiderFlags() {
        return defaultOutsiderFlags.copy();
    }

    public boolean getDefaultOpen() {
        return defaultOpen;
    }

    public boolean isDefaultFlagEnabled(TownFlag flag, boolean forCitizens) {
        TownFlags target = forCitizens ? defaultCitizenFlags : defaultOutsiderFlags;
        return target.isEnabled(flag);
    }

    public void addTown(Town town) {
        registerTown(town);
        save();
    }

    public void deleteTown(Town town) {
        townsByName.remove(town.getName().toLowerCase(Locale.ROOT));
        townsByMayor.remove(town.getMayor());
        townsByMember.remove(town.getMayor());
        for (ChunkPosition chunk : town.getChunks()) {
            claimedChunks.remove(chunk);
        }
        for (String citizen : town.getCitizens()) {
            townsByMember.remove(citizen);
        }
        save();
    }

    private void registerTown(Town town) {
        townsByName.put(town.getName().toLowerCase(Locale.ROOT), town);
        townsByMayor.put(town.getMayor(), town);
        townsByMember.put(town.getMayor(), town);
        for (String citizen : town.getCitizens()) {
            townsByMember.put(citizen, town);
        }
        for (ChunkPosition chunk : town.getChunks()) {
            claimedChunks.put(chunk, town);
        }
    }

    public boolean claimChunk(Town town, Chunk chunk) {
        ChunkPosition position = ChunkPosition.fromChunk(chunk);
        if (claimedChunks.containsKey(position)) {
            return false;
        }
        if (!town.addChunk(position)) {
            return false;
        }
        claimedChunks.put(position, town);
        save();
        return true;
    }

    public boolean unclaimChunk(Town town, Chunk chunk) {
        ChunkPosition position = ChunkPosition.fromChunk(chunk);
        if (!town.ownsChunk(position) || position.equals(town.getCapital())) {
            return false;
        }
        if (!town.removeChunk(position)) {
            return false;
        }
        claimedChunks.remove(position);
        save();
        return true;
    }

    public boolean addCitizen(Town town, String playerName) {
        if (town.isMember(playerName)) {
            return false;
        }
        if (!town.addCitizen(playerName)) {
            return false;
        }
        townsByMember.put(playerName.toLowerCase(Locale.ROOT), town);
        save();
        return true;
    }

    public boolean removeCitizen(Town town, String playerName) {
        if (!town.removeCitizen(playerName)) {
            return false;
        }
        townsByMember.remove(playerName.toLowerCase(Locale.ROOT));
        save();
        return true;
    }

    public void deposit(Town town, double amount) {
        town.deposit(amount);
        save();
    }

    public boolean withdraw(Town town, double amount) {
        if (!town.withdraw(amount)) {
            return false;
        }
        save();
        return true;
    }

    public void setBank(Town town, double amount) {
        town.setBank(amount);
        save();
    }

    public void setAge(Town town, int level) {
        town.setAgeLevel(level);
        save();
    }

    public void setBuildingLevel(Town town, BuildingType type, int level) {
        town.setBuildingLevel(type, level);
        save();
    }

    public void setInventory(Town town, java.util.List<org.bukkit.inventory.ItemStack> contents) {
        town.setInventoryContents(contents);
        save();
    }

    public void setDefaultFlag(TownFlag flag, boolean forCitizens, boolean enabled) {
        TownFlags target = forCitizens ? defaultCitizenFlags : defaultOutsiderFlags;
        target.set(flag, enabled);
        String path = "town.defaults." + (forCitizens ? "citizens." : "outsiders.") + flag.getConfigKey();
        plugin.getConfig().set(path, enabled);
        plugin.saveConfig();
    }

    private TownFlags loadDefaults(String basePath) {
        TownFlags defaults = new TownFlags();
        for (TownFlag flag : TownFlag.values()) {
            boolean value = plugin.getConfig().getBoolean(basePath + "." + flag.getConfigKey(), defaults.isEnabled(flag));
            defaults.set(flag, value);
        }
        return defaults;
    }
}