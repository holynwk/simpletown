package com.simpletown.map;

import com.simpletown.data.ChunkPosition;

import java.util.Map;
import java.util.Set;

public interface MapRenderer {
    void upsertCity(String cityId, String name, String description, Map<String, Set<ChunkPosition>> worldChunks, String colorHex);

    void removeCity(String cityId);

    void upsertCityChunks(String cityId, String name, Map<String, Set<ChunkPosition>> worldChunks, String colorHex);

    void upsertOccupiedChunks(String controllerId, String controllerName, Map<String, Set<ChunkPosition>> worldChunks, String colorHex);

    void removeOccupied(String controllerId);

    void clearWorld(String worldName);

    void clearAll();
}