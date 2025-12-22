package com.simpletown.api;

import com.simpletown.data.Town;
import com.simpletown.data.TownManager;
import org.bukkit.Chunk;

import java.util.Optional;

public class SimpleTownAPI {
    private final TownManager townManager;

    public SimpleTownAPI(TownManager townManager) {
        this.townManager = townManager;
    }

    public boolean hasTownInChunk(Chunk chunk) {
        return townManager.isChunkClaimed(chunk);
    }

    public Optional<Town> getTownInChunk(Chunk chunk) {
        return Optional.ofNullable(townManager.getTownAtChunk(chunk));
    }
}
