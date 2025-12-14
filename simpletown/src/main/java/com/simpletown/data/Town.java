package com.simpletown.data;

import java.util.*;

public class Town {
    private final String name;
    private final String mayor;
    private final Set<String> citizens;
    private final Set<ChunkPosition> chunks;
    private ChunkPosition capital;
    private double bank;
    private boolean open;
    private final TownFlags citizenFlags;
    private final TownFlags outsiderFlags;
    private int ageLevel;
    private final Map<BuildingType, Integer> buildingLevels;
    private List<org.bukkit.inventory.ItemStack> inventoryContents;
    private String mapColor;

    public Town(String name, String mayor, Set<String> citizens, Set<ChunkPosition> chunks, ChunkPosition capital, boolean open, TownFlags citizenFlags, TownFlags outsiderFlags, String mapColor) {
        this(name, mayor, citizens, chunks, capital, 0.0, open, citizenFlags, outsiderFlags, mapColor, AgeTier.AGE1.getLevel(), new HashMap<>(), new ArrayList<>());
    }

    public Town(String name, String mayor, Set<String> citizens, Set<ChunkPosition> chunks, ChunkPosition capital, double bank, boolean open, TownFlags citizenFlags, TownFlags outsiderFlags, String mapColor) {
        this(name, mayor, citizens, chunks, capital, bank, open, citizenFlags, outsiderFlags, mapColor, AgeTier.AGE1.getLevel(), new HashMap<>(), new ArrayList<>());
    }

    public Town(String name, String mayor, Set<String> citizens, Set<ChunkPosition> chunks, ChunkPosition capital, double bank, boolean open, TownFlags citizenFlags, TownFlags outsiderFlags, String mapColor, int ageLevel, Map<BuildingType, Integer> buildingLevels, List<org.bukkit.inventory.ItemStack> inventoryContents) {
        this.name = name;
        this.mayor = normalizeName(mayor);
        this.citizens = new HashSet<>();
        for (String citizen : citizens) {
            this.citizens.add(normalizeName(citizen));
        }
        this.chunks = new HashSet<>(chunks);
        this.capital = capital;
        this.bank = bank;
        this.open = open;
        this.citizenFlags = new TownFlags(citizenFlags);
        this.outsiderFlags = new TownFlags(outsiderFlags);
        this.ageLevel = Math.max(1, ageLevel);
        this.buildingLevels = new HashMap<>();
        this.buildingLevels.putAll(buildingLevels);
        this.inventoryContents = new ArrayList<>();
        this.mapColor = normalizeColor(mapColor);
        if (inventoryContents != null) {
            for (org.bukkit.inventory.ItemStack item : inventoryContents) {
                this.inventoryContents.add(item == null ? null : item.clone());
            }
        }
    }

    public String getName() {
        return name;
    }

    public String getMayor() {
        return mayor;
    }

    public Set<String> getCitizens() {
        return Collections.unmodifiableSet(citizens);
    }

    public Set<ChunkPosition> getChunks() {
        return Collections.unmodifiableSet(chunks);
    }

    public ChunkPosition getCapital() {
        return capital;
    }

    public void setCapital(ChunkPosition capital) {
        if (capital != null) {
            this.capital = capital;
        }
    }

    public double getBank() {
        return bank;
    }

    public void setBank(double bank) {
        this.bank = Math.max(0, bank);
    }

    public TownFlags getCitizenFlags() {
        return citizenFlags;
    }

    public TownFlags getOutsiderFlags() {
        return outsiderFlags;
    }

    public boolean isOpen() {
        return open;
    }

    public int getAgeLevel() {
        return ageLevel;
    }

    public void setAgeLevel(int ageLevel) {
        this.ageLevel = Math.max(1, ageLevel);
    }

    public int getBuildingLevel(BuildingType type) {
        return buildingLevels.getOrDefault(type, 0);
    }

    public void setBuildingLevel(BuildingType type, int level) {
        if (level <= 0) {
            buildingLevels.remove(type);
        } else {
            buildingLevels.put(type, level);
        }
    }

    public Map<BuildingType, Integer> getBuildingLevels() {
        return Collections.unmodifiableMap(buildingLevels);
    }

    public List<org.bukkit.inventory.ItemStack> getInventoryContents() {
        List<org.bukkit.inventory.ItemStack> copy = new ArrayList<>();
        for (org.bukkit.inventory.ItemStack item : inventoryContents) {
            copy.add(item == null ? null : item.clone());
        }
        return copy;
    }

    public void setInventoryContents(List<org.bukkit.inventory.ItemStack> contents) {
        inventoryContents.clear();
        if (contents == null) {
            return;
        }
        for (org.bukkit.inventory.ItemStack item : contents) {
            inventoryContents.add(item == null ? null : item.clone());
        }
    }

    public String getMapColor() {
        return mapColor;
    }

    public void setMapColor(String mapColor) {
        this.mapColor = normalizeColor(mapColor);
    }

    public boolean ownsChunk(ChunkPosition position) {
        return chunks.contains(position);
    }

    public boolean addChunk(ChunkPosition position) {
        return chunks.add(position);
    }

    public boolean removeChunk(ChunkPosition position) {
        if (position.equals(capital)) {
            return false;
        }
        return chunks.remove(position);
    }

    public boolean isMayor(String playerName) {
        return normalizeName(playerName).equals(mayor);
    }

    public boolean isMember(String playerName) {
        String normalized = normalizeName(playerName);
        return mayor.equals(normalized) || citizens.contains(normalized);
    }

    public boolean addCitizen(String playerName) {
        return citizens.add(normalizeName(playerName));
    }

    public boolean removeCitizen(String playerName) {
        return citizens.remove(normalizeName(playerName));
    }

    public void deposit(double amount) {
        bank += amount;
    }

    public boolean withdraw(double amount) {
        if (amount > bank) {
            return false;
        }
        bank -= amount;
        return true;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    private String normalizeColor(String color) {
        if (color == null) {
            return "#FFD700";
        }
        String trimmed = color.trim();
        if (!trimmed.startsWith("#")) {
            trimmed = "#" + trimmed;
        }
        if (trimmed.length() == 7) {
            return trimmed.toUpperCase(Locale.ROOT);
        }
        if (trimmed.length() == 4) {
            char r = trimmed.charAt(1);
            char g = trimmed.charAt(2);
            char b = trimmed.charAt(3);
            return ("#" + r + r + g + g + b + b).toUpperCase(Locale.ROOT);
        }
        return "#FFD700";
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }
}