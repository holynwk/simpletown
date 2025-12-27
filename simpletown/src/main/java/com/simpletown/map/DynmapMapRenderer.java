package com.simpletown.map;

import com.simpletown.data.ChunkPosition;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.markers.*;
import org.dynmap.*;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;
import org.dynmap.markers.PolyLineMarker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import com.simpletown.map.ChunkOutlineBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class DynmapMapRenderer implements MapRenderer {
    private static final String CITY_SET_ID = "cities";
    private static final String OCCUPIED_SET_ID = "occupied_chunks";
    private static final int LINE_WEIGHT = 2;
    private static final double CITY_BORDER_OPACITY = 1.0;
    private static final double CITY_BORDER_FILL_OPACITY = 0.35;
    private static final double OCCUPIED_FILL_OPACITY = 0.4;
    private static final double OCCUPIED_LINE_OPACITY = 1.0;

    private final JavaPlugin plugin;
    private final Logger logger;
    private DynmapCommonAPI api;
    private MarkerAPI markerAPI;
    private MarkerSet citySet;
    private MarkerSet cityChunkSet;
    private MarkerSet occupiedSet;
    private final Map<String, Set<String>> cityMarkers = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> occupiedMarkers = new ConcurrentHashMap<>();
    private final Map<String, String> cityDescriptions = new ConcurrentHashMap<>();

    public DynmapMapRenderer(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        plugin.getServer().getPluginManager().registerEvents(new DynmapLifecycleListener(), plugin);
        initialize();
    }

    private void initialize() {
        if (ready()) {
            return;
        }
        DynmapCommonAPI dynmap = resolveDynmap();
        if (dynmap == null) {
            return;
        }
        this.api = dynmap;
        this.markerAPI = dynmap.getMarkerAPI();
        if (markerAPI == null) {
            logger.warning("Dynmap MarkerAPI недоступен. Отрисовка карты отключена.");
            return;
        }
        citySet = ensureSet(CITY_SET_ID, "Города");
        occupiedSet = ensureSet(OCCUPIED_SET_ID, "Оккупированные чанки");
    }

    private DynmapCommonAPI resolveDynmap() {
        Plugin dynmapPlugin = plugin.getServer().getPluginManager().getPlugin("dynmap");
        if (!(dynmapPlugin instanceof DynmapCommonAPI dynmap)) {
            logger.warning("Dynmap не найден или не активирован. Отрисовка карты отключена.");
            return null;
        }
        if (!dynmapPlugin.isEnabled()) {
            logger.warning("Dynmap ещё не активирован. Повторная попытка будет выполнена при включении Dynmap.");
            return null;
        }
        return dynmap;
    }

    @Override
    public void upsertCity(String cityId, String name, String description, Map<String, Set<ChunkPosition>> worldChunks, String colorHex) {
        if (!ready()) {
            return;
        }
        removeMarkers(citySet, cityMarkers.remove(cityId));
        Set<String> created = new HashSet<>();
        int rgb = parseColor(colorHex);
        for (Map.Entry<String, Set<ChunkPosition>> entry : worldChunks.entrySet()) {
            String world = entry.getKey();
            World bukkitWorld = Bukkit.getWorld(world);
            if (bukkitWorld == null || bukkitWorld.getEnvironment() == World.Environment.NETHER) {
                continue;
            }
            List<List<ChunkOutlineBuilder.Point>> outlines = ChunkOutlineBuilder.buildOutlines(entry.getValue());
            int index = 0;
            for (List<ChunkOutlineBuilder.Point> outline : outlines) {
                if (outline.size() < 4) {
                    continue;
                }
                double[] x = outline.stream().mapToDouble(ChunkOutlineBuilder.Point::x).toArray();
                double[] z = outline.stream().mapToDouble(ChunkOutlineBuilder.Point::z).toArray();
                String markerId = "city_" + cityId + "_" + world + "_p" + index++;
                AreaMarker area = citySet.createAreaMarker(markerId, name, false, world, x, z, false);
                if (area != null) {
                    area.setFillStyle(CITY_BORDER_FILL_OPACITY, rgb);
                    area.setLineStyle(LINE_WEIGHT, CITY_BORDER_OPACITY, rgb);
                    area.setDescription(description == null ? "" : description);
                    created.add(markerId);
                }
            }
        }
        if (!created.isEmpty()) {
            cityMarkers.put(cityId, created);
            if (description != null) {
                cityDescriptions.put(cityId, description);
            }
        }
    }

    @Override
    public void removeCity(String cityId) {
        if (!ready()) {
            return;
        }
        removeMarkers(citySet, cityMarkers.remove(cityId));
        cityDescriptions.remove(cityId);
    }

    @Override
    public void upsertCityChunks(String cityId, String name, Map<String, Set<ChunkPosition>> worldChunks, String colorHex) {
        // Городские чанки теперь отображаются вместе с контурами города в одном слое.
        // Для совместимости повторно вызываем upsertCity, чтобы обновить заливку.
        String description = cityDescriptions.getOrDefault(cityId, "");
        upsertCity(cityId, name, description, worldChunks, colorHex);
    }

    @Override
    public void upsertOccupiedChunks(String controllerId, String controllerName, Map<String, Set<ChunkPosition>> worldChunks, String colorHex) {
        if (!ready()) {
            return;
        }
        removeMarkers(occupiedSet, occupiedMarkers.remove(controllerId));
        Set<String> created = new HashSet<>();
        int rgb = parseColor(colorHex);
        for (Map.Entry<String, Set<ChunkPosition>> entry : worldChunks.entrySet()) {
            String world = entry.getKey();
            for (ChunkPosition pos : entry.getValue()) {
                World bukkitWorld = Bukkit.getWorld(world);
                if (bukkitWorld == null || bukkitWorld.getEnvironment() == World.Environment.NETHER) {
                    continue;
                }
                double x0 = pos.getX() * 16.0;
                double z0 = pos.getZ() * 16.0;
                double[] x = new double[]{x0, x0 + 16.0, x0 + 16.0, x0};
                double[] z = new double[]{z0, z0, z0 + 16.0, z0 + 16.0};
                String markerId = "occupied_" + controllerId + "_" + world + "_" + pos.getX() + "_" + pos.getZ();
                AreaMarker area = occupiedSet.createAreaMarker(markerId, "Оккупировано " + controllerName, false, world, x, z, false);
                if (area != null) {
                    area.setFillStyle(OCCUPIED_FILL_OPACITY, rgb);
                    area.setLineStyle(LINE_WEIGHT, OCCUPIED_LINE_OPACITY, rgb);
                    created.add(markerId);
                }
            }
        }
        if (!created.isEmpty()) {
            occupiedMarkers.put(controllerId, created);
        }
    }

    @Override
    public void removeOccupied(String controllerId) {
        if (!ready()) {
            return;
        }
        removeMarkers(occupiedSet, occupiedMarkers.remove(controllerId));
    }

    @Override
    public void clearWorld(String worldName) {
        if (!ready()) {
            return;
        }
        clearWorld(citySet, worldName);
        clearWorld(occupiedSet, worldName);
    }

    @Override
    public void clearAll() {
        if (!ready()) {
            return;
        }
        deleteSet(citySet);
        deleteSet(occupiedSet);
        cityMarkers.clear();
        cityDescriptions.clear();
        occupiedMarkers.clear();
        initialize();
    }

    private boolean ready() {
        return api != null && markerAPI != null && citySet != null && occupiedSet != null;
    }

    private class DynmapLifecycleListener implements Listener {
        @EventHandler
        public void onPluginEnable(PluginEnableEvent event) {
            if (markerAPI == null && "dynmap".equalsIgnoreCase(event.getPlugin().getName())) {
                initialize();
            }
        }
    }

    private MarkerSet ensureSet(String id, String label) {
        MarkerSet set = markerAPI.getMarkerSet(id);
        if (set == null) {
            set = markerAPI.createMarkerSet(id, label, null, false);
        } else {
            set.setMarkerSetLabel(label);
        }
        return set;
    }

    private void removeMarkers(MarkerSet set, Set<String> ids) {
        if (set == null || ids == null) {
            return;
        }
        for (String id : ids) {
            Optional.ofNullable(set.findAreaMarker(id)).ifPresent(marker -> marker.deleteMarker());
            Optional.ofNullable(set.findPolyLineMarker(id)).ifPresent(marker -> marker.deleteMarker());
        }
    }

    private void clearWorld(MarkerSet set, String world) {
        if (set == null || world == null) {
            return;
        }
        for (AreaMarker marker : new ArrayList<>(set.getAreaMarkers())) {
            if (world.equals(marker.getWorld())) {
                marker.deleteMarker();
            }
        }
        for (PolyLineMarker marker : new ArrayList<>(set.getPolyLineMarkers())) {
            if (world.equals(marker.getWorld())) {
                marker.deleteMarker();
            }
        }
    }

    private void deleteSet(MarkerSet set) {
        if (set != null) {
            set.deleteMarkerSet();
        }
    }

    private int parseColor(String hex) {
        String normalized = hex == null ? "#FFD700" : hex.trim();
        if (!normalized.startsWith("#")) {
            normalized = "#" + normalized;
        }
        try {
            return Integer.parseInt(normalized.substring(1), 16);
        } catch (Exception ex) {
            return 0xFFD700;
        }
    }
}