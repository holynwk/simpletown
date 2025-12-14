package com.simpletown.service;

import com.simpletown.data.ChunkPosition;
import com.simpletown.data.Town;
import com.simpletown.data.TownManager;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.map.Map;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import com.flowpowered.math.vector.Vector2d;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BlueMapService {
    private static final String MARKER_SET_ID = "simpletown-towns";
    private static final float HEIGHT_Y = 60f;

    private final JavaPlugin plugin;
    private final TownManager townManager;
    private final java.util.Map<String, Set<String>> markers = new ConcurrentHashMap<>();
    private BlueMapAPI api;

    public BlueMapService(JavaPlugin plugin, TownManager townManager) {
        this.plugin = plugin;
        this.townManager = townManager;

        BlueMapAPI.onEnable(api -> {
            this.api = api;
            api.getMaps().forEach(map ->
                    map.getMarkerSets().computeIfAbsent(MARKER_SET_ID,
                            id -> MarkerSet.builder().label("Города").toggleable(true).build()));
            refreshAll();
        });

        BlueMapAPI.onDisable(api -> {
            if (this.api == api) {
                this.api = null;
            }
        });
    }

    public void refreshTown(Town town) {
        drawTown(town);
    }

    public void refreshAll() {
        BlueMapAPI apiLocal = this.api;
        if (apiLocal == null) {
            return;
        }

        apiLocal.getMaps().forEach(map ->
                map.getMarkerSets().computeIfAbsent(MARKER_SET_ID,
                        id -> MarkerSet.builder().label("Города").toggleable(true).build()));

        Set<String> existing = new HashSet<>();
        for (Town town : townManager.getTowns()) {
            existing.add(town.getName());
            drawTown(town);
        }

        // Clean up markers for removed towns
        Set<String> stale = new HashSet<>(markers.keySet());
        stale.removeAll(existing);
        for (String name : stale) {
            eraseTown(name);
        }
    }

    private void drawTown(Town town) {
        eraseTown(town.getName());
        if (town.getChunks().isEmpty()) {
            return;
        }

        Set<ChunkPosition> validChunks = new HashSet<>();
        for (ChunkPosition chunk : town.getChunks()) {
            World world = Bukkit.getWorld(chunk.getWorld());
            if (world == null || world.getEnvironment() == World.Environment.NETHER) {
                continue;
            }
            validChunks.add(chunk);
        }

        if (validChunks.isEmpty()) {
            return;
        }

        Color base = colorFromHex(town.getMapColor());
        Color fillCol = new Color(base.getRed(), base.getGreen(), base.getBlue(), 0.4f);
        Color outlineCol = new Color(base.getRed(), base.getGreen(), base.getBlue(), 1f);
        Set<String> newIds = new HashSet<>();

        BlueMapAPI.getInstance().ifPresent(apiLocal -> {
            for (Map map : apiLocal.getMaps()) {
                MarkerSet set = map.getMarkerSets().get(MARKER_SET_ID);
                if (set == null) {
                    continue;
                }

                for (ChunkPosition pos : validChunks) {
                    double x0 = pos.getX() * 16.0;
                    double z0 = pos.getZ() * 16.0;
                    Shape rect = Shape.builder().addPoints(
                            new Vector2d(x0, z0),
                            new Vector2d(x0 + 16.0, z0),
                            new Vector2d(x0 + 16.0, z0 + 16.0),
                            new Vector2d(x0, z0 + 16.0)
                    ).build();

                    String fillId = town.getName() + "-fill-" + pos.getX() + "-" + pos.getZ();
                    set.getMarkers().put(fillId, ShapeMarker.builder()
                            .label(town.getName())
                            .shape(rect, HEIGHT_Y)
                            .fillColor(fillCol)
                            .lineColor(new Color(0, 0, 0, 0f))
                            .depthTestEnabled(false)
                            .build());
                    newIds.add(fillId);
                }

                record Edge(int ax, int az, int bx, int bz) {
                }

                Set<Edge> edges = new HashSet<>();
                java.util.function.BiConsumer<Entry<Integer, Integer>, Entry<Integer, Integer>> addEdge = (a, b) -> {
                    Edge e = new Edge(a.getKey(), a.getValue(), b.getKey(), b.getValue());
                    Edge re = new Edge(b.getKey(), b.getValue(), a.getKey(), a.getValue());
                    if (!edges.remove(re)) {
                        edges.add(e);
                    }
                };

                for (ChunkPosition pos : validChunks) {
                    int x0 = pos.getX() * 16;
                    int z0 = pos.getZ() * 16;
                    addEdge.accept(java.util.Map.entry(x0, z0), java.util.Map.entry(x0 + 16, z0));
                    addEdge.accept(java.util.Map.entry(x0 + 16, z0), java.util.Map.entry(x0 + 16, z0 + 16));
                    addEdge.accept(java.util.Map.entry(x0 + 16, z0 + 16), java.util.Map.entry(x0, z0 + 16));
                    addEdge.accept(java.util.Map.entry(x0, z0 + 16), java.util.Map.entry(x0, z0));
                }

                Set<Edge> remaining = new HashSet<>(edges);
                while (!remaining.isEmpty()) {
                    Edge start = remaining.iterator().next();
                    java.util.List<Vector2d> outline = new java.util.ArrayList<>();
                    outline.add(new Vector2d(start.ax(), start.az()));
                    remaining.remove(start);
                    Edge curr = start;

                    while (true) {
                        Edge next = remaining.stream()
                                .filter(e -> e.ax() == curr.bx() && e.az() == curr.bz())
                                .findFirst()
                                .orElse(null);
                        if (next == null) {
                            break;
                        }
                        outline.add(new Vector2d(next.ax(), next.az()));
                        remaining.remove(next);
                        curr = next;
                    }

                    Shape shape = Shape.builder().addPoints(outline.toArray(new Vector2d[0])).build();
                    String id = town.getName() + "-border-" + newIds.size();
                    set.getMarkers().put(id, ShapeMarker.builder()
                            .label(town.getName())
                            .shape(shape, HEIGHT_Y)
                            .fillColor(new Color(0, 0, 0, 0f))
                            .lineColor(outlineCol)
                            .depthTestEnabled(false)
                            .build());
                    newIds.add(id);
                }
            }
        });

        markers.put(town.getName(), newIds);
    }

    private void eraseTown(String name) {
        BlueMapAPI.getInstance().ifPresent(apiLocal -> {
            Set<String> ids = markers.remove(name);
            if (ids == null || ids.isEmpty()) {
                return;
            }
            for (Map map : apiLocal.getMaps()) {
                MarkerSet set = map.getMarkerSets().get(MARKER_SET_ID);
                if (set != null) {
                    set.getMarkers().keySet().removeAll(ids);
                }
            }
        });
    }

    private Color colorFromHex(String value) {
        String hex = value == null ? "#FFD700" : value.trim();
        if (!hex.startsWith("#")) {
            hex = "#" + hex;
        }
        try {
            int r = Integer.parseInt(hex.substring(1, 3), 16);
            int g = Integer.parseInt(hex.substring(3, 5), 16);
            int b = Integer.parseInt(hex.substring(5, 7), 16);
            return new Color(r, g, b);
        } catch (Exception ex) {
            return new Color(255, 215, 0);
        }
    }
}
