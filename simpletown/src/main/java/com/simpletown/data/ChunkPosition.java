package com.simpletown.data;

import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.Objects;

public class ChunkPosition {
    private final String world;
    private final int x;
    private final int z;

    public ChunkPosition(String world, int x, int z) {
        this.world = world;
        this.x = x;
        this.z = z;
    }

    public static ChunkPosition fromChunk(Chunk chunk) {
        return new ChunkPosition(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    public String getWorld() {
        return world;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    public String serialize() {
        return world + ":" + x + ":" + z;
    }

    public static ChunkPosition deserialize(String key) {
        String[] parts = key.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Неверный формат чанка: " + key);
        }
        return new ChunkPosition(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    public boolean isSameChunk(Chunk chunk) {
        World chunkWorld = chunk.getWorld();
        return chunkWorld.getName().equals(world) && chunk.getX() == x && chunk.getZ() == z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChunkPosition that = (ChunkPosition) o;
        return x == that.x && z == that.z && Objects.equals(world, that.world);
    }

    @Override
    public int hashCode() {
        return Objects.hash(world, x, z);
    }

    @Override
    public String toString() {
        return serialize();
    }
}