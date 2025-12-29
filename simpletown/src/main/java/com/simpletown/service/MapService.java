package com.simpletown.service;

import com.simpletown.data.ChunkPosition;
import com.simpletown.data.Town;
import com.simpletown.data.TownManager;
import com.simpletown.map.MapRenderer;
import com.simpletown.war.WarConflict;
import com.simpletown.war.WarManager;
import com.simpletown.war.WarStatus;
import com.simpletown.war.WarFlag;
import com.simpletown.map.TownPopupFormatter;
import java.util.ArrayList;
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
    private final Set<String> knownCaptures = new HashSet<>();

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
        Map<String, Set<ChunkPosition>> chunks = byWorld(visibleChunks(town));
        String description = TownPopupFormatter.buildDescription(town, warManager);
        renderer.upsertCity(cityId, town.getName(), description, chunks, town.getMapColor(), town.getCapital());
        knownCities.add(cityId);
        refreshOccupied();
        refreshCaptures();
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
        refreshCaptures();
    }

    public void clearAll() {
        renderer.clearAll();
        knownCities.clear();
        knownOccupations.clear();
        knownCaptures.clear();
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

    public void refreshCaptures() {
        Set<String> active = new HashSet<>();
        if (warManager != null) {
            for (WarConflict conflict : new HashSet<>(warManager.getConflicts())) {
                if (conflict.getStatus() != WarStatus.ACTIVE) {
                    continue;
                }
                for (WarFlag flag : new ArrayList<>(conflict.getActiveFlags())) {
                    ChunkPosition chunk = flag.getChunk();
                    if (chunk == null) {
                        continue;
                    }
                    String id = captureId(chunk);
                    active.add(id);
                    renderer.upsertCaptureFlag(id, flag.getOwningTown(), chunk);
                }
            }
        }
        Set<String> stale = new HashSet<>(knownCaptures);
        stale.removeAll(active);
        for (String removed : stale) {
            renderer.removeCaptureFlag(removed);
        }
        knownCaptures.clear();
        knownCaptures.addAll(active);
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

    private Set<ChunkPosition> visibleChunks(Town town) {
        Set<ChunkPosition> visible = new HashSet<>();
        if (town == null || town.getChunks() == null) {
            return visible;
        }
        for (ChunkPosition position : town.getChunks()) {
            if (warManager == null) {
                visible.add(position);
                continue;
            }
            String controller = warManager.getChunkController(position);
            if (controller == null || controller.isBlank() || controller.equalsIgnoreCase(town.getName())) {
                visible.add(position);
            }
        }
        return visible;
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
        return (name == null ? "unknown" : name).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
    }

    private String occupationId(String controller) {
        return "occupied_" + cityId(controller == null ? "unknown" : controller);
    }

    private String captureId(ChunkPosition position) {
        if (position == null) {
            return "capture_unknown";
        }
        return String.format("capture_%s_%d_%d", position.getWorld(), position.getX(), position.getZ());
    }
}