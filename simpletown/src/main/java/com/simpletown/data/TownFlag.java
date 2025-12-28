package com.simpletown.data;

import org.bukkit.Material;

public enum TownFlag {
    BREAK("break", "Ломать", Material.IRON_PICKAXE),
    PLACE("place", "Ставить", Material.GRASS_BLOCK),
    INTERACT("interact", "Взаимодействовать", Material.LEVER),
    PVP("pvp", "ПвП", Material.IRON_SWORD),
    ANIMAL_SPAWN("animal-spawn", "Спавн животных", Material.COW_SPAWN_EGG),
    MONSTER_SPAWN("monster-spawn", "Спавн мобов", Material.ZOMBIE_SPAWN_EGG);

    private final String configKey;
    private final String displayName;
    private final Material icon;

    TownFlag(String configKey, String displayName, Material icon) {
        this.configKey = configKey;
        this.displayName = displayName;
        this.icon = icon;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }
}