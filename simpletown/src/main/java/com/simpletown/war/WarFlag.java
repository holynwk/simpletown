package com.simpletown.war;

import com.simpletown.data.ChunkPosition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public class WarFlag {
    private final ChunkPosition chunk;
    private final String owningTown;
    private final boolean center;
    private final String worldName;
    private final int x;
    private final int y;
    private final int z;
    private int health;
    private long remainingCaptureMs;
    private long lastTick;
    private UUID hologramId;

    public WarFlag(ChunkPosition chunk, String owningTown, boolean center, int health, long remainingCaptureMs, String worldName, int x, int y, int z) {
        this.chunk = chunk;
        this.owningTown = owningTown;
        this.center = center;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.health = health;
        this.remainingCaptureMs = remainingCaptureMs;
        this.lastTick = System.currentTimeMillis();
    }

    public ChunkPosition getChunk() {
        return chunk;
    }

    public String getOwningTown() {
        return owningTown;
    }

    public boolean isCenter() {
        return center;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public int getHealth() {
        return health;
    }

    public void damage() {
        this.health = Math.max(0, this.health - 1);
    }

    public long getRemainingCaptureMs() {
        return remainingCaptureMs;
    }

    public void setRemainingCaptureMs(long remainingCaptureMs) {
        this.remainingCaptureMs = remainingCaptureMs;
    }

    public long getLastTick() {
        return lastTick;
    }

    public void setLastTick(long lastTick) {
        this.lastTick = lastTick;
    }

    public UUID getHologramId() {
        return hologramId;
    }

    public void setHologramId(UUID hologramId) {
        this.hologramId = hologramId;
    }

    public boolean isFlagBlock(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        return location.getWorld().getName().equals(worldName)
                && location.getBlockX() == x
                && location.getBlockY() == y
                && location.getBlockZ() == z;
    }

    public boolean isSupportBlock(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        return location.getWorld().getName().equals(worldName)
                && location.getBlockX() == x
                && location.getBlockY() == y - 1
                && location.getBlockZ() == z;
    }

    public Location toLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x + 0.5, y, z + 0.5);
    }
}