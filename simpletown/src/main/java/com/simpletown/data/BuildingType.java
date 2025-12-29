package com.simpletown.data;

import org.bukkit.Material;

public enum BuildingType {
    WAREHOUSE("Склад", Material.CHEST, 3),
    BANK("Банк", Material.GOLD_INGOT, 3),
    WORKSHOP("Станок", Material.STONECUTTER, 5),
    ARMORY("Оружейная", Material.STONE_SWORD, 5),
    UNIVERSITY("Университет", Material.BOOKSHELF, 1),
    POWER_PLANT("Электростанция", Material.REDSTONE, 1),
    LABORATORY("Лаборатория", Material.BREWING_STAND, 1),
    INDUSTRIAL_FACTORY("Промышленный Завод", Material.BRICK, 1),
    MACHINE_FACTORY("Машинный Завод", Material.MINECART, 1),
    MILITARY_FACTORY("Военный Завод", Material.TNT, 1);

    private final String displayName;
    private final Material baseIcon;
    private final int maxLevel;

    BuildingType(String displayName, Material baseIcon, int maxLevel) {
        this.displayName = displayName;
        this.baseIcon = baseIcon;
        this.maxLevel = maxLevel;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getBaseIcon() {
        return baseIcon;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public boolean isMultiLevel() {
        return maxLevel > 1;
    }
}