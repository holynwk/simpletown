package com.simpletown.jobs;

import com.simpletown.SimpleTownPlugin;
import com.simpletown.service.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class JobsService {
    public static final long KILLER_COOLDOWN_MS = 60L * 60L * 1000L;
    public static final long KILLER_TIMEOUT_MS = 15L * 60L * 1000L;
    public static final long KILLER_SUSPEND_MS = 24L * 60L * 60L * 1000L;
    public static final double KILLER_REWARD = 150.0;

    private final SimpleTownPlugin plugin;
    private final JobsManager jobsManager;
    private final MessageService messages;

    public JobsService(SimpleTownPlugin plugin, JobsManager jobsManager, MessageService messages) {
        this.plugin = plugin;
        this.jobsManager = jobsManager;
        this.messages = messages;
    }

    public JobType getJob(Player player) {
        return jobsManager.getJob(player.getUniqueId());
    }

    public void setJob(Player player, JobType type) {
        if (type == JobType.UNEMPLOYED) {
            jobsManager.setJob(player.getUniqueId(), type);
            return;
        }
        jobsManager.setJob(player.getUniqueId(), type);
        if (type != JobType.KILLER) {
            jobsManager.setKillerStatus(player.getUniqueId(), null);
        }
    }

    public KillerStatus getOrCreateKillerStatus(UUID uuid) {
        KillerStatus status = jobsManager.getKillerStatus(uuid);
        if (status == null) {
            status = new KillerStatus();
            jobsManager.setKillerStatus(uuid, status);
        }
        return status;
    }

    public void assignKillerTarget(Player player) {
        KillerStatus status = getOrCreateKillerStatus(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (status.isSuspended(now)) {
            return;
        }
        if (now - status.getLastCompletedAt() < KILLER_COOLDOWN_MS) {
            return;
        }
        Player target = pickTarget(player.getUniqueId(), status.getLastTargetId());
        if (target == null) {
            messages.send(player, "jobs.killer.no-targets");
            return;
        }
        status.setTarget(target.getUniqueId(), target.getName(), now);
        jobsManager.setKillerStatus(player.getUniqueId(), status);
        messages.send(player, "jobs.killer.task", java.util.Map.of("player", target.getName()));
    }

    public void handleTargetOffline(UUID targetId) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (getJob(online) != JobType.KILLER) {
                continue;
            }
            KillerStatus status = jobsManager.getKillerStatus(online.getUniqueId());
            if (status == null || targetId == null) {
                continue;
            }
            if (targetId.equals(status.getTargetId())) {
                assignKillerTarget(online);
            }
        }
    }

    public boolean handleKillerKill(Player killer, Player victim) {
        if (killer == null || victim == null) {
            return false;
        }
        if (getJob(killer) != JobType.KILLER) {
            return false;
        }
        KillerStatus status = getOrCreateKillerStatus(killer.getUniqueId());
        if (status.getTargetId() == null || !status.getTargetId().equals(victim.getUniqueId())) {
            return false;
        }
        status.setLastTargetId(status.getTargetId());
        status.clearTarget();
        status.setLastCompletedAt(System.currentTimeMillis());
        jobsManager.setKillerStatus(killer.getUniqueId(), status);
        messages.send(killer, "jobs.killer.completed");
        return true;
    }

    public void handleKillerTimeout(Player player) {
        KillerStatus status = getOrCreateKillerStatus(player.getUniqueId());
        if (status.getTargetId() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - status.getAssignedAt() < KILLER_TIMEOUT_MS) {
            return;
        }
        status.clearTarget();
        status.setSuspendedUntil(now + KILLER_SUSPEND_MS);
        status.setLastCompletedAt(now);
        jobsManager.setKillerStatus(player.getUniqueId(), status);
        jobsManager.setJob(player.getUniqueId(), JobType.UNEMPLOYED);
        messages.send(player, "jobs.killer.failed");
    }

    public void ensureKillerTarget(Player player) {
        if (getJob(player) != JobType.KILLER) {
            return;
        }
        KillerStatus status = getOrCreateKillerStatus(player.getUniqueId());
        if (status.getTargetId() == null) {
            assignKillerTarget(player);
        }
    }

    private Player pickTarget(UUID killerId, UUID lastTarget) {
        List<Player> candidates = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(killerId)) {
                continue;
            }
            if (lastTarget != null && online.getUniqueId().equals(lastTarget)) {
                continue;
            }
            candidates.add(online);
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }
}