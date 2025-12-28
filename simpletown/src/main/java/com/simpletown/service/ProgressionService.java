package com.simpletown.service;

import com.simpletown.data.AgeTier;
import com.simpletown.data.BuildingType;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class ProgressionService {
    public static class AgeRequirement {
        private final int requiredChunks;
        private final double cost;
        private final Map<BuildingType, Integer> requiredBuildings;
        private final int requiredCitizens;
        private final double reward;

        public AgeRequirement(int requiredChunks, double cost, Map<BuildingType, Integer> requiredBuildings, int requiredCitizens, double reward) {
            this.requiredChunks = requiredChunks;
            this.cost = cost;
            this.requiredBuildings = requiredBuildings;
            this.requiredCitizens = requiredCitizens;
            this.reward = reward;
        }

        public int getRequiredChunks() {
            return requiredChunks;
        }

        public double getCost() {
            return cost;
        }

        public Map<BuildingType, Integer> getRequiredBuildings() {
            return requiredBuildings;
        }

        public int getRequiredCitizens() {
            return requiredCitizens;
        }

        public double getReward() {
            return reward;
        }
    }

    public static class BuildingCost {
        private final double cost;
        private final double income;

        public BuildingCost(double cost, double income) {
            this.cost = cost;
            this.income = income;
        }

        public double getCost() {
            return cost;
        }

        public double getIncome() {
            return income;
        }
    }

    private final Map<AgeTier, AgeRequirement> ageRequirements = new HashMap<>();
    private final Map<BuildingType, Map<Integer, BuildingCost>> buildingCosts = new HashMap<>();

    public ProgressionService(Plugin plugin) {
        loadAges(plugin);
        loadBuildings(plugin);
    }

    private void loadAges(Plugin plugin) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("progress.ages");
        if (section == null) {
            return;
        }
        for (AgeTier tier : AgeTier.values()) {
            ConfigurationSection tierSection = section.getConfigurationSection(String.valueOf(tier.getLevel()));
            if (tierSection == null) {
                continue;
            }
            int chunks = tierSection.getInt("chunks", 0);
            double cost = tierSection.getDouble("cost", 0.0);
            int citizens = tierSection.getInt("citizens", 0);
            double reward = tierSection.getDouble("reward", 0.0);
            Map<BuildingType, Integer> requiredBuildings = new HashMap<>();
            ConfigurationSection reqSection = tierSection.getConfigurationSection("buildings");
            if (reqSection != null) {
                for (String key : reqSection.getKeys(false)) {
                    try {
                        BuildingType type = BuildingType.valueOf(key.toUpperCase(Locale.ROOT));
                        int level = reqSection.getInt(key, 1);
                        requiredBuildings.put(type, level);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            ageRequirements.put(tier, new AgeRequirement(chunks, cost, requiredBuildings, citizens, reward));
        }
    }

    private void loadBuildings(Plugin plugin) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("progress.buildings");
        if (section == null) {
            return;
        }
        for (BuildingType type : BuildingType.values()) {
            ConfigurationSection buildingSection = section.getConfigurationSection(type.name().toLowerCase(Locale.ROOT));
            if (buildingSection == null) {
                continue;
            }
            Map<Integer, BuildingCost> levelCosts = new HashMap<>();
            if (type.isMultiLevel()) {
                ConfigurationSection levels = buildingSection.getConfigurationSection("levels");
                if (levels != null) {
                    for (String levelKey : levels.getKeys(false)) {
                        int level = Integer.parseInt(levelKey);
                        double cost = levels.getDouble(levelKey + ".cost", 0.0);
                        double income = levels.getDouble(levelKey + ".income", 0.0);
                        levelCosts.put(level, new BuildingCost(cost, income));
                    }
                }
            } else {
                double cost = buildingSection.getDouble("cost", 0.0);
                double income = buildingSection.getDouble("income", 0.0);
                levelCosts.put(1, new BuildingCost(cost, income));
            }
            buildingCosts.put(type, levelCosts);
        }
    }

    public AgeRequirement getRequirement(AgeTier tier) {
        return ageRequirements.getOrDefault(tier, new AgeRequirement(0, 0.0, Collections.emptyMap(), 0, 0.0));
    }

    public BuildingCost getBuildingCost(BuildingType type, int level) {
        Map<Integer, BuildingCost> map = buildingCosts.get(type);
        if (map == null) {
            return new BuildingCost(0.0, 0.0);
        }
        return map.getOrDefault(level, new BuildingCost(0.0, 0.0));
    }

    public ChatColor statusColor(boolean enabled) {
        return enabled ? ChatColor.GREEN : ChatColor.RED;
    }
}