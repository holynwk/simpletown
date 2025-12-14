package com.simpletown.service;

import com.simpletown.data.BuildingType;
import com.simpletown.data.Town;
import com.simpletown.data.TownManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class CraftRestrictionService implements Listener {
    private final TownManager townManager;
    private final MessageService messages;
    private final Map<Material, Restriction> restrictions = new HashMap<>();
    private final List<BuildingLock> buildingLocks = new ArrayList<>();

    public CraftRestrictionService(Plugin plugin, TownManager townManager, MessageService messages) {
        this.townManager = townManager;
        this.messages = messages;
        loadRestrictions(plugin);
    }

    private void loadRestrictions(Plugin plugin) {
        restrictions.clear();
        buildingLocks.clear();
        for (Map<?, ?> raw : plugin.getConfig().getMapList("crafting.restricted")) {
            Object itemRaw = raw.get("item");
            if (!(itemRaw instanceof String itemName)) {
                continue;
            }
            Material material = Material.matchMaterial(itemName.toUpperCase(Locale.ROOT));
            if (material == null) {
                continue;
            }
            Object minAgeRaw = raw.get("min-age");
            int minAge = minAgeRaw instanceof Number number ? number.intValue() : 0;
            boolean requireTown = Boolean.TRUE.equals(raw.get("require-town"));
            boolean mayorOnly = Boolean.TRUE.equals(raw.get("mayor-only"));
            restrictions.put(material, new Restriction(minAge, requireTown, mayorOnly));
        }

        for (Map<?, ?> raw : plugin.getConfig().getMapList("crafting.building-locks")) {
            Object buildingRaw = raw.get("building");
            Object levelRaw = raw.get("level");
            if (!(buildingRaw instanceof String building) || !(levelRaw instanceof Number number)) {
                continue;
            }
            try {
                BuildingType type = BuildingType.valueOf(building.toUpperCase(Locale.ROOT));
                List<String> prefixes = new ArrayList<>();
                Set<String> items = new HashSet<>();
                Object prefixesRaw = raw.get("prefixes");
                if (prefixesRaw instanceof List<?> list) {
                    for (Object obj : list) {
                        if (obj instanceof String str) {
                            prefixes.add(str.toUpperCase(Locale.ROOT));
                        }
                    }
                }
                Object itemsRaw = raw.get("items");
                if (itemsRaw instanceof List<?> list) {
                    for (Object obj : list) {
                        if (obj instanceof String str) {
                            items.add(str.toUpperCase(Locale.ROOT));
                        }
                    }
                }
                if (!prefixes.isEmpty() || !items.isEmpty()) {
                    buildingLocks.add(new BuildingLock(type, number.intValue(), prefixes, items));
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack result = event.getRecipe() != null ? event.getRecipe().getResult() : null;
        if (result == null) {
            return;
        }
        String materialName = result.getType().name();
        BuildingLock lock = buildingLocks.stream()
                .filter(entry -> entry.matches(materialName))
                .findFirst()
                .orElse(null);

        Town town = townManager.getTownByMember(player.getName());
        if (lock != null) {
            if (town == null) {
                deny(event, player, "craft.require-town", result);
                return;
            }
            if (town.getBuildingLevel(lock.type()) < lock.level()) {
                deny(event, player, "craft.require-building", result, Map.of(
                        "building", lock.type().getDisplayName(),
                        "level", String.valueOf(lock.level())
                ));
                return;
            }
        }

        Restriction restriction = restrictions.get(result.getType());
        if (restriction == null) {
            return;
        }

        if (town == null && (restriction.requireTown || restriction.minAge > 0 || restriction.mayorOnly)) {
            deny(event, player, "craft.require-town", result);
            return;
        }
        if (restriction.mayorOnly && (town == null || !town.getMayor().equalsIgnoreCase(player.getName()))) {
            deny(event, player, "craft.require-mayor", result);
            return;
        }
        if (restriction.minAge > 0 && town != null && town.getAgeLevel() < restriction.minAge) {
            deny(event, player, "craft.require-age", result, Map.of("age", String.valueOf(restriction.minAge)));
        }
    }

    private void deny(CraftItemEvent event, Player player, String messagePath, ItemStack result) {
        deny(event, player, messagePath, result, Map.of());
    }

    private void deny(CraftItemEvent event, Player player, String messagePath, ItemStack result, Map<String, String> extra) {
        event.setCancelled(true);
        Map<String, String> placeholders = new HashMap<>(extra);
        placeholders.put("item", displayName(result));
        messages.sendError(player, messagePath, placeholders);
    }

    private String displayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private record Restriction(int minAge, boolean requireTown, boolean mayorOnly) {
    }

    private record BuildingLock(BuildingType type, int level, List<String> prefixes, Set<String> items) {
        boolean matches(String materialName) {
            if (items.contains(materialName)) {
                return true;
            }
            for (String prefix : prefixes) {
                if (materialName.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        }
    }
}