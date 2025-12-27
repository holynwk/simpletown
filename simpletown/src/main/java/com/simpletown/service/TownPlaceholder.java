package com.simpletown.service;

import com.simpletown.data.Town;
import com.simpletown.data.TownManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class TownPlaceholder extends PlaceholderExpansion {
    private final TownManager townManager;

    public TownPlaceholder(TownManager townManager) {
        this.townManager = townManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "p";
    }

    @Override
    public @NotNull String getAuthor() {
        return "simpletown";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (!"town".equalsIgnoreCase(params) || player == null || player.getName() == null) {
            return null;
        }
        Town town = townManager.getTownByMember(player.getName());
        return town == null ? "-" : town.getName();
    }
}