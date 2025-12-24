package com.simpletown.service;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class PoliticalScorePlaceholder extends PlaceholderExpansion {
    private final PoliticalScoreService politicalScoreService;

    public PoliticalScorePlaceholder(PoliticalScoreService politicalScoreService) {
        this.politicalScoreService = politicalScoreService;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "polit";
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
        if (!"score".equalsIgnoreCase(params) || player == null || player.getUniqueId() == null) {
            return null;
        }
        return String.valueOf(politicalScoreService.getScore(player.getUniqueId()));
    }
}