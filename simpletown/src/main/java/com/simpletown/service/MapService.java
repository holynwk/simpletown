package com.simpletown.service;

import com.simpletown.data.ChunkPosition;
import com.simpletown.data.Town;
import com.simpletown.data.TownManager;
import com.simpletown.map.MapRenderer;
import com.simpletown.war.WarConflict;
import com.simpletown.war.WarManager;
import com.simpletown.war.WarStatus;
import com.simpletown.map.TownPopupFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class MapService {
    private final TownManager townManager;
    private final WarManager warManager;
    private final MapRenderer renderer;
    private final Set<String> knownCities = new HashSet<>();
    private final Set<String> knownOccupations = new HashSet<>();

    public MapService(TownManager townManager, WarManager warManager, MapRenderer renderer) {
        this.townManager = townManager;
        this.warManager = warManager;
        this.renderer = renderer;
    }

    public void refreshTown(Town town) {
        if (town == null) {
            return;
        }
        String cityId = cityId(town.getName());
        Map<String, Set<ChunkPosition>> chunks = byWorld(town.getChunks());
        String description = TownPopupFormatter.buildDescription(town, warManager);
        renderer.upsertCity(cityId, town.getName(), description, chunks, town.getMapColor());
        knownCities.add(cityId);
        refreshOccupied();
    }


    public void removeTown(Town town) {
        if (town == null) {
            return;
        }
        String cityId = cityId(town.getName());
        renderer.removeCity(cityId);
        knownCities.remove(cityId);
    }

    public void refreshAll() {
        Set<String> current = townManager.getTowns().stream()
                .map(Town::getName)
                .map(this::cityId)
                .collect(Collectors.toSet());
        for (Town town : townManager.getTowns()) {
            refreshTown(town);
        }
        Set<String> stale = new HashSet<>(knownCities);
        stale.removeAll(current);
        for (String removed : stale) {
            renderer.removeCity(removed);
        }
        knownCities.clear();
        knownCities.addAll(current);
        refreshOccupied();
    }

    public void clearAll() {
        renderer.clearAll();
        knownCities.clear();
        knownOccupations.clear();
    }

    public void refreshOccupied() {
        Set<String> active = new HashSet<>();
        if (warManager != null) {
            for (WarConflict conflict : new HashSet<>(warManager.getConflicts())) {
                if (conflict.getStatus() == WarStatus.ENDED) {
                    continue;
                }
                handleOccupation(conflict.getAttacker(), conflict.getAttackerOccupied(), active);
                handleOccupation(conflict.getDefender(), conflict.getDefenderOccupied(), active);
            }
        }
        Set<String> stale = new HashSet<>(knownOccupations);
        stale.removeAll(active);
        for (String removed : stale) {
            renderer.removeOccupied(removed);
        }
        knownOccupations.clear();
        knownOccupations.addAll(active);
    }

    private void handleOccupation(String controller, Set<ChunkPosition> chunks, Set<String> active) {
        if (chunks == null) {
            return;
        }
        String occupationId = occupationId(controller);
        active.add(occupationId);
        Map<String, Set<ChunkPosition>> byWorld = byWorld(chunks);
        String color = townColor(controller);
        if (byWorld.isEmpty()) {
            renderer.removeOccupied(occupationId);
            return;
        }
        renderer.upsertOccupiedChunks(occupationId, controller, byWorld, color);
    }

    private String townColor(String townName) {
        Town town = townManager.getTownByName(townName);
        return town == null ? "#FFD700" : town.getMapColor();
    }

    private Map<String, Set<ChunkPosition>> byWorld(Set<ChunkPosition> chunks) {
        Map<String, Set<ChunkPosition>> grouped = new HashMap<>();
        if (chunks == null) {
            return grouped;
        }
        for (ChunkPosition pos : chunks) {
            grouped.computeIfAbsent(pos.getWorld(), w -> new HashSet<>()).add(pos);
        }
        return grouped;
    }

    private String cityId(String name) {
        return (name == null ? "unknown" : name).toLowerCase(Locale.ROOT).replaceAll("/[a-zA-Z0-9_-]/", "_");
    }

    private String occupationId(String controller) {
        return "occupied_" + cityId(controller == null ? "unknown" : controller);
    }
}