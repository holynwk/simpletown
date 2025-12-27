package com.simpletown.service;

import com.simpletown.data.Town;
import com.simpletown.data.TownManager;
import com.simpletown.jobs.JobType;
import com.simpletown.jobs.JobsManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class TownPlaceholder extends PlaceholderExpansion {
    private final TownManager townManager;
    private final JobsManager jobsManager;
    private final MessageService messages;

    public TownPlaceholder(TownManager townManager, JobsManager jobsManager, MessageService messages) {
        this.townManager = townManager;
        this.jobsManager = jobsManager;
        this.messages = messages;
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
        if (player == null || player.getName() == null || player.getUniqueId() == null) {
            return null;
        }

        if ("town".equalsIgnoreCase(params)) {
            Town town = townManager.getTownByMember(player.getName());
            return town == null ? "-" : town.getName();
        }

        if ("job".equalsIgnoreCase(params)) {
            JobType job = jobsManager.getJob(player.getUniqueId());
            if (job == null || JobType.UNEMPLOYED.equals(job)) {
                return "-";
            }

            String jobName = messages.get(job.getMessageKey());
            return jobName == null ? "-" : jobName;
        }

        return null;
    }
}